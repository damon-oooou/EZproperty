package com.propertymap.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.propertymap.model.InspectionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InspectionPhotoRepository extends JpaRepository<InspectionPhoto, Long> {

    List<InspectionPhoto> findByInspectionId(Long inspectionId);

    /**
     * v0.8:返回 join 行本身(带 carried_forward / confirmed_*)并抓好 photo,
     * 取代原先只返回 Photo 的查询。按 photo id 排序,让网格顺序稳定(之前无 ORDER BY)。
     */
    @Query("select ip from InspectionPhoto ip join fetch ip.photo p " +
           "where ip.inspection.id = :inspectionId and p.room.id = :roomId " +
           "order by p.id")
    List<InspectionPhoto> findLinksByInspectionIdAndRoomId(@Param("inspectionId") Long inspectionId,
                                                           @Param("roomId") Long roomId);

    Optional<InspectionPhoto> findByInspectionIdAndPhotoId(Long inspectionId, Long photoId);

    void deleteByInspectionIdAndPhotoIdIn(Long inspectionId, List<Long> photoIds);

    boolean existsByPhotoId(Long photoId);

    /**
     * v0.8:一次查询取回一组照片的全部引用(照片 × 引用它的 inspection),
     * 按 (photoId, join 行 id) 升序;调用方取每个 photoId 的第一行作为来源。
     * 行数 = 照片数 × 各自被引用次数,量级很小;禁止逐张查询。
     */
    @Query("select new com.propertymap.repository.PhotoOriginRow(" +
           "ip.photo.id, ip.id, i.id, i.type, i.inspectionDate) " +
           "from InspectionPhoto ip join ip.inspection i " +
           "where ip.photo.id in :photoIds " +
           "order by ip.photo.id, ip.id")
    List<PhotoOriginRow> findOriginRows(@Param("photoIds") Collection<Long> photoIds);

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
