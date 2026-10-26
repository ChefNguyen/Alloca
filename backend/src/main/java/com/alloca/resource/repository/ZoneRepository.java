package com.alloca.resource.repository;

import com.alloca.resource.domain.Zone;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
}
