package com.alloca.booking.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AvailabilityCalculator {
    private AvailabilityCalculator() {
    }

    public static List<TimeSlot> excludeBooked(
            List<TimeSlot> candidates,
            List<TimeSlot> confirmedBookings
    ) {
        if (confirmedBookings == null || confirmedBookings.isEmpty()) {
            return List.copyOf(candidates);
        }

        return candidates.stream()
                .filter(candidate -> confirmedBookings.stream().noneMatch(candidate::overlaps))
                .toList();
    }

    public static List<TimeSlot> splitIntoSlots(TimeSlot window, Duration step) {
        if (window == null || step == null || step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("Window and positive step duration are required");
        }
        List<TimeSlot> slots = new ArrayList<>();
        Instant current = window.startTime();
        while (!current.plus(step).isAfter(window.endTime())) {
            Instant next = current.plus(step);
            slots.add(new TimeSlot(current, next));
            current = next;
        }
        return slots;
    }
}
