package com.propertymap.controller.dto;

import com.propertymap.model.InspectionType;

import java.time.LocalDate;
import java.util.List;

/**
 * v0.8:某次 inspection 下某个房间的历史面板数据。三组全部从既有字段推导:
 *   updated         carried_forward = false 且 replaces_photo_id 非空
 *   newlyCaptured   carried_forward = false 且 replaces_photo_id 为空
 *   carriedForward  carried_forward = true
 */
public record RoomHistoryResponse(List<UpdatedPhoto> updated,
                                  List<PhotoResponse> newlyCaptured,
                                  CarriedForward carriedForward) {

    /** 一次更新:旧照片 → 新照片,note 取新照片的 note。 */
    public record UpdatedPhoto(PhotoResponse oldPhoto, PhotoResponse newPhoto, String note) {}

    /**
     * 沿用照片组。origins 按来源 inspection 分组计数、按日期倒序,
     * 供面板折叠态显示 "From entry, 19 Mar 2024 · 11 photos"。
     * 同一组沿用照片可能来自多次 inspection(有些从上上次一路沿用下来),所以是数组。
     */
    public record CarriedForward(int count, List<PhotoResponse> photos, List<OriginSummary> origins) {}

    public record OriginSummary(Long inspectionId, InspectionType type,
                                LocalDate inspectionDate, int photoCount) {}
}
