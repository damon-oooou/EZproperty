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

    @Transactional
    public Inspection createInspection(Long propertyId, InspectionType type,
                                       LocalDate inspectionDate, boolean inheritFromPrevious) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new EntityNotFoundException("Property not found: " + propertyId));

        // 必须在保存新 inspection 之前找"上一次",原因见下面的解释
        Optional<Inspection> previous =
                inspectionRepository.findTopByPropertyIdOrderByInspectionDateDescIdDesc(propertyId);

        Inspection inspection = new Inspection();
        inspection.setProperty(property);
        inspection.setType(type);
        inspection.setInspectionDate(inspectionDate);
        Inspection saved = inspectionRepository.save(inspection);

        if (inheritFromPrevious && previous.isPresent()) {
            List<InspectionPhoto> inheritedLinks =
                    inspectionPhotoRepository.findByInspectionId(previous.get().getId())
                            .stream()
                            .map(link -> {
                                InspectionPhoto copy = new InspectionPhoto();
                                copy.setInspection(saved);
                                copy.setPhoto(link.getPhoto());
                                return copy;
                            })
                            .toList();
            inspectionPhotoRepository.saveAll(inheritedLinks);
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