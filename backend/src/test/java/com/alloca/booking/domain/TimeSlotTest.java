package com.alloca.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeSlotTest {

    private static Instant at(String time) {
        return Instant.parse("2030-06-10T" + time + ":00Z");
    }

    @Test
    void createsValidInterval() {
        TimeSlot slot = new TimeSlot(at("09:00"), at("10:00"));

        assertThat(slot.startTime()).isEqualTo(at("09:00"));
        assertThat(slot.endTime()).isEqualTo(at("10:00"));
    }

    @Test
    void rejectsMissingEndpoints() {
        assertThatThrownBy(() -> new TimeSlot(null, at("10:00")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new TimeSlot(at("09:00"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "09:00, 09:00",
        "10:00, 09:00"
    })
    void rejectsEmptyOrReversedInterval(String start, String end) {
        assertThatThrownBy(() -> new TimeSlot(at(start), at(end)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "08:00, 09:00, false",
        "10:00, 11:00, false",
        "11:00, 12:00, false",
        "09:30, 10:30, true",
        "08:30, 09:30, true",
        "09:00, 10:00, true",
        "09:15, 09:45, true",
        "08:00, 11:00, true"
    })
    void detectsOverlap(String start, String end, boolean expected) {
        TimeSlot existing = new TimeSlot(at("09:00"), at("10:00"));
        TimeSlot candidate = new TimeSlot(at(start), at(end));

        assertThat(existing.overlaps(candidate)).isEqualTo(expected);
        assertThat(candidate.overlaps(existing)).isEqualTo(expected);
    }
}
