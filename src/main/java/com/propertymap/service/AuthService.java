package com.propertymap.service;

import com.propertymap.controller.dto.AuthResponse;
import com.propertymap.controller.dto.LoginRequest;
import com.propertymap.controller.dto.RegisterRequest;
import com.propertymap.controller.dto.UserResponse;
import com.propertymap.model.Agency;
import com.propertymap.model.User;
import com.propertymap.repository.AgencyRepository;
import com.propertymap.repository.UserRepository;
import com.propertymap.security.CurrentUser;
import com.propertymap.security.GoogleTokenVerifier;
import com.propertymap.security.JwtService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AgencyRepository agencyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final RefreshTokenService refreshTokenService;

    /** 开放注册:每个新用户自动获得一个自己的 agency(单人租户)。 */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        Agency agency = new Agency();
        agency.setName(request.agencyName() != null && !request.agencyName().isBlank()
                ? request.agencyName().trim()
                : request.fullName().trim() + "'s Agency");
        agencyRepository.save(agency);

        User user = new User();
        user.setAgency(agency);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        userRepository.save(user);

        return toAuthResponse(user);
    }

    // v0.7:去掉 readOnly——签发 refresh token 要写 refresh_tokens 表
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 找不到用户和密码错误返回同一个消息,不泄露"该邮箱是否已注册"。
        // passwordHash == null 的是纯 Google 账号,不能走密码登录。
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .filter(u -> u.getPasswordHash() != null
                        && passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return toAuthResponse(user);
    }

    /**
     * v0.5.1 Google 登录:验证 ID token 后,老用户直接签发 JWT,
     * 新用户自动注册(和密码注册一样自动建单人 agency)。
     * 同邮箱的密码账号用 Google 登录会直接放行——邮箱已经过 Google 验证,归属没有疑义。
     */
    @Transactional
    public AuthResponse googleLogin(String idToken) {
        GoogleTokenVerifier.GoogleUser googleUser = googleTokenVerifier.verify(idToken);
        String email = googleUser.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            Agency agency = new Agency();
            agency.setName(googleUser.name() + "'s Agency");
            agencyRepository.save(agency);

            User created = new User();
            created.setAgency(agency);
            created.setEmail(email);
            created.setFullName(googleUser.name());
            created.setPasswordHash(null);
            created.setAuthProvider("GOOGLE");
            return userRepository.save(created);
        });

        return toAuthResponse(user);
    }

    /** v0.7:access 过期后用 refresh token 换新 token 对(轮换 + 滑动续期)。 */
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.TokenPair pair = refreshTokenService.rotate(refreshToken);
        // rotate 校验失败(不存在/过期/已撤销/复用)返回 null,统一转 401,不区分原因
        if (pair == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        // 新 access 是我们刚签发的,parse 拿 userId 组装 user 信息,不引入额外返回类型
        User user = userRepository.findById(jwtService.parse(pair.accessToken()).userId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return new AuthResponse(pair.accessToken(), pair.refreshToken(), UserResponse.from(user));
    }

    /** v0.7:服务端真登出,撤销整条 refresh token 链。幂等,不抛错。 */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse toAuthResponse(User user) {
        RefreshTokenService.TokenPair pair = refreshTokenService.issue(user);
        return new AuthResponse(pair.accessToken(), pair.refreshToken(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return userRepository.findById(CurrentUser.get().userId())
                .map(UserResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
