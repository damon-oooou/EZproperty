package com.propertymap.repository;

import com.propertymap.model.InspectionType;

import java.time.LocalDate;

/**
 * v0.8:一张照片被某次 inspection 引用的一行。
 * 按 (photoId, linkId) 升序取回,每个 photoId 的第一行即"最早引用这张照片的 inspection" = 来源。
 *
 * 用 join 行 id 而不是 inspection_date 判先后:用户可以给 inspection 倒填日期,
 * 同一天也可能有两次 inspection;join 行的创建顺序才是"谁先拥有这张照片"的事实。
 */
public record PhotoOriginRow(Long photoId, Long linkId, Long inspectionId,
                             InspectionType type, LocalDate inspectionDate) {}
