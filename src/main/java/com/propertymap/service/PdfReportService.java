package com.propertymap.service;

import com.propertymap.controller.dto.ReportDetailsResponse;
import com.propertymap.controller.dto.RoomConditionResponse;
import com.propertymap.model.Inspection;
import com.propertymap.model.Photo;
import com.propertymap.model.Property;
import com.propertymap.model.Room;
import com.propertymap.repository.InspectionPhotoRepository;
import com.propertymap.repository.InspectionRepository;
import com.propertymap.repository.RoomRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportService {

    private final InspectionRepository inspectionRepository;
    private final RoomRepository roomRepository;
    private final InspectionPhotoRepository inspectionPhotoRepository;
    private final ReportService reportService;
    private final TemplateEngine templateEngine; // Spring Boot 自动配置的 SpringTemplateEngine

    private static final int MAX_PHOTO_WIDTH = 1200;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    /** 生成结果:PDF 字节 + 建议文件名。 */
    public record GeneratedReport(byte[] content, String fileName) {}

    /** 模板视图模型(Spring 的 Thymeleaf 用 SpEL,record 访问器可直接用) */
    public record ConditionRow(String roomName, String condition, String comments) {}
    public record PhotoView(String dataUri, String caption) {}
    public record RoomPhotos(String roomName, List<PhotoView> photos) {}

    @Transactional(readOnly = true)
    public GeneratedReport generate(Long inspectionId) {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("Inspection not found: " + inspectionId));
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
            builder.useFastMode();
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

    /** 按房间分组收集照片。读不了的照片(HEIC、文件丢失)跳过并打日志,不让整份报告失败。 */
    private List<RoomPhotos> buildRoomPhotos(Long inspectionId, Long propertyId) {
        List<RoomPhotos> result = new ArrayList<>();
        for (Room room : roomRepository.findByPropertyIdOrderByPosition(propertyId)) {
            List<Photo> photos = inspectionPhotoRepository
                    .findPhotosByInspectionIdAndRoomId(inspectionId, room.getId());
            List<PhotoView> views = new ArrayList<>();
            for (Photo photo : photos) {
                String dataUri = toDataUri(photo);
                if (dataUri == null) continue;
                int n = views.size() + 1;
                views.add(new PhotoView(dataUri,
                        n == 1 ? room.getName() : room.getName() + " " + n));
            }
            if (!views.isEmpty()) {
                result.add(new RoomPhotos(room.getName(), views));
            }
        }
        return result;
    }

    /** 读磁盘 -> 压到最大 1200px 宽 -> 重编码 JPEG -> base64 data URI。失败返回 null。 */
    private String toDataUri(Photo photo) {
        try {
            BufferedImage src = ImageIO.read(Paths.get(photo.getFilePath()).toFile());
            if (src == null) {
                log.warn("Unreadable image format, skipping photo {} ({})",
                        photo.getId(), photo.getFilePath());
                return null;
            }
            int w = src.getWidth();
            int targetW = Math.min(w, MAX_PHOTO_WIDTH);
            int targetH = (int) Math.round(src.getHeight() * (targetW / (double) w));

            // 统一画到 RGB 画布上:兼顾缩放,以及 PNG 带透明通道时 JPEG 编码器会失败的问题
            BufferedImage rgb = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, targetW, targetH, Color.WHITE, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", baos);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.warn("Skipping photo {} ({}): {}", photo.getId(), photo.getFilePath(), e.getMessage());
            return null;
        }
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
        return b == null ? "\u2014" : (b ? "Yes" : "No");
    }

    private String formatDate(LocalDate d) {
        return d == null ? "" : DATE_FMT.format(d);
    }
}