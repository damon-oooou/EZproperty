package com.propertymap.controller;

import com.propertymap.model.Room;
import com.propertymap.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/properties/{propertyId}/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<List<Room>> getRooms(@PathVariable Long propertyId) {
        return ResponseEntity.ok(roomService.getRoomsByProperty(propertyId));
    }

    @PostMapping
    public ResponseEntity<Room> addRoom(
            @PathVariable Long propertyId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(roomService.addRoom(propertyId, body.get("name")));
    }
}