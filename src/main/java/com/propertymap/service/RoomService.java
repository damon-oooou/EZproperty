package com.propertymap.service;

import com.propertymap.model.Property;
import com.propertymap.model.Room;
import com.propertymap.repository.RoomRepository;
import com.propertymap.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final TenantGuard tenantGuard;

    public List<Room> getRoomsByProperty(Long propertyId) {
        tenantGuard.property(propertyId); // v0.5:先验归属,再查房间
        return roomRepository.findByPropertyIdOrderByPosition(propertyId);
    }

    public Room addRoom(Long propertyId, String name) {
        Property property = tenantGuard.property(propertyId);

        List<Room> existing = roomRepository.findByPropertyIdOrderByPosition(propertyId);
        Room room = new Room();
        room.setProperty(property);
        room.setName(name);
        room.setPosition(existing.size() + 1);
        return roomRepository.save(room);
    }
}
