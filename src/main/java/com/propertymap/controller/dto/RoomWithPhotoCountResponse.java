package com.propertymap.controller.dto;

/** inspection 语境下的房间:固定房间全集 + 本次 inspection 的照片数。 */
public record RoomWithPhotoCountResponse(Long id, String name, int position, long photoCount) {}