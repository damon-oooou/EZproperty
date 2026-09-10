package com.propertymap.service;

import com.propertymap.controller.dto.ReportDetailsResponse;
import com.propertymap.controller.dto.RoomConditionResponse;
import com.propertymap.model.Inspection;
import com.propertymap.model.InspectionPhoto;
import com.propertymap.model.Photo;
import com.propertymap.model.Property;
import com.propertymap.model.Room;
import com.propertymap.model.User;
import com.propertymap.repository.InspectionPhotoRepository;
import com.propertymap.repository.RoomRepository;
import com.propertymap.repository.UserRepository;
import com.propertymap.security.TenantGuard;
import com.propertymap.storage.PhotoKeys;
import com.propertymap.storage.PhotoStorage;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportService {

    private final RoomRepository roomRepository;
    private final InspectionPhotoRepository inspectionPhotoRepository;
    private final UserRepository userRepository;
    private final ReportService reportService;
    private final PhotoStorage photoStorage;
    private final TenantGuard tenantGuard;
    private final TemplateEngine templateEngine; // Spring Boot 自动配置的 SpringTemplateEngine

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    /**
     * v0.6 阶段 B:并行拉取照片字节的固定线程池(约 8 线程)。
     * 大报告(数百张图)从 R2 逐张串行拉不可接受;dev 本地读也无害。
     * daemon 线程,不阻塞 JVM 退出。
     */
    private static final ExecutorService PHOTO_LOAD_POOL = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "pdf-photo-load");
        t.setDaemon(true);
        return t;
    });

    /** 生成结果:PDF 字节 + 建议文件名。 */
    public record GeneratedReport(byte[] content, String fileName) {}

    /** 模板视图模型(Spring 的 Thymeleaf 用 SpEL,record 访问器可直接用) */
    public record ConditionRow(String roomName, String condition, String comments) {}
    /** v0.8:note 可为 null(模板按 th:if 渲染)。 */
    public record PhotoView(String dataUri, String caption, String note) {}
    public record RoomPhotos(String roomName, List<PhotoView> photos) {}

    @Transactional(readOnly = true)
    public GeneratedReport generate(Long inspectionId) {
        Inspection inspection = tenantGuard.inspection(inspectionId); // v0.5:归属校验
        Property property = inspection.getProperty();

        ReportDetailsResponse details = reportService.getReportDetails(inspectionId);
        List<RoomConditionResponse> conditions = reportService.getConditions(inspectionId);

        String typeLabel = switch (inspection.getType()) {
            case ENTRY -> "Entry";
            case ROUTINE -> "Routine";
            case EXIT -> "Exit";
        };

        // --- 组装模板数据 ---
        Context ctx = new Context();
        ctx.setVariable("title", typeLabel + " Inspection Report");
        ctx.setVariable("address", property.getAddress());
        ctx.setVariable("inspectionDate", formatDate(inspection.getInspectionDate()));
        ctx.setVariable("landlordName", orBlank(details.landlordName()));
        ctx.setVariable("tenantName", orBlank(details.tenantName()));
        ctx.setVariable("leaseExpiry", formatDate(details.leaseExpiry()));
        ctx.setVariable("smokeAlarmsPresent", yesNo(details.smokeAlarmsPresent()));
        ctx.setVariable("smokeAlarmsLocation", orBlank(details.smokeAlarmsLocation()));
        ctx.setVariable("tenantRepairs", yesNo(details.tenantRepairsCarriedOut()));
        ctx.setVariable("urgentAction", orBlank(details.urgentAction()));
        ctx.setVariable("generalComments", orBlank(details.generalComments()));
        ctx.setVariable("tenantActionRequired", orBlank(details.tenantActionRequired()));
        ctx.setVariable("agentName", orBlank(details.agentName()));
        ctx.setVariable("agentTradingAs", orBlank(details.agentTradingAs()));
        ctx.setVariable("disclaimer", orBlank(details.disclaimer()));

        ctx.setVariable("rows", conditions.stream()
                .map(c -> new ConditionRow(
                        c.roomName(),
                        c.satisfactory() == null ? "Not inspected"
                                : (c.satisfactory() ? "Satisfactory" : "Not satisfactory"),
                        orBlank(c.comments())))
                .toList());

        ctx.setVariable("roomPhotos", buildRoomPhotos(inspectionId, property.getId()));

        // --- 渲染 HTML,转 PDF ---
        String html = templateEngine.process("report", ctx);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // useFastMode() 已废弃:io.github fork 里快速模式是唯一模式,无需再调
            builder.withHtmlContent(html, null); // 图片全是 data URI,不需要 baseUri
            builder.toStream(os);
            builder.run();
            return new GeneratedReport(
                    os.toByteArray(),
                    buildFileName(typeLabel, property.getAddress(), inspection.getInspectionDate()));
        } catch (IOException e) {
            throw new UncheckedIOException("PDF generation failed for inspection " + inspectionId, e);
        }
    }

    /**
     * 按房间分组收集照片。
     * v0.6 阶段 B:照片字节经 PhotoStorage.load()(中间档 key)并行拉取,
     * 之后按房间顺序组装。个别对象读不到时跳过并打日志,不让整份报告失败。
     */
    private List<RoomPhotos> buildRoomPhotos(Long inspectionId, Long propertyId) {
        List<Room> rooms = roomRepository.findByPropertyIdOrderByPosition(propertyId);

        // 1. 收集全部 (room -> photos),并提交并行 load
        //    v0.8:取 join 行而非 Photo,题注需要 carried_forward / confirmed_*
        Map<Long, List<InspectionPhoto>> linksByRoom = new HashMap<>();
        Map<Long, Future<byte[]>> loads = new HashMap<>();
        for (Room room : rooms) {
            List<InspectionPhoto> links = inspectionPhotoRepository
                    .findLinksByInspectionIdAndRoomId(inspectionId, room.getId());
            linksByRoom.put(room.getId(), links);
            for (InspectionPhoto link : links) {
                Photo photo = link.getPhoto();
                String mediumKey = PhotoKeys.medium(photo.getStorageKey());
                loads.put(photo.getId(),
                        PHOTO_LOAD_POOL.submit(() -> photoStorage.load(mediumKey)));
            }
        }

        // v0.8:现场确认人姓名一次取回(v0.8 不会写入 confirmed_by,模板仍需支持)
        Map<Long, String> confirmerNames = confirmerNames(linksByRoom.values());

        // 2. 按房间顺序组装(join 各 Future)
        List<RoomPhotos> result = new ArrayList<>();
        for (Room room : rooms) {
            List<PhotoView> views = new ArrayList<>();
            for (InspectionPhoto link : linksByRoom.get(room.getId())) {
                Photo photo = link.getPhoto();
                String dataUri = toDataUri(photo, loads.get(photo.getId()));
                if (dataUri == null) continue;
                int n = views.size() + 1;
                String label = n == 1 ? room.getName() : room.getName() + " " + n;
                String caption = label + " \u2014 " + dateCaption(photo)
                        + provenanceCaption(link, confirmerNames);
                views.add(new PhotoView(dataUri, caption, photo.getNote()));
            }
            if (!views.isEmpty()) {
                result.add(new RoomPhotos(room.getName(), views));
            }
        }
        return result;
    }

    /**
     * v0.8:沿用标注。carried_forward 时追加 " · Carried forward";
     * 若还有现场确认(confirmed_at 非空)追加 " · confirmed on site {日期}, {确认人}"。
     */
    private String provenanceCaption(InspectionPhoto link, Map<Long, String> confirmerNames) {
        if (!link.isCarriedForward()) return "";
        StringBuilder sb = new StringBuilder(" · Carried forward");
        if (link.getConfirmedAt() != null) {
            sb.append(" · confirmed on site ")
              .append(DATE_FMT.format(link.getConfirmedAt().toLocalDate()));
            String name = link.getConfirmedBy() == null ? null : confirmerNames.get(link.getConfirmedBy());
            if (name != null) sb.append(", ").append(name);
        }
        return sb.toString();
    }

    /** 确认人 user id → 短名("J. Okafor")。一次 findAllById,不逐行查。 */
    private Map<Long, String> confirmerNames(Collection<List<InspectionPhoto>> linkGroups) {
        Set<Long> ids = new HashSet<>();
        for (List<InspectionPhoto> links : linkGroups) {
            for (InspectionPhoto link : links) {
                if (link.getConfirmedAt() != null && link.getConfirmedBy() != null) {
                    ids.add(link.getConfirmedBy());
                }
            }
        }
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            names.put(user.getId(), shortName(user.getFullName()));
        }
        return names;
    }

    /** "Jane Okafor" → "J. Okafor";单个词原样返回。 */
    static String shortName(String fullName) {
        if (fullName == null) return null;
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length < 2) return fullName.trim();
        return parts[0].charAt(0) + ". " + parts[parts.length - 1];
    }

    /**
     * v0.6:嵌入源直接用规格化中间档(1600px q0.85),不再做运行时压缩 ——
     * 管线保证所有照片必已摆正、必无 EXIF,这里只是取字节转 base64。
     */
    private String toDataUri(Photo photo, Future<byte[]> load) {
        try {
            byte[] bytes = load.get();
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (ExecutionException e) {
            log.warn("Skipping photo {} ({}): {}", photo.getId(), photo.getStorageKey(),
                    e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading report photos", e);
        }
    }

    /**
     * 题注日期(与前端同一措辞规则):有 EXIF 拍摄时间显示 "Taken {日期}",
     * 否则回退 "Uploaded {日期}"。禁止拿上传时间冒充拍摄时间(法律场景要求诚实区分)。
     */
    private String dateCaption(Photo photo) {
        LocalDateTime takenAt = photo.getTakenAt();
        if (takenAt != null) {
            return "Taken " + DATE_FMT.format(takenAt.toLocalDate());
        }
        LocalDateTime uploadedAt = photo.getUploadedAt();
        return uploadedAt == null ? "" : "Uploaded " + DATE_FMT.format(uploadedAt.toLocalDate());
    }

    private String buildFileName(String typeLabel, String address, LocalDate date) {
        String cleanAddress = address.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return typeLabel + "_Inspection_Report_" + cleanAddress + "_" + date + ".pdf";
    }

    private String orBlank(String s) {
        return s == null ? "" : s;
    }

    private String yesNo(Boolean b) {
        return b == null ? "—" : (b ? "Yes" : "No");
    }

    private String formatDate(LocalDate d) {
        return d == null ? "" : DATE_FMT.format(d);
    }
}