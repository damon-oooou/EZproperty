package com.propertymap.controller;

import com.propertymap.controller.dto.CreatePropertyRequest;
import com.propertymap.controller.dto.PropertyResponse;
import com.propertymap.model.Property;
import com.propertymap.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @GetMapping
    public ResponseEntity<List<PropertyResponse>> getAllProperties() {
        return ResponseEntity.ok(propertyService.getAllProperties()
                .stream().map(PropertyResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getProperty(@PathVariable Long id) {
        return ResponseEntity.ok(PropertyResponse.from(propertyService.getPropertyById(id)));
    }

    @PostMapping
    public ResponseEntity<PropertyResponse> createProperty(@RequestBody CreatePropertyRequest request) {
        // 请求体也换成 DTO:请求侧绑实体和响应侧暴露实体是同一类问题
        Property property = new Property();
        property.setAddress(request.address());
        property.setType(request.type());
        return ResponseEntity.ok(PropertyResponse.from(propertyService.createProperty(property)));
    }
}