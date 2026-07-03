package com.propertymap.controller.dto;

import com.propertymap.model.InspectionType;

import java.time.LocalDate;

public record CreateInspectionRequest(
        InspectionType type,
        LocalDate inspectionDate,
        boolean inheritFromPrevious
) {}