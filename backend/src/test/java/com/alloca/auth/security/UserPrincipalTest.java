package com.alloca.auth.security;

import com.alloca.auth.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ALC-25: UserPrincipal & Object Scope Tests (TC-SCOPE-01..04)")
class UserPrincipalTest {

    private final UUID deptA = UUID.randomUUID();
    private final UUID deptB = UUID.randomUUID();
    private final UUID user1Id = UUID.randomUUID();
    private final UUID user2Id = UUID.randomUUID();

    @Test
    @DisplayName("Authorities match Spring Security convention ROLE_<ROLE>")
    void authorities_MatchRoleConvention() {
        UserPrincipal principal = new UserPrincipal(user1Id, "admin", Role.ADMIN, deptA);

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.isManager()).isFalse();
        assertThat(principal.isEmployee()).isFalse();
    }

    @Nested
    @DisplayName("TC-SCOPE: canAccessOwner Object Scope Enforcement")
    class ScopeEnforcement {

        @Test
        @DisplayName("ADMIN has global access across all departments and owners")
        void admin_CanAccessAnyOwner() {
            UserPrincipal admin = new UserPrincipal(user1Id, "admin", Role.ADMIN, deptA);

            assertThat(admin.canAccessOwner(user2Id, deptB)).isTrue();
            assertThat(admin.canAccessOwner(user2Id, deptA)).isTrue();
            assertThat(admin.canAccessOwner(user2Id, null)).isTrue();
        }

        @Test
        @DisplayName("MANAGER can access owners within the same department")
        void manager_CanAccessSameDepartmentOwner() {
            UserPrincipal managerDeptA = new UserPrincipal(user1Id, "managerA", Role.MANAGER, deptA);

            assertThat(managerDeptA.canAccessOwner(user2Id, deptA)).isTrue();
        }

        @Test
        @DisplayName("MANAGER cannot access owners from different departments (TC-SCOPE-01/02)")
        void manager_CannotAccessDifferentDepartmentOwner() {
            UserPrincipal managerDeptA = new UserPrincipal(user1Id, "managerA", Role.MANAGER, deptA);

            assertThat(managerDeptA.canAccessOwner(user2Id, deptB)).isFalse();
            assertThat(managerDeptA.canAccessOwner(user2Id, null)).isFalse();
        }

        @Test
        @DisplayName("MANAGER with null department cannot access departmental owners")
        void managerWithoutDept_CannotAccess() {
            UserPrincipal managerNoDept = new UserPrincipal(user1Id, "managerNoDept", Role.MANAGER, null);

            assertThat(managerNoDept.canAccessOwner(user2Id, deptA)).isFalse();
        }

        @Test
        @DisplayName("EMPLOYEE can only access their own resources (ownerId == principalId)")
        void employee_CanOnlyAccessOwn() {
            UserPrincipal employee1 = new UserPrincipal(user1Id, "emp1", Role.EMPLOYEE, deptA);

            // Access own resource -> allowed
            assertThat(employee1.canAccessOwner(user1Id, deptA)).isTrue();
            assertThat(employee1.canAccessOwner(user1Id, deptB)).isTrue();

            // Access another employee's resource in same department -> rejected (TC-SCOPE-01)
            assertThat(employee1.canAccessOwner(user2Id, deptA)).isFalse();

            // Access another employee's resource in different department -> rejected
            assertThat(employee1.canAccessOwner(user2Id, deptB)).isFalse();
        }
    }
}
