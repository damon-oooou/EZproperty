package com.propertymap.repository;

import com.propertymap.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByPropertyIdOrderByPosition(Long propertyId);
}