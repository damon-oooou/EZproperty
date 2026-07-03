package com.propertymap.repository;
import com.propertymap.model.Photo;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.propertymap.model.InspectionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InspectionPhotoRepository extends JpaRepository<InspectionPhoto, Long> {

    List<InspectionPhoto> findByInspectionId(Long inspectionId);

    @Query("select ip.photo from InspectionPhoto ip " +
           "where ip.inspection.id = :inspectionId and ip.photo.room.id = :roomId")
    List<Photo> findPhotosByInspectionIdAndRoomId(@Param("inspectionId") Long inspectionId,
                                                  @Param("roomId") Long roomId);

    void deleteByInspectionIdAndPhotoIdIn(Long inspectionId, List<Long> photoIds);

    boolean existsByPhotoId(Long photoId);
}