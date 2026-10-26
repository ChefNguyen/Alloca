package com.alloca.resource.dto;

import com.alloca.resource.domain.ResourceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** OpenAPI {@code ResourceStatusUpdateRequest}; only AVAILABLE or SUSPENDED are accepted. */
public record ResourceStatusUpdateRequest(
        @NotNull ResourceStatus status,
        @Size(max = 500) String reason) {
}
