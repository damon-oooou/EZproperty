package com.propertymap.controller.dto;

import com.propertymap.model.Property;
import java.time.LocalDateTime;

public record PropertyResponse(Long id, String address, String type, LocalDateTime createdAt) {

    public static PropertyResponse from(Property p) {
        return new PropertyResponse(p.getId(), p.getAddress(), p.getType(), p.getCreatedAt());
    }
}