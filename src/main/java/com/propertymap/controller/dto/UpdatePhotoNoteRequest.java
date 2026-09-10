package com.propertymap.controller.dto;

/** v0.8:PATCH /api/photos/{id}/note。note 为 null 或空白 = 清空备注。 */
public record UpdatePhotoNoteRequest(String note) {}
