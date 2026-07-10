package com.propertymap.controller.dto;

import com.propertymap.model.User;

public record UserResponse(Long id, String email, String fullName,
                           Long agencyId, String agencyName) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getAgency().getId(), user.getAgency().getName());
    }
}
