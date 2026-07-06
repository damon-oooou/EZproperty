package com.propertymap.controller.dto;

import com.propertymap.model.Inspection;
import com.propertymap.model.InspectionType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InspectionResponse(Long id, InspectionType type, LocalDate inspectionDate,
                                 LocalDateTime createdAt) {

    public static InspectionResponse from(Inspection i) {
        return new InspectionResponse(i.getId(), i.getType(), i.getInspectionDate(), i.getCreatedAt());
    }
}