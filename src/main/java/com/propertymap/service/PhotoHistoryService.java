package com.propertymap.service;

import com.propertymap.controller.dto.PhotoOrigin;
import com.propertymap.controller.dto.PhotoResponse;
import com.propertymap.controller.dto.RoomHistoryResponse;
import com.propertymap.controller.dto.RoomHistoryResponse.CarriedForward;
import com.propertymap.controller.dto.RoomHistoryResponse.OriginSummary;
import com.propertymap.controller.dto.RoomHistoryResponse.UpdatedPhoto;
import com.propertymap.model.InspectionPhoto;
import com.propertymap.model.Photo;
import com.propertymap.repository.InspectionPhotoRepository;
import com.propertymap.repository.PhotoOriginRow;
import com.propertymap.repository.PhotoRepository;
import com.propertymap.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * v0.8:照片在 inspection 语境下的读取——带 carriedForward 与来源 inspection——
 * 以及房间历史面板的三组分类。
 *
 * 与 getRoomsWithPhotoCounts 同一个理由直接返回 DTO:
 * 把 join 行、照片、来源三份数据合并成一个形状本身就是业务逻辑。
 */
@Service
@RequiredArgsConstructor
public class PhotoHistoryService {

    private final InspectionPhotoRepository inspectionPhotoRepository;
    private final PhotoRepository photoRepository;
    private final PhotoUrlService photoUrlService;
    private final TenantGuard tenantGuard;

    /** 既有 GET photos 端点的数据源,现在每张照片带上 carriedForward + origin。 */
    @Transactional(readOnly = true)
    public List<PhotoResponse> photosForRoom(Long inspectionId, Long roomId) {
        tenantGuard.inspection(inspectionId);
        List<InspectionPhoto> links =
                inspectionPhotoRepository.findLinksByInspectionIdAndRoomId(inspectionId, roomId);
        Map<Long, PhotoOrigin> origins = originsFor(
                links.stream().map(l -> l.getPhoto().getId()).toList());
        return links.stream().map(l -> toResponse(l, origins)).toList();
    }

    @Transactional(readOnly = true)
    public RoomHistoryResponse roomHistory(Long inspectionId, Long roomId) {
        tenantGuard.inspection(inspectionId);
        List<InspectionPhoto> links =
                inspectionPhotoRepository.findLinksByInspectionIdAndRoomId(inspectionId, roomId);

        // 一次取回本组全部照片(含被替换的旧照片)的来源
        List<Long> newIds = links.stream()
                .filter(l -> !l.isCarriedForward())
                .map(l -> l.getPhoto().getReplacesPhotoId())
                .filter(id -> id != null)
                .toList();
        List<Long> allIds = new ArrayList<>(links.stream().map(l -> l.getPhoto().getId()).toList());
        allIds.addAll(newIds);
        Map<Long, PhotoOrigin> origins = originsFor(allIds);

        // 被替换的旧照片一次取回(updated 组用)
        Map<Long, Photo> oldPhotos = newIds.isEmpty() ? Map.of()
                : photoRepository.findAllById(newIds).stream()
                        .collect(Collectors.toMap(Photo::getId, Function.identity()));

        List<UpdatedPhoto> updated = new ArrayList<>();
        List<PhotoResponse> newlyCaptured = new ArrayList<>();
        List<PhotoResponse> carried = new ArrayList<>();

        for (InspectionPhoto link : links) {
            Photo photo = link.getPhoto();
            if (link.isCarriedForward()) {
                carried.add(toResponse(link, origins));
            } else if (photo.getReplacesPhotoId() != null) {
                Photo old = oldPhotos.get(photo.getReplacesPhotoId());
                // 旧照片受 FK RESTRICT 保护不可能缺失;防御性处理:缺失则降级为"本次新拍"
                if (old == null) {
                    newlyCaptured.add(toResponse(link, origins));
                    continue;
                }
                PhotoResponse oldResponse = PhotoResponse.from(
                        old, photoUrlService.urlsFor(old), null, origins.get(old.getId()));
                updated.add(new UpdatedPhoto(oldResponse, toResponse(link, origins), photo.getNote()));
            } else {
                newlyCaptured.add(toResponse(link, origins));
            }
        }

        // 沿用照片按来源 inspection 分组计数,按日期倒序(同日按 id 倒序)
        Map<Long, OriginSummary> byOrigin = new LinkedHashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        for (PhotoResponse p : carried) {
            PhotoOrigin o = p.origin();
            if (o == null) continue;
            counts.merge(o.inspectionId(), 1, Integer::sum);
            byOrigin.putIfAbsent(o.inspectionId(),
                    new OriginSummary(o.inspectionId(), o.type(), o.inspectionDate(), 0));
        }
        List<OriginSummary> originSummaries = byOrigin.values().stream()
                .map(s -> new OriginSummary(s.inspectionId(), s.type(), s.inspectionDate(),
                        counts.get(s.inspectionId())))
                .sorted(Comparator.comparing(OriginSummary::inspectionDate).reversed()
                        .thenComparing(OriginSummary::inspectionId, Comparator.reverseOrder()))
                .toList();

        return new RoomHistoryResponse(updated, newlyCaptured,
                new CarriedForward(carried.size(), carried, originSummaries));
    }

    /**
     * 一组照片的来源:最早引用这张照片的 inspection(按 join 行 id 判先后,见 PhotoOriginRow)。
     * 一次查询,不逐张查。
     */
    private Map<Long, PhotoOrigin> originsFor(Collection<Long> photoIds) {
        if (photoIds.isEmpty()) return Map.of();
        Map<Long, PhotoOrigin> result = new HashMap<>();
        for (PhotoOriginRow row : inspectionPhotoRepository.findOriginRows(photoIds)) {
            // 查询已按 (photoId, linkId) 升序,每个 photoId 第一行即来源
            result.putIfAbsent(row.photoId(),
                    new PhotoOrigin(row.inspectionId(), row.type(), row.inspectionDate()));
        }
        return result;
    }

    private PhotoResponse toResponse(InspectionPhoto link, Map<Long, PhotoOrigin> origins) {
        Photo photo = link.getPhoto();
        return PhotoResponse.from(photo, photoUrlService.urlsFor(photo),
                link.isCarriedForward(), origins.get(photo.getId()));
    }
}
