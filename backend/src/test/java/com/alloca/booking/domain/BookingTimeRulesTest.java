package com.alloca.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BookingTimeRulesTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static Instant at(String time) {
        return LocalDate.of(2030, 6, 10)
                .atTime(LocalTime.parse(time))
                .atZone(BUSINESS_ZONE)
                .toInstant();
    }

    @ParameterizedTest
    @CsvSource({
        "09:00, 09:30, true",
        "09:30, 10:00, true",
        "09:15, 10:00, false",
        "09:00, 09:45, false",
        "09:00:01, 10:00, false",
        "09:00, 10:00:01, false",
        "09:00:00.000000001, 10:00, false",
        "09:00, 10:00:00.000000001, false"
    })
    void checksBothEndpointsAgainstTheGrid(String start, String end, boolean expected) {
        TimeSlot slot = new TimeSlot(at(start), at(end));

        assertThat(BookingTimeRules.isOnGrid(slot, BUSINESS_ZONE)).isEqualTo(expected);
    }

    @Test
    void checksTheGridInTheGivenTimezoneRatherThanUtc() {
        Instant start = Instant.parse("2030-06-10T03:15:00Z");
        TimeSlot slot = new TimeSlot(start, start.plusSeconds(30 * 60));
        ZoneId zoneWithQuarterHourOffset = ZoneOffset.ofHoursMinutes(5, 45);

        assertThat(BookingTimeRules.isOnGrid(slot, ZoneOffset.UTC)).isFalse();
        assertThat(BookingTimeRules.isOnGrid(slot, zoneWithQuarterHourOffset)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "1799, false",
        "1800, true",
        "28800, true",
        "28801, false"
    })
    void checksDurationLimits(long seconds, boolean expected) {
        Instant start = at("09:00");
        TimeSlot slot = new TimeSlot(start, start.plusSeconds(seconds));

        assertThat(BookingTimeRules.hasValidDuration(slot))
                .isEqualTo(expected);
    }

    @Test
    void rejectsDurationsOneNanosecondOutsideTheLimits() {
        Instant start = at("09:00");

        TimeSlot justUnder30Minutes = new TimeSlot(
                start, start.plusSeconds(1800).minusNanos(1));

        TimeSlot justOver8Hours = new TimeSlot(
                start, start.plusSeconds(28800).plusNanos(1));

        assertThat(BookingTimeRules.hasValidDuration(justUnder30Minutes)).isFalse();
        assertThat(BookingTimeRules.hasValidDuration(justOver8Hours)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "08:00, 08:00",
        "08:15, 08:30",
        "08:30, 08:30",
        "08:45, 09:00",
        "08:01, 08:30"
    })
    void snapsForwardToGridCorrectly(String inputTime, String expectedSnappedTime) {
        Instant input = at(inputTime);
        Instant expected = at(expectedSnappedTime);

        assertThat(BookingTimeRules.snapForwardToGrid(input, BUSINESS_ZONE)).isEqualTo(expected);
    }
}
