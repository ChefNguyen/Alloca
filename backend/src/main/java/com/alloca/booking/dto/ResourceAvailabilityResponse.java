package com.alloca.booking.dto;

import com.alloca.resource.domain.ResourceStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ResourceAvailabilityResponse(
        UUID resourceId,
        LocalDate date,
        String timezone,
        ResourceStatus resourceStatus,
        List<TimeSlotResponse> operatingWindows,
        List<TimeSlotResponse> availableSlots
) {
}

