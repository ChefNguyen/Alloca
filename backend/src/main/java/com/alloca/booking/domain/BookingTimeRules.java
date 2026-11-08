package com.alloca.booking.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

// utility class, không cho phép kế thừa, mở rộng, gọi method thông qua class không phải object
public final class BookingTimeRules {
    private BookingTimeRules(){
    }

    public static boolean isOnGrid(TimeSlot slot, ZoneId businessZone){
        return isBoundary(slot.startTime(), businessZone) && 
            isBoundary(slot.endTime(), businessZone);
    }

    public static boolean isBoundary(Instant time, ZoneId businessZone){
        ZonedDateTime localTime = time.atZone(businessZone);

        return localTime.getMinute() % 30 == 0
            && localTime.getSecond() == 0
            && localTime.getNano() == 0;
    }

    public static Instant snapForwardToGrid(Instant instant, ZoneId businessZone) {
        if (instant == null || businessZone == null) {
            throw new IllegalArgumentException("Instant and businessZone are required");
        }
        ZonedDateTime zdt = instant.atZone(businessZone);
        if (zdt.getMinute() % 30 == 0 && zdt.getSecond() == 0 && zdt.getNano() == 0) {
            return instant;
        }
        int minute = zdt.getMinute();
        int minutesToAdd = 30 - (minute % 30);
        return zdt.plusMinutes(minutesToAdd).withSecond(0).withNano(0).toInstant();
    }

    public static boolean hasValidDuration(TimeSlot slot) {
        Duration duration = Duration.between(slot.startTime(), slot.endTime());
        return duration.compareTo(Duration.ofMinutes(30)) >= 0
                && duration.compareTo(Duration.ofHours(8)) <= 0;
    }
}
