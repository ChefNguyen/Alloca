package com.alloca.resource.repository;

import com.alloca.resource.domain.Resource;
import jakarta.persistence.LockModeType;
import java.time.Instant;
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
}
