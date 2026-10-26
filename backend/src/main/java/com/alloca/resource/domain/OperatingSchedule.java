package com.alloca.resource.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A resource's weekly operating hours (FR-RES-02). Days without a window are closed.
 *
 * <p>Touching windows (08:00-12:00 and 12:00-17:00) are merged, so a booking may span them.
 * Overlap and {@code start < end} are checked before a schedule is accepted, see
 * {@code ScheduleInputValidator}; this class assumes valid windows.
 */
public final class OperatingSchedule {

    private final Map<DayOfWeek, List<ScheduleWindow>> mergedByDay = new EnumMap<>(DayOfWeek.class);
    private final ZoneId zone;

    public OperatingSchedule(List<ScheduleWindow> windows, ZoneId zone) {
        this.zone = zone;
        for (DayOfWeek day : DayOfWeek.values()) {
            List<ScheduleWindow> merged = new ArrayList<>();
            windows.stream()
                    .filter(window -> window.day() == day)
                    .sorted(Comparator.comparing(ScheduleWindow::start))
                    .forEach(window -> {
                        ScheduleWindow last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
                        if (last != null && !window.start().isAfter(last.end())) {
                            if (window.end().isAfter(last.end())) {
                                merged.set(merged.size() - 1, new ScheduleWindow(day, last.start(), window.end()));
                            }
                        } else {
                            merged.add(window);
                        }
                    });
            mergedByDay.put(day, merged);
        }
    }

    /**
     * Whether {@code [start, end)} lies entirely inside one merged window of a single local day.
     * Bookings that cross local midnight are never allowed (booking-and-time spec).
     */
    public boolean allows(Instant start, Instant end) {
        ZonedDateTime localStart = start.atZone(zone);
        ZonedDateTime localEnd = end.atZone(zone);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            return false;
        }
        return mergedByDay.get(localStart.getDayOfWeek()).stream()
                .anyMatch(window -> !localStart.toLocalTime().isBefore(window.start())
                        && !localEnd.toLocalTime().isAfter(window.end()));
    }

    public List<ScheduleWindow> mergedWindows(DayOfWeek day) {
        return List.copyOf(mergedByDay.get(day));
    }
}
