package com.propertymap.controller.dto;

/** PUT /conditions 请求体数组的元素。 */
public record RoomConditionUpdate(
        Long roomId,
        Boolean satisfactory,
        String comments
) {}