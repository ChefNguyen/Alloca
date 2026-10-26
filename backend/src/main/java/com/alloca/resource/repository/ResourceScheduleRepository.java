package com.alloca.resource.repository;

import com.alloca.resource.domain.ResourceSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceScheduleRepository extends JpaRepository<ResourceSchedule, UUID> {

    List<ResourceSchedule> findByResourceIdOrderByDayOfWeekAscStartTimeAsc(UUID resourceId);
}
