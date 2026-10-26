package com.alloca.resource.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.util.List;

/** OpenAPI {@code ResourceScheduleReplaceRequest}: the complete weekly schedule, 1 to 21 windows. */
public record ResourceScheduleReplaceRequest(
        @NotNull @Size(min = 1, max = 21) List<@Valid @NotNull Window> schedules) {

    /** {@code HH:mm} or {@code HH:mm:ss}, as in OpenAPI {@code ResourceScheduleInput}. */
    public static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$";

    public record Window(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull @Pattern(regexp = TIME_PATTERN, message = "must be HH:mm or HH:mm:ss") String startTime,
            @NotNull @Pattern(regexp = TIME_PATTERN, message = "must be HH:mm or HH:mm:ss") String endTime) {
    }
}
