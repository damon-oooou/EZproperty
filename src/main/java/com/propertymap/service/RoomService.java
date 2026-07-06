package com.propertymap.service;

import com.propertymap.model.Property;
import com.propertymap.model.Room;
import com.propertymap.repository.PropertyRepository;
import com.propertymap.repository.RoomRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;

    public List<Room> getRoomsByProperty(Long propertyId) {
        return roomRepository.findByPropertyIdOrderByPosition(propertyId);
    }

    public Room getRoomById(Long id) {
        return roomRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Room not found: " + id));
    }

    public Room addRoom(Long propertyId, String name) {
        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new EntityNotFoundException("Property not found: " + propertyId));

        List<Room> existing = roomRepository.findByPropertyIdOrderByPosition(propertyId);
        Room room = new Room();
        room.setProperty(property);
        room.setName(name);
        room.setPosition(existing.size() + 1);
        return roomRepository.save(room);
    }
}