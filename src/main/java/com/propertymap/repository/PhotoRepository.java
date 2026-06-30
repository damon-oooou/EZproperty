package com.propertymap.repository;

import com.propertymap.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByRoomId(Long roomId);
}