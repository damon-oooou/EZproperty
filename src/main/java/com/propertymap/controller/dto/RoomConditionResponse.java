package com.propertymap.controller.dto;

/** 服务端合并的产物：全房间列表 + 已填的 condition，未填的 satisfactory/comments 为 null。 */
public record RoomConditionResponse(
        Long roomId,
        String roomName,
        int position,
        Boolean satisfactory,
        String comments
) {}