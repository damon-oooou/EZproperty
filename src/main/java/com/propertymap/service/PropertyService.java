package com.propertymap.service;

import com.propertymap.model.Property;
import com.propertymap.model.Room;
import com.propertymap.repository.AgencyRepository;
import com.propertymap.repository.PropertyRepository;
import com.propertymap.repository.RoomRepository;
import com.propertymap.security.CurrentUser;
import com.propertymap.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final AgencyRepository agencyRepository;
    private final TenantGuard tenantGuard;

    private static final List<String> DEFAULT_ROOMS = Arrays.asList(
        "Entrance", "Lounge Room", "Dining Room", "Family Room",
        "Kitchen", "Oven", "Exhaust Fan", "Laundry",
        "Bedroom 1", "Bedroom 2", "Bedroom 3",
        "Bathroom", "Toilet",
        "Front Garden", "Rear Garden", "Driveway", "Garage", "Gate"
    );

    @Transactional
    public Property createProperty(Property property) {
        // getReferenceById 拿代理即可,不需要真的查 agency 行
        property.setAgency(agencyRepository.getReferenceById(CurrentUser.agencyId()));
        Property saved = propertyRepository.save(property);
        createDefaultRooms(saved);
        return saved;
    }

    public List<Property> getAllProperties() {
        return propertyRepository.findByAgencyIdOrderByCreatedAtDesc(CurrentUser.agencyId());
    }

    public Property getPropertyById(Long id) {
        return tenantGuard.property(id);
    }

    private void createDefaultRooms(Property property) {
        for (int i = 0; i < DEFAULT_ROOMS.size(); i++) {
            Room room = new Room();
            room.setProperty(property);
            room.setName(DEFAULT_ROOMS.get(i));
            room.setPosition(i + 1);
            roomRepository.save(room);
        }
    }
}
