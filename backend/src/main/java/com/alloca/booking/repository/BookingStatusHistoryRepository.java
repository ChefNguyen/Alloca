package com.alloca.booking.repository;

import com.alloca.booking.domain.BookingStatusHistory;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, UUID> {
    @EntityGraph(attributePaths = "changedBy")
    List<BookingStatusHistory> findByBookingIdOrderByChangedAtAscIdAsc(UUID bookingId);
}
