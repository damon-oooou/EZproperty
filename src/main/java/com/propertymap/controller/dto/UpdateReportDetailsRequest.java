package com.propertymap.controller.dto;

import java.time.LocalDate;

public record UpdateReportDetailsRequest(
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
) {}