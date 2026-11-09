package com.alloca.analytics.domain;

import com.alloca.auth.domain.User;
import com.alloca.resource.domain.Resource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alert {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType = "HIGH_UTILIZATION";

    @Column(name = "period_start_local", nullable = false)
    private LocalDate periodStartLocal;

    @Column(name = "period_end_local", nullable = false)
    private LocalDate periodEndLocal;

    @Column(name = "dedup_key", nullable = false, unique = true, length = 255)
    private String dedupKey;

    @Column(name = "threshold_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal thresholdPercent;

    @Column(name = "actual_percent", precision = 7, scale = 2)
    private BigDecimal actualPercent;

    @Column(name = "booked_minutes", nullable = false)
    private int bookedMinutes;

    @Column(name = "available_minutes", nullable = false)
    private int availableMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "triggered_at", nullable = false, updatable = false)
    private Instant triggeredAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_reason", columnDefinition = "text")
    private String resolutionReason;

    public Alert(
            UUID id,
            Resource resource,
            LocalDate periodStartLocal,
            LocalDate periodEndLocal,
            String dedupKey,
            BigDecimal thresholdPercent,
            BigDecimal actualPercent,
            int bookedMinutes,
            int availableMinutes,
            Instant triggeredAt) {
        this.id = id;
        this.resource = resource;
        this.alertType = "HIGH_UTILIZATION";
        this.periodStartLocal = periodStartLocal;
        this.periodEndLocal = periodEndLocal;
        this.dedupKey = dedupKey;
        this.thresholdPercent = thresholdPercent;
        this.actualPercent = actualPercent;
        this.bookedMinutes = bookedMinutes;
        this.availableMinutes = availableMinutes;
        this.status = AlertStatus.OPEN;
        this.triggeredAt = triggeredAt;
    }

    public void updateMetrics(
            BigDecimal actualPercent,
            int bookedMinutes,
            int availableMinutes,
            LocalDate periodStartLocal,
            LocalDate periodEndLocal) {
        this.actualPercent = actualPercent;
        this.bookedMinutes = bookedMinutes;
        this.availableMinutes = availableMinutes;
        this.periodStartLocal = periodStartLocal;
        this.periodEndLocal = periodEndLocal;
    }

    public void acknowledge(User user) {
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedBy = user;
    }

    public void resolve(String reason, Instant now) {
        this.status = AlertStatus.RESOLVED;
        this.resolutionReason = reason;
        this.resolvedAt = now;
    }

    public boolean isActive() {
        return this.status == AlertStatus.OPEN || this.status == AlertStatus.ACKNOWLEDGED;
    }
}
