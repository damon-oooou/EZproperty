package com.propertymap.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_details")
@Getter @Setter @NoArgsConstructor
public class ReportDetails {

    /** 主键即 inspection 的主键（@MapsId），一对一。 */
    @Id
    @Column(name = "inspection_id")
    private Long inspectionId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "inspection_id")
    private Inspection inspection;

    @Column(name = "landlord_name")
    private String landlordName;

    @Column(name = "tenant_name")
    private String tenantName;

    @Column(name = "lease_expiry")
    private LocalDate leaseExpiry;

    @Column(name = "smoke_alarms_present")
    private Boolean smokeAlarmsPresent;

    @Column(name = "smoke_alarms_location", columnDefinition = "text")
    private String smokeAlarmsLocation;

    @Column(name = "tenant_repairs_carried_out")
    private Boolean tenantRepairsCarriedOut;

    @Column(name = "urgent_action", columnDefinition = "text")
    private String urgentAction;

    @Column(name = "general_comments", columnDefinition = "text")
    private String generalComments;

    @Column(name = "tenant_action_required", columnDefinition = "text")
    private String tenantActionRequired;

    @Column(name = "agent_name")
    private String agentName;

    @Column(name = "agent_trading_as")
    private String agentTradingAs;

    @Column(columnDefinition = "text")
    private String disclaimer;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}