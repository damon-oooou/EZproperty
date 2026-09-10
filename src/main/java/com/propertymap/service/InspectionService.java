package com.propertymap.service;

import com.propertymap.controller.dto.RoomWithPhotoCountResponse;
import com.propertymap.exception.ConflictException;
import com.propertymap.model.*;
import com.propertymap.repository.*;
import com.propertymap.security.TenantGuard;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final InspectionPhotoRepository inspectionPhotoRepository;
    private final PhotoRepository photoRepository;
    private final PhotoService photoService;
    private final RoomRepository roomRepository;
    private final RoomConditionRepository roomConditionRepository;
    private final ReportDetailsRepository reportDetailsRepository;
    private final TenantGuard tenantGuard;

    @Transactional
    public Inspection createInspection(Long propertyId, InspectionType type,
                                       LocalDate inspectionDate, boolean inheritFromPrevious) {
        Property property = tenantGuard.property(propertyId);

        // ROUTINE 不参与继承:前端已隐藏该选项,这里再挡一层,防止 API 直接传 true。
        boolean inherit = inheritFromPrevious && type != InspectionType.ROUTINE;

        // 必须在保存新 inspection 之前找"上一次",否则新建的这条自己会成为查询结果。
        // 来源固定为最近一次非 ROUTINE:Entry→Exit→下一个Entry 自然衔接。
        Optional<Inspection> previous =
                inspectionRepository.findTopByPropertyIdAndTypeNotOrderByInspectionDateDescIdDesc(
                        propertyId, InspectionType.ROUTINE);

        Inspection inspection = new Inspection();
        inspection.setProperty(property);
        inspection.setType(type);
        inspection.setInspectionDate(inspectionDate);
        Inspection saved = inspectionRepository.save(inspection);

        if (inherit && previous.isPresent()) {
            Long previousId = previous.get().getId();

            // 1. 照片引用(v0.2 的原有逻辑)
            //    v0.8:复制的 join 行标 carried_forward = TRUE;confirmed_at / confirmed_by
            //    保持 NULL —— "现场确认过"需要一个真实的用户动作,创建时间不是证据。
            List<InspectionPhoto> inheritedLinks =
                    inspectionPhotoRepository.findByInspectionId(previousId)
                            .stream()
                            .map(link -> {
                                InspectionPhoto copy = new InspectionPhoto();
                                copy.setInspection(saved);
                                copy.setPhoto(link.getPhoto());
                                copy.setCarriedForward(true);
                                return copy;
                            })
                            .toList();
            inspectionPhotoRepository.saveAll(inheritedLinks);

            // 2. 房间 condition(satisfactory + comments 全量拷贝)
            List<RoomCondition> inheritedConditions =
                    roomConditionRepository.findByInspectionIdWithRoom(previousId)
                            .stream()
                            .map(prev -> {
                                RoomCondition copy = new RoomCondition();
                                copy.setInspection(saved);
                                copy.setRoom(prev.getRoom());
                                copy.setSatisfactory(prev.getSatisfactory());
                                copy.setComments(prev.getComments());
                                return copy;
                            })
                            .toList();
            roomConditionRepository.saveAll(inheritedConditions);

            // 3. 报告头:身份类字段拷贝,三个行动框留空(每次检查的新发现,不该抄上次的)
            reportDetailsRepository.findById(previousId).ifPresent(prev -> {
                ReportDetails copy = new ReportDetails();
                copy.setInspection(saved);
                copy.setLandlordName(prev.getLandlordName());
                copy.setTenantName(prev.getTenantName());
                copy.setLeaseExpiry(prev.getLeaseExpiry());
                copy.setSmokeAlarmsPresent(prev.getSmokeAlarmsPresent());
                copy.setSmokeAlarmsLocation(prev.getSmokeAlarmsLocation());
                copy.setAgentName(prev.getAgentName());
                copy.setAgentTradingAs(prev.getAgentTradingAs());
                copy.setDisclaimer(prev.getDisclaimer());
                // urgentAction / generalComments / tenantActionRequired / tenantRepairsCarriedOut 有意留空
                reportDetailsRepository.save(copy);
            });
        }
        return saved;
    }

    public List<Inspection> getInspectionsForProperty(Long propertyId) {
        tenantGuard.property(propertyId);
        return inspectionRepository.findByPropertyIdOrderByInspectionDateDescIdDesc(propertyId);
    }

    @Transactional
    public void removePhotosFromInspection(Long inspectionId, List<Long> photoIds) {
        tenantGuard.inspection(inspectionId); // v0.5:先验归属再删引用
        inspectionPhotoRepository.deleteByInspectionIdAndPhotoIdIn(inspectionId, photoIds);
    }

    @Transactional
    public List<Photo> uploadPhotosToInspection(Long inspectionId, Long roomId,
                                                List<MultipartFile> files) throws IOException {
        Inspection inspection = tenantGuard.inspection(inspectionId);

        Room room = photoService.getRoomOrThrow(roomId);
        if (!room.getProperty().getId().equals(inspection.getProperty().getId())) {
            throw new IllegalArgumentException(
                    "Room " + roomId + " does not belong to the same property as inspection " + inspectionId);
        }

        List<Photo> photos = photoService.storePhotos(room, files);
        for (Photo photo : photos) {
            linkPhotoToInspection(inspection, photo);
        }
        return photos;
    }

    @Transactional
    public void linkPhotoToInspection(Inspection inspection, Photo photo) {
        InspectionPhoto link = new InspectionPhoto();
        link.setInspection(inspection);
        link.setPhoto(photo);
        link.setCarriedForward(false); // 本次新增,不是沿用
        inspectionPhotoRepository.save(link);
    }

    /**
     * v0.8:添加更新照片。新增一张照片记录它替换了 oldPhotoId,并在"当前" inspection 里
     * 用新照片顶替旧照片的引用。
     *
     * 必须遵守:
     *   1. 只删当前 inspection 与旧照片的 join 行,其他 inspection 一律不动
     *   2. 旧照片的 Photo 记录与存储文件完全不动
     *   3. 上传失败整个事务回滚,旧 join 行仍在,无孤儿文件(storePhoto 自带清理)
     *   4. 不级联更新任何其他 inspection
     */
    @Transactional
    public Photo updatePhoto(Long inspectionId, Long roomId, Long oldPhotoId,
                             MultipartFile file, String note) throws IOException {
        tenantGuard.inspection(inspectionId);

        Photo oldPhoto = photoRepository.findById(oldPhotoId)
                .orElseThrow(() -> new EntityNotFoundException("Photo not found: " + oldPhotoId));
        if (!oldPhoto.getRoom().getId().equals(roomId)) {
            throw new IllegalArgumentException("Photo does not belong to this room");
        }

        // 旧照片必须当前就在这次 inspection 里,否则这个动作没有意义。
        // 这一步同时保证了 room 属于 inspection 的 property(能在 inspection 里就一定同 property)。
        InspectionPhoto oldJoin = inspectionPhotoRepository
                .findByInspectionIdAndPhotoId(inspectionId, oldPhotoId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Photo " + oldPhotoId + " is not part of inspection " + inspectionId));

        // UNIQUE 约束会兜底,这里提前给出友好错误
        if (photoRepository.existsByReplacesPhotoId(oldPhotoId)) {
            throw new ConflictException("This photo has already been updated");
        }

        // 没有说明的替换半年后无法解读,History 会变成一堆无意义的箭头。API 层同样必填。
        String normalizedNote = PhotoService.normalizeNote(note);
        if (normalizedNote == null) {
            throw new IllegalArgumentException("A note describing what changed is required");
        }

        Photo newPhoto;
        try {
            // 复用现有上传管线(EXIF 处理、三档 tier、taken_at);note 与 replaces 随 INSERT 一起写入
            newPhoto = photoService.storePhoto(oldPhoto.getRoom(), file, normalizedNote, oldPhotoId);
        } catch (DataIntegrityViolationException e) {
            // 并发下两次更新同一张旧照片:第二个 INSERT 撞 uq_photos_replaces,文件已被 storePhoto 清理
            throw new ConflictException("This photo has already been updated");
        }

        inspectionPhotoRepository.delete(oldJoin);
        linkPhotoToInspection(oldJoin.getInspection(), newPhoto);
        return newPhoto;
    }

    /**
     * 分层说明:本项目的惯例是 service 返回实体、controller 转 DTO。
     * 唯独这个方法直接返回 DTO,因为它要把两份数据(房间 + 统计)合并成一个形状,
     * 这个"合并"本身就是业务逻辑,放 controller 里不合适。
     *
     * @Transactional(readOnly = true) 是必需的:inspection.getProperty() 是 LAZY 关联,
     * 必须在事务内访问,否则抛 LazyInitializationException。
     */
    @Transactional(readOnly = true)
    public List<RoomWithPhotoCountResponse> getRoomsWithPhotoCounts(Long inspectionId) {
        Inspection inspection = tenantGuard.inspection(inspectionId);

        List<Room> rooms = roomRepository.findByPropertyIdOrderByPosition(
                inspection.getProperty().getId());

        Map<Long, Long> counts = inspectionPhotoRepository
                .countPhotosByRoomForInspection(inspectionId)
                .stream()
                .collect(Collectors.toMap(RoomPhotoCount::roomId, RoomPhotoCount::count));

        return rooms.stream()
                .map(r -> new RoomWithPhotoCountResponse(
                        r.getId(), r.getName(), r.getPosition(),
                        counts.getOrDefault(r.getId(), 0L)))
                .toList();
    }
}