package com.propertymap.service;

import com.propertymap.controller.dto.ReportDetailsResponse;
import com.propertymap.controller.dto.RoomConditionResponse;
import com.propertymap.controller.dto.RoomConditionUpdate;
import com.propertymap.controller.dto.UpdateReportDetailsRequest;
import com.propertymap.model.*;
import com.propertymap.repository.*;
import com.propertymap.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final RoomRepository roomRepository;
    private final RoomConditionRepository roomConditionRepository;
    private final ReportDetailsRepository reportDetailsRepository;
    private final TenantGuard tenantGuard;

    /**
     * 和 getRoomsWithPhotoCounts 同一个套路：全房间列表 + 已填数据服务端合并，
     * 没填过的房间也出现在结果里（satisfactory/comments 为 null）。
     */
    @Transactional(readOnly = true)
    public List<RoomConditionResponse> getConditions(Long inspectionId) {
        Inspection inspection = getInspectionOrThrow(inspectionId);

        List<Room> rooms = roomRepository.findByPropertyIdOrderByPosition(
                inspection.getProperty().getId());

        Map<Long, RoomCondition> filled = roomConditionRepository
                .findByInspectionIdWithRoom(inspectionId)
                .stream()
                .collect(Collectors.toMap(rc -> rc.getRoom().getId(), Function.identity()));

        return rooms.stream()
                .map(r -> {
                    RoomCondition rc = filled.get(r.getId());
                    return new RoomConditionResponse(
                            r.getId(), r.getName(), r.getPosition(),
                            rc != null ? rc.getSatisfactory() : null,
                            rc != null ? rc.getComments() : null);
                })
                .toList();
    }

    /** 批量 upsert：有行就更新，没行就插入。返回合并后的最新状态。 */
    @Transactional
    public List<RoomConditionResponse> updateConditions(Long inspectionId,
                                                        List<RoomConditionUpdate> updates) {
        Inspection inspection = getInspectionOrThrow(inspectionId);

        Map<Long, Room> roomsOfProperty = roomRepository
                .findByPropertyIdOrderByPosition(inspection.getProperty().getId())
                .stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));

        Map<Long, RoomCondition> existing = roomConditionRepository
                .findByInspectionIdWithRoom(inspectionId)
                .stream()
                .collect(Collectors.toMap(rc -> rc.getRoom().getId(), Function.identity()));

        for (RoomConditionUpdate u : updates) {
            Room room = roomsOfProperty.get(u.roomId());
            if (room == null) {
                throw new IllegalArgumentException(
                        "Room " + u.roomId() + " does not belong to the same property as inspection " + inspectionId);
            }
            RoomCondition rc = existing.get(u.roomId());
            if (rc == null) {
                rc = new RoomCondition();
                rc.setInspection(inspection);
                rc.setRoom(room);
            }
            rc.setSatisfactory(u.satisfactory());
            rc.setComments(u.comments());
            roomConditionRepository.save(rc);
        }
        return getConditions(inspectionId);
    }

    @Transactional(readOnly = true)
    public ReportDetailsResponse getReportDetails(Long inspectionId) {
        getInspectionOrThrow(inspectionId);
        return reportDetailsRepository.findById(inspectionId)
                .map(ReportDetailsResponse::from)
                .orElse(ReportDetailsResponse.empty(inspectionId));
    }

    @Transactional
    public ReportDetailsResponse updateReportDetails(Long inspectionId,
                                                     UpdateReportDetailsRequest req) {
        Inspection inspection = getInspectionOrThrow(inspectionId);

        ReportDetails details = reportDetailsRepository.findById(inspectionId)
                .orElseGet(() -> {
                    ReportDetails d = new ReportDetails();
                    d.setInspection(inspection);
                    return d;
                });

        details.setLandlordName(req.landlordName());
        details.setTenantName(req.tenantName());
        details.setLeaseExpiry(req.leaseExpiry());
        details.setSmokeAlarmsPresent(req.smokeAlarmsPresent());
        details.setSmokeAlarmsLocation(req.smokeAlarmsLocation());
        details.setTenantRepairsCarriedOut(req.tenantRepairsCarriedOut());
        details.setUrgentAction(req.urgentAction());
        details.setGeneralComments(req.generalComments());
        details.setTenantActionRequired(req.tenantActionRequired());
        details.setAgentName(req.agentName());
        details.setAgentTradingAs(req.agentTradingAs());
        details.setDisclaimer(req.disclaimer());

        return ReportDetailsResponse.from(reportDetailsRepository.save(details));
    }

    /** v0.5:归属校验统一收口到 TenantGuard,不属于当前 agency 一律 404。 */
    private Inspection getInspectionOrThrow(Long inspectionId) {
        return tenantGuard.inspection(inspectionId);
    }
}