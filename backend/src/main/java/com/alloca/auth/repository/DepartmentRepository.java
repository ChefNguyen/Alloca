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

    // Ngăn race condition bằng lock manager của postgresql, auto inject "FOR UPDATE" vào query
    // PostgreSQL cấp lock và transaction 1, transaction chỉ được đánh thức khi transaction 1 commit
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.id = :id")
    Optional<Department> findByIdForUpdate(@Param("id") UUID id);
}
