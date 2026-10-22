package com.alloca.auth.repository;

import com.alloca.auth.domain.Department;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID>{
    Optional<Department> findByName(String name);
}
