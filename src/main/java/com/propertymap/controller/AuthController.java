package com.propertymap.controller;

import com.propertymap.controller.dto.AuthResponse;
import com.propertymap.controller.dto.GoogleLoginRequest;
import com.propertymap.controller.dto.LoginRequest;
import com.propertymap.controller.dto.RegisterRequest;
import com.propertymap.controller.dto.UserResponse;
import com.propertymap.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** v0.5.1:Google 登录/自动注册。 */
    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.googleLogin(request.idToken());
    }

    /** 需要有效 token(SecurityConfig 只放行了 login/register 两个路径)。 */
    @GetMapping("/me")
    public UserResponse me() {
        return authService.me();
    }
}
