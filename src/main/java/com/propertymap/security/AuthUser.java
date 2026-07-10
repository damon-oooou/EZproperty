package com.propertymap.security;

/**
 * 认证后的当前用户,从 JWT claims 还原,作为 SecurityContext 的 principal。
 * 只放请求处理需要的最小信息,不带实体,避免 controller 层意外触发数据库查询。
 */
public record AuthUser(Long userId, Long agencyId, String email) {
}
