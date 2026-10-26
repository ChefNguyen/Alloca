package com.alloca.resource.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperatingScheduleTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    // 2026-10-26 is a Monday; Vietnam is UTC+7.
    private static Instant monday(int hour, int minute) {
        return Instant.parse("2026-10-26T00:00:00Z").plusSeconds(((hour - 7) * 60L + minute) * 60);
    }

    private static ScheduleWindow window(DayOfWeek day, String start, String end) {
        return new ScheduleWindow(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void allowsBookingInsideAWindowIncludingTheBoundaries() {
        OperatingSchedule schedule = new OperatingSchedule(
                List.of(window(DayOfWeek.MONDAY, "08:00", "18:00")), VN);

        assertThat(schedule.allows(monday(9, 0), monday(11, 0))).isTrue();
        assertThat(schedule.allows(monday(8, 0), monday(18, 0))).isTrue();
    }

    @Test
    void rejectsBookingThatStartsBeforeOrEndsAfterTheWindow() {
        OperatingSchedule schedule = new OperatingSchedule(
                List.of(window(DayOfWeek.MONDAY, "08:00", "18:00")), VN);

        assertThat(schedule.allows(monday(7, 30), monday(9, 0))).isFalse();
        assertThat(schedule.allows(monday(17, 30), monday(18, 30))).isFalse();
    }

    @Test
    void daysWithoutAWindowAreClosed() {
        OperatingSchedule schedule = new OperatingSchedule(
                List.of(window(DayOfWeek.TUESDAY, "08:00", "18:00")), VN);

        assertThat(schedule.allows(monday(9, 0), monday(10, 0))).isFalse();
    }

    @Test
    void touchingWindowsAreMergedSoABookingCanSpanThem() {
        OperatingSchedule schedule = new OperatingSchedule(List.of(
                window(DayOfWeek.MONDAY, "12:00", "17:00"),
                window(DayOfWeek.MONDAY, "08:00", "12:00")), VN);

        assertThat(schedule.mergedWindows(DayOfWeek.MONDAY))
                .containsExactly(window(DayOfWeek.MONDAY, "08:00", "17:00"));
        assertThat(schedule.allows(monday(11, 0), monday(13, 0))).isTrue();
    }

    @Test
    void aLunchBreakBetweenWindowsBlocksABookingAcrossIt() {
        OperatingSchedule schedule = new OperatingSchedule(List.of(
                window(DayOfWeek.MONDAY, "08:00", "12:00"),
                window(DayOfWeek.MONDAY, "13:00", "17:00")), VN);

        assertThat(schedule.mergedWindows(DayOfWeek.MONDAY)).hasSize(2);
        assertThat(schedule.allows(monday(11, 0), monday(14, 0))).isFalse();
        assertThat(schedule.allows(monday(13, 0), monday(14, 0))).isTrue();
    }

    @Test
    void weekdayIsDecidedInBusinessTimeNotUtc() {
        // 02:00 UTC on Monday is 09:00 in Vietnam, still Monday; 18:00 UTC Sunday is 01:00 Monday there.
        OperatingSchedule mondayOnly = new OperatingSchedule(
                List.of(window(DayOfWeek.MONDAY, "00:30", "03:00")), VN);

        assertThat(mondayOnly.allows(Instant.parse("2026-10-25T18:00:00Z"), Instant.parse("2026-10-25T19:00:00Z")))
                .isTrue();
    }

    @Test
    void bookingCrossingLocalMidnightIsNeverAllowed() {
        OperatingSchedule schedule = new OperatingSchedule(
                List.of(window(DayOfWeek.MONDAY, "00:00", "23:59"), window(DayOfWeek.TUESDAY, "00:00", "23:59")), VN);

        assertThat(schedule.allows(monday(23, 30), monday(23, 30).plusSeconds(3600))).isFalse();
    }
}
