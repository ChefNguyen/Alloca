package com.alloca.analytics.repository;

import com.alloca.analytics.domain.Alert;
import com.alloca.analytics.domain.AlertStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {

    @Query("SELECT a FROM Alert a "
            + "WHERE a.resource.id = :resourceId "
            + "AND a.alertType = :alertType "
            + "AND a.status IN :statuses")
    Optional<Alert> findByResourceIdAndAlertTypeAndStatusIn(
            @Param("resourceId") UUID resourceId,
            @Param("alertType") String alertType,
            @Param("statuses") Collection<AlertStatus> statuses);

    @Override
    @EntityGraph(attributePaths = {"resource", "resource.zone", "resource.department", "acknowledgedBy"})
    Page<Alert> findAll(Specification<Alert> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"resource", "resource.zone", "resource.department", "acknowledgedBy"})
    @Query("SELECT a FROM Alert a WHERE a.id = :id")
    Optional<Alert> findWithRelationsById(@Param("id") UUID id);
}
