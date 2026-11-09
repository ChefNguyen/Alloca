package com.alloca.analytics.dto;

import com.alloca.analytics.domain.Alert;
import com.alloca.analytics.domain.AlertStatus;
import com.alloca.resource.dto.ResourceResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        ResourceResponse resource,
        AlertStatus status,
        LocalDate windowStart,
        LocalDate windowEnd,
        BigDecimal utilizationPercent,
        BigDecimal threshold,
        UUID acknowledgedBy,
        Instant acknowledgedAt,
        Instant resolvedAt,
        String resolutionReason,
        Instant createdAt
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                ResourceResponse.from(alert.getResource()),
                alert.getStatus(),
                alert.getPeriodStartLocal(),
                alert.getPeriodEndLocal(),
                alert.getActualPercent(),
                alert.getThresholdPercent(),
                alert.getAcknowledgedBy() != null ? alert.getAcknowledgedBy().getId() : null,
                alert.getStatus() == AlertStatus.ACKNOWLEDGED ? alert.getTriggeredAt() : null,
                alert.getResolvedAt(),
                alert.getResolutionReason(),
                alert.getTriggeredAt()
        );
    }
}
