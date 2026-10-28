package com.alloca.auth.repository;

import com.alloca.auth.domain.Department;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID>{
    Optional<Department> findByName(String name);

    /** {@code SELECT ... FOR UPDATE}; a concurrent user/resource insert referencing it waits for the lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.id = :id")
    Optional<Department> findByIdForUpdate(@Param("id") UUID id);
}
