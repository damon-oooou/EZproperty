package com.propertymap.controller.dto;

import com.propertymap.model.Room;

/** property 语境下的房间(不带照片数——照片数属于某次 inspection,在这里没有意义)。 */
public record RoomResponse(Long id, String name, int position) {

    public static RoomResponse from(Room r) {
        return new RoomResponse(r.getId(), r.getName(), r.getPosition());
    }
}