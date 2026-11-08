package com.alloca.resource.repository;

import com.alloca.resource.domain.ResourceSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceScheduleRepository extends JpaRepository<ResourceSchedule, UUID> {

    List<ResourceSchedule> findByResourceIdOrderByDayOfWeekAscStartTimeAsc(UUID resourceId);

    List<ResourceSchedule> findByResourceIdAndDayOfWeekOrderByStartTimeAsc(UUID resourceId, short dayOfWeek);

    /**
     * Bulk delete that is flushed immediately. Hibernate otherwise runs inserts before deletes,
     * which would violate {@code uq_resource_schedules_window} when a window is re-saved unchanged.
     */
    // Modifying là câu lệnh ghi, không phải select
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ResourceSchedule s where s.resourceId = :resourceId")
    void deleteByResourceId(@Param("resourceId") UUID resourceId);
}
