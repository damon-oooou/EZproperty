package com.propertymap.controller.dto;

// v0.7:双 token。旧 token 字段不保留(破坏性变更,前后端同批部署)。
public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
}
