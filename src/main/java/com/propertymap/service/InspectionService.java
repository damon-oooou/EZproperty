package com.propertymap.service;

import com.propertymap.controller.dto.RoomWithPhotoCountResponse;
import com.propertymap.model.*;
import com.propertymap.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
    private final PropertyRepository propertyRepository;
    private final PhotoService photoService;
    private final RoomRepository roomRepository;
    private final RoomConditionRepository roomConditionRepository;
    private final ReportDetailsRepository reportDetailsRepository;

    @Transactional
    public Inspection createInspection(Long propertyId, InspectionType type,
                                       LocalDate inspectionDate, boolean inheritFromPrevious) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException("Property not found: " + propertyId));

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
            List<InspectionPhoto> inheritedLinks =
                    inspectionPhotoRepository.findByInspectionId(previousId)
                            .stream()
                            .map(link -> {
                                InspectionPhoto copy = new InspectionPhoto();
                                copy.setInspection(saved);
                                copy.setPhoto(link.getPhoto());
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
        return inspectionRepository.findByPropertyIdOrderByInspectionDateDescIdDesc(propertyId);
    }

    public List<Photo> getPhotosForRoom(Long inspectionId, Long roomId) {
        return inspectionPhotoRepository.findPhotosByInspectionIdAndRoomId(inspectionId, roomId);
    }

    @Transactional
    public void removePhotosFromInspection(Long inspectionId, List<Long> photoIds) {
        inspectionPhotoRepository.deleteByInspectionIdAndPhotoIdIn(inspectionId, photoIds);
    }

    @Transactional
    public List<Photo> uploadPhotosToInspection(Long inspectionId, Long roomId,
                                                List<MultipartFile> files) throws IOException {
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("Inspection not found: " + inspectionId));

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
        inspectionPhotoRepository.save(link);
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
        Inspection inspection = inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new EntityNotFoundException("Inspection not found: " + inspectionId));

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