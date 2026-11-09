package com.alloca.analytics.dto;

import com.alloca.resource.dto.ResourceResponse;

public record UtilizationMetric(
        ResourceResponse resource,
        int scheduledMinutes,
        int operatingMinutes,
        Double utilizationPercent
) {}
