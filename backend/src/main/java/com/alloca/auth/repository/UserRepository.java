package com.alloca.auth.repository;

import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    @EntityGraph(attributePaths = {"department"}) // Override LAZY -> EAGER cho query findByUsername
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    @EntityGraph(attributePaths = {"department"})
    Optional<User> findById(UUID id);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    /**
     * ALC-26: Tìm kiếm người dùng có phân trang theo phòng ban, role, trạng thái active và từ khóa tìm kiếm q.
     */
    @EntityGraph(attributePaths = {"department"})
    @Query("""
        select u from User u
        where (:departmentId is null or u.department.id = :departmentId)
          and (:role is null or u.role = :role)
          and (:isActive is null or u.isActive = :isActive)
          and (:q is null or :q = '' or (
               lower(u.username) like lower(concat('%', :q, '%'))
            or lower(u.fullname) like lower(concat('%', :q, '%'))
            or lower(u.email) like lower(concat('%', :q, '%'))
          ))
    """)
    Page<User> searchUsers(
            @Param("departmentId") UUID departmentId,
            @Param("role") Role role,
            @Param("isActive") Boolean isActive,
            @Param("q") String q,
            Pageable pageable
    );

    long countByDepartment_Id(UUID departmentId);
}
