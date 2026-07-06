package com.propertymap.repository;

/**
 * 查询投影:某次 inspection 中,每个房间的照片数量。
 * 由 JPQL 构造器表达式直接实例化,不是实体。
 */
public record RoomPhotoCount(Long roomId, long count) {}