package com.alloca.resource.dto;

import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * OpenAPI {@code ResourceCreateRequest}.
 *
 * <p>{@code zoneId} is required because {@code resources.zone_id} is NOT NULL in the schema.
 */
public record ResourceCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull ResourceType type,
        @NotNull @Min(1) @Max(10000) Integer capacity,
        ResourceStatus status,
        @Size(max = 2000) String description,
        @NotNull UUID zoneId,
        UUID departmentId,
        @DecimalMin("0") @DecimalMax("100") BigDecimal utilizationThreshold) {
}
