package com.alloca.booking.repository;

import com.alloca.booking.domain.Booking;
import com.alloca.booking.domain.TimeSlot;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    @Override
    @EntityGraph(attributePaths = {"resource", "resource.zone", "resource.department", "user", "user.department", "createdBy", "cancelledBy"})
    Page<Booking> findAll(Specification<Booking> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"resource", "resource.zone", "resource.department", "user", "user.department", "createdBy", "cancelledBy"})
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForReadById(@Param("id") UUID id);

    // Truy vấn giá trị vô hướng (scalar) resourceId để tránh đưa entity Booking vào cache tầng 1 trước khi khóa Resource.
    @Query("select b.resource.id from Booking b where b.id = :id")
    Optional<UUID> findResourceIdById(@Param("id") UUID id);

    /** ALC-38/39/40: Khóa bi quan bản ghi booking (SELECT ... FOR UPDATE) trước khi chuyển trạng thái sang CANCELLED. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Dùng cho ALC-34: Tìm tất cả booking CONFIRMED trong một khoảng thời gian (cả ngày)
     * để đưa vào AvailabilityCalculator lọc slot trống.
     */
    @Query("""
        select new com.alloca.booking.domain.TimeSlot(b.startTime, b.endTime) from Booking b
        where b.resource.id = :resourceId and b.status = com.alloca.booking.domain.BookingStatus.CONFIRMED
        and b.startTime < :intervalEnd
        and b.endTime > :intervalStart
            """)
    List<TimeSlot> findConfirmedSlotsOverlapping(
        @Param("resourceId") UUID resourceId,
        @Param("intervalStart") Instant intervalStart,
        @Param("intervalEnd") Instant intervalEnd
    );

    /**
     * Dùng cho ALC-35: Kiểm tra nhanh xem khung giờ định đặt có bị trùng với booking nào không.
     */
    @Query("""
        select count(b) > 0 from Booking b
        where b.resource.id = :resourceId and b.status = com.alloca.booking.domain.BookingStatus.CONFIRMED
        and b.startTime < :endTime
        and b.endTime > :startTime
            """)
    boolean hasConfirmedConflict(
        @Param("resourceId") UUID resourceId,
        @Param("endTime") Instant endTime,
        @Param("startTime") Instant startTime
    );
}
