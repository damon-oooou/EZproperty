package com.propertymap.controller.dto;

import com.propertymap.model.ReportDetails;

import java.time.LocalDate;

public record ReportDetailsResponse(
        Long inspectionId,
        String landlordName,
        String tenantName,
        LocalDate leaseExpiry,
        Boolean smokeAlarmsPresent,
        String smokeAlarmsLocation,
        Boolean tenantRepairsCarriedOut,
        String urgentAction,
        String generalComments,
        String tenantActionRequired,
        String agentName,
        String agentTradingAs,
        String disclaimer
) {
    public static ReportDetailsResponse from(ReportDetails d) {
        return new ReportDetailsResponse(
                d.getInspectionId(),
                d.getLandlordName(),
                d.getTenantName(),
                d.getLeaseExpiry(),
                d.getSmokeAlarmsPresent(),
                d.getSmokeAlarmsLocation(),
                d.getTenantRepairsCarriedOut(),
                d.getUrgentAction(),
                d.getGeneralComments(),
                d.getTenantActionRequired(),
                d.getAgentName(),
                d.getAgentTradingAs(),
                d.getDisclaimer()
        );
    }

    /** 还没填过 report details 时返回空壳而不是 404，前端表单直接渲染空值。 */
    public static ReportDetailsResponse empty(Long inspectionId) {
        return new ReportDetailsResponse(inspectionId,
                null, null, null, null, null, null,
                null, null, null, null, null, null);
    }
}