package com.propertymap.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** Google Identity Services 回调里拿到的 credential(ID token)。 */
public record GoogleLoginRequest(@NotBlank String idToken) {
}
