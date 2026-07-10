package com.propertymap.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** service 层取当前用户的入口,避免把 principal 一路当参数传。 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            // 受保护端点走到这里说明 SecurityConfig 配置有漏洞,直接失败比静默返回空数据安全
            throw new IllegalStateException("No authenticated user in security context");
        }
        return user;
    }

    public static Long agencyId() {
        return get().agencyId();
    }
}
