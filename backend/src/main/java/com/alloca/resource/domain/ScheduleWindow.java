package com.alloca.resource.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** One operating window on a weekday, in business-zone local time; {@code start < end}. */
public record ScheduleWindow(DayOfWeek day, LocalTime start, LocalTime end) {
}
