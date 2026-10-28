package com.alloca.resource.repository;

import com.alloca.resource.domain.Zone;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    List<Zone> findByDeletedAtIsNull(Sort sort);

    /** {@code SELECT ... FOR UPDATE}; a concurrent resource insert into this zone waits for the lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select z from Zone z where z.id = :id")
    Optional<Zone> findByIdForUpdate(@Param("id") UUID id);
}
