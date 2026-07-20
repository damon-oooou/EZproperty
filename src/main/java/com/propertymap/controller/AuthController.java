package com.propertymap.controller;

import com.propertymap.controller.dto.AuthResponse;
import com.propertymap.controller.dto.GoogleLoginRequest;
import com.propertymap.controller.dto.LoginRequest;
import com.propertymap.controller.dto.LogoutRequest;
import com.propertymap.controller.dto.RefreshRequest;
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

    /** v0.7:refresh token 轮换换新 token 对。校验失败统一 401 "Invalid refresh token"。 */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    /** v0.7:服务端真登出(撤销整条 refresh 链)。幂等,重复登出仍 204。 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    /** 需要有效 token(SecurityConfig 只放行了 login/register 两个路径)。 */
    @GetMapping("/me")
    public UserResponse me() {
        return authService.me();
    }
}
