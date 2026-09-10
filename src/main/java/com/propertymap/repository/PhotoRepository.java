package com.propertymap.repository;

import com.propertymap.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByRoomId(Long roomId);

    /** v0.8:该照片是否已有后继(被更新过)。UNIQUE 约束兜底,这里提前给友好的 409。 */
    boolean existsByReplacesPhotoId(Long replacesPhotoId);
}
