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

    /**
     * 一次 GROUP BY 拿到该 inspection 下所有房间的照片数,避免逐房间查询(N+1)。
     * 注意:JPQL 构造器表达式必须写 record 的全限定名。
     */
    @Query("select new com.propertymap.repository.RoomPhotoCount(ip.photo.room.id, count(ip)) " +
           "from InspectionPhoto ip " +
           "where ip.inspection.id = :inspectionId " +
           "group by ip.photo.room.id")
    List<RoomPhotoCount> countPhotosByRoomForInspection(@Param("inspectionId") Long inspectionId);
}