package com.propertymap.controller.dto;

/** 创建 property 的请求体。字段与前端现有 JSON 完全一致,前端零改动。 */
public record CreatePropertyRequest(String address, String type) {}