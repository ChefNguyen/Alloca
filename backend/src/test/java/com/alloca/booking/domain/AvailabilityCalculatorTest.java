package com.alloca.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

    private static Instant at(String time) {
        return Instant.parse("2030-06-10T" + time + ":00Z");
    }

    @Nested
    @DisplayName("splitIntoSlots")
    class SplitIntoSlots {

        @Test
        @DisplayName("Splits a window evenly into 30-minute slots")
        void splitsEvenWindowIntoSlots() {
            TimeSlot window = new TimeSlot(at("08:00"), at("10:00"));
            Duration step = Duration.ofMinutes(30);

            List<TimeSlot> slots = AvailabilityCalculator.splitIntoSlots(window, step);

            assertThat(slots).containsExactly(
                    new TimeSlot(at("08:00"), at("08:30")),
                    new TimeSlot(at("08:30"), at("09:00")),
                    new TimeSlot(at("09:00"), at("09:30")),
                    new TimeSlot(at("09:30"), at("10:00"))
            );
        }

        @Test
        @DisplayName("Drops uneven trailing minutes that cannot fit a full slot")
        void dropsUnevenTrailingWindow() {
            TimeSlot window = new TimeSlot(at("08:00"), at("08:50"));
            Duration step = Duration.ofMinutes(30);

            List<TimeSlot> slots = AvailabilityCalculator.splitIntoSlots(window, step);

            assertThat(slots).containsExactly(
                    new TimeSlot(at("08:00"), at("08:30"))
            );
        }

        @Test
        @DisplayName("Returns empty list when window duration is less than slot step")
        void returnsEmptyWhenWindowShorterThanStep() {
            TimeSlot window = new TimeSlot(at("08:00"), at("08:20"));
            Duration step = Duration.ofMinutes(30);

            List<TimeSlot> slots = AvailabilityCalculator.splitIntoSlots(window, step);

            assertThat(slots).isEmpty();
        }

        @Test
        @DisplayName("Rejects invalid arguments (null, zero, or negative step)")
        void rejectsInvalidArguments() {
            TimeSlot window = new TimeSlot(at("08:00"), at("10:00"));

            assertThatThrownBy(() -> AvailabilityCalculator.splitIntoSlots(null, Duration.ofMinutes(30)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> AvailabilityCalculator.splitIntoSlots(window, null))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> AvailabilityCalculator.splitIntoSlots(window, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> AvailabilityCalculator.splitIntoSlots(window, Duration.ofMinutes(-30)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("excludeBooked")
    class ExcludeBooked {

        private final List<TimeSlot> candidates = List.of(
                new TimeSlot(at("08:00"), at("08:30")),
                new TimeSlot(at("08:30"), at("09:00")),
                new TimeSlot(at("09:00"), at("09:30")),
                new TimeSlot(at("09:30"), at("10:00"))
        );

        @Test
        @DisplayName("Returns all candidates when there are no confirmed bookings")
        void returnsAllWhenNoBookings() {
            assertThat(AvailabilityCalculator.excludeBooked(candidates, Collections.emptyList()))
                    .isEqualTo(candidates);

            assertThat(AvailabilityCalculator.excludeBooked(candidates, null))
                    .isEqualTo(candidates);
        }

        @Test
        @DisplayName("Filters out slots that exactly match a confirmed booking")
        void filtersOutExactMatchBooking() {
            List<TimeSlot> booked = List.of(new TimeSlot(at("08:30"), at("09:00")));

            List<TimeSlot> available = AvailabilityCalculator.excludeBooked(candidates, booked);

            assertThat(available).containsExactly(
                    new TimeSlot(at("08:00"), at("08:30")),
                    new TimeSlot(at("09:00"), at("09:30")),
                    new TimeSlot(at("09:30"), at("10:00"))
            );
        }

        @Test
        @DisplayName("Filters out multiple slots covered by a longer booking")
        void filtersOutMultiSlotBooking() {
            List<TimeSlot> booked = List.of(new TimeSlot(at("08:30"), at("10:00")));

            List<TimeSlot> available = AvailabilityCalculator.excludeBooked(candidates, booked);

            assertThat(available).containsExactly(
                    new TimeSlot(at("08:00"), at("08:30"))
            );
        }

        @Test
        @DisplayName("Preserves adjacent slots that touch the boundary without overlapping")
        void preservesAdjacentSlots() {
            // A booking from 08:30 to 09:30 touches [08:00, 08:30) and [09:30, 10:00) at exact boundaries
            List<TimeSlot> booked = List.of(new TimeSlot(at("08:30"), at("09:30")));

            List<TimeSlot> available = AvailabilityCalculator.excludeBooked(candidates, booked);

            assertThat(available).containsExactly(
                    new TimeSlot(at("08:00"), at("08:30")),
                    new TimeSlot(at("09:30"), at("10:00"))
            );
        }

        @Test
        @DisplayName("Filters out all slots affected by a partial overlap")
        void filtersOutPartiallyOverlappingSlots() {
            // Booking 08:15 to 08:45 intersects with both [08:00, 08:30) and [08:30, 09:00)
            List<TimeSlot> booked = List.of(new TimeSlot(at("08:15"), at("08:45")));

            List<TimeSlot> available = AvailabilityCalculator.excludeBooked(candidates, booked);

            assertThat(available).containsExactly(
                    new TimeSlot(at("09:00"), at("09:30")),
                    new TimeSlot(at("09:30"), at("10:00"))
            );
        }
    }
}

