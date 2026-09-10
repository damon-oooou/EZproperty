package com.propertymap.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inspection_photos")
@Getter @Setter @NoArgsConstructor
public class InspectionPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id")
    @JsonIgnore
    private Inspection inspection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id")
    private Photo photo;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    /** v0.8:TRUE = 创建 inspection 时从上一次沿用的引用;FALSE = 本次新增(上传或更新)。 */
    @Column(name = "carried_forward", nullable = false)
    private boolean carriedForward;

    /**
     * v0.8:PM 在现场确认过这张沿用照片仍然准确。需要一个明确的用户动作,
     * v0.8 的 web 流程里没有,因此只建字段不写入——不要用创建时间冒充。
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 确认人 user id(FK ON DELETE SET NULL),用 Long 避免加载 User 实体。 */
    @Column(name = "confirmed_by")
    private Long confirmedBy;
}
