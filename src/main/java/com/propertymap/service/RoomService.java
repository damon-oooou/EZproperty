package com.propertymap.service;

import com.propertymap.model.Room;
import com.propertymap.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    public List<Room> getRoomsByProperty(Long propertyId) {
        return roomRepository.findByPropertyIdOrderByPosition(propertyId);
    }

    public Room getRoomById(Long id) {
        return roomRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Room not found: " + id));
    }

    public Room addRoom(Long propertyId, String name) {
        List<Room> existing = roomRepository.findByPropertyIdOrderByPosition(propertyId);
        Room room = new Room();
        room.setName(name);
        room.setPosition(existing.size() + 1);
        return roomRepository.save(room);
    }
}