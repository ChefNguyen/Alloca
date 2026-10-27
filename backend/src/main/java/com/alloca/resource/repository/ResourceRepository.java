package com.alloca.resource.repository;

import com.alloca.resource.domain.Resource;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, UUID>, JpaSpecificationExecutor<Resource> {

    @Override
    @EntityGraph(attributePaths = {"zone", "department"})
    Page<Resource> findAll(Specification<Resource> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"zone", "department"})
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findWithRelationsById(@Param("id") UUID id);

    /** {@code SELECT ... FOR UPDATE} on the resource row (BR-RESOURCE-02 transactional lock). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findByIdForUpdate(@Param("id") UUID id);

    /** CONFIRMED bookings that are ongoing or upcoming ({@code end_time > now}). */
    @Query(value = """
            select b.id from bookings b
            where b.resource_id = :resourceId
              and b.status = 'CONFIRMED'
              and b.end_time > :now
            order by b.start_time
            """, nativeQuery = true)
    List<UUID> findActiveConfirmedBookingIds(@Param("resourceId") UUID resourceId, @Param("now") Instant now);

    /** An ongoing or upcoming CONFIRMED booking, with its interval in UTC. */
    record ActiveBooking(UUID id, Instant startTime, Instant endTime) {
    }

    /** Raw rows {@code [id, start_time, end_time]}; use {@link #findActiveConfirmedBookings}. */
    @Query(value = """
            select b.id, b.start_time, b.end_time from bookings b
            where b.resource_id = :resourceId
              and b.status = 'CONFIRMED'
              and b.end_time > :now
            order by b.start_time
            """, nativeQuery = true)
    List<Object[]> findActiveConfirmedBookingRows(@Param("resourceId") UUID resourceId, @Param("now") Instant now);

    default List<ActiveBooking> findActiveConfirmedBookings(UUID resourceId, Instant now) {
        return findActiveConfirmedBookingRows(resourceId, now).stream()
                .map(row -> new ActiveBooking((UUID) row[0], toInstant(row[1]), toInstant(row[2])))
                .toList();
    }

    /** The driver may return any of these for timestamptz, depending on Hibernate/JDBC settings. */
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        } else if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        } else if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        } else if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        throw new IllegalStateException("Unexpected timestamp type " + (value != null ? value.getClass() : "null"));
    }
}
