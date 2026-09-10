package com.propertymap.controller.dto;

import com.propertymap.model.InspectionType;

import java.time.LocalDate;

/** v0.8:一张照片的来源 inspection = 最早引用了这张照片的那一次。 */
public record PhotoOrigin(Long inspectionId, InspectionType type, LocalDate inspectionDate) {}
