package com.alloca.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BookingTest {
    private static final Instant START = Instant.parse("2030-06-10T02:00:00Z");
    private final User actor = new User(UUID.randomUUID(), "owner", "owner@test.invalid", "hash", "Owner", Role.EMPLOYEE, null);

    @Test
    void cancellationImmediatelyBeforeStartPreservesCreationAndCannotRepeat() {
        Instant created = START.minusSeconds(3600);
        Booking booking = new Booking(UUID.randomUUID(), null, actor, actor, START, START.plusSeconds(3600), "meeting", created);
        assertThat(booking.cancel(actor, "reason", START.minusNanos(1))).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCreatedAt()).isEqualTo(created);
        assertThat(booking.getUpdatedAt()).isEqualTo(START.minusNanos(1));
        assertThat(booking.getCancelledBy()).isSameAs(actor);
        assertThat(booking.cancel(actor, "overwrite", START.minusNanos(1))).isFalse();
        assertThat(booking.getCancelReason()).isEqualTo("reason");
    }

    @ParameterizedTest
    @CsvSource({"CONFIRMED,0", "CONFIRMED,1", "CANCELLED,-1", "COMPLETED,-1"})
    void invalidTransitionDoesNotMutateAudit(BookingStatus status, long offset) {
        Booking booking = new Booking(UUID.randomUUID(), null, actor, actor, START, START.plusSeconds(3600), null, START.minusSeconds(60));
        booking.setStatus(status);
        assertThat(booking.cancel(actor, "reason", START.plusSeconds(offset))).isFalse();
        assertThat(booking.getStatus()).isEqualTo(status);
        assertThat(booking.getCancelledBy()).isNull();
        assertThat(booking.getCancelReason()).isNull();
        assertThat(booking.getUpdatedAt()).isEqualTo(booking.getCreatedAt());
    }
}
