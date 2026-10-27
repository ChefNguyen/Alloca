package com.alloca.auth.service;

import com.alloca.auth.api.dto.DepartmentSummaryResponse;
import com.alloca.auth.api.dto.UserAccessUpdateRequest;
import com.alloca.auth.api.dto.UserCreateRequest;
import com.alloca.auth.api.dto.UserResponse;
import com.alloca.auth.api.dto.UserUpdateRequest;
import com.alloca.auth.domain.ActivityLog;
import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.repository.ActivityLogRepository;
import com.alloca.auth.repository.DepartmentRepository;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.common.api.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ALC-26 & ALC-27: UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private Department deptEngineering;
    private Department deptSales;
    private User testUser;
    private UserPrincipal adminPrincipal;
    private UserPrincipal managerEngPrincipal;

    @BeforeEach
    void setUp() {
        deptEngineering = new Department(UUID.randomUUID(), "Engineering");
        deptSales = new Department(UUID.randomUUID(), "Sales");

        testUser = new User(
                UUID.randomUUID(),
                "john.doe",
                "john@alloca.com",
                "hashed_pwd",
                "John Doe",
                Role.EMPLOYEE,
                deptEngineering
        );

        adminPrincipal = new UserPrincipal(UUID.randomUUID(), "admin", Role.ADMIN, null);
        managerEngPrincipal = new UserPrincipal(UUID.randomUUID(), "manager.eng", Role.MANAGER, deptEngineering.getId());
    }

    @Nested
    @DisplayName("ALC-26: createUser")
    class CreateUserTests {

        @Test
        @DisplayName("Create user with valid data -> success, password hashed with BCrypt")
        void createUser_Success() {
            UserCreateRequest request = new UserCreateRequest(
                    "new.user",
                    "new@alloca.com",
                    "password123",
                    "New User",
                    Role.EMPLOYEE,
                    deptEngineering.getId(),
                    true
            );

            when(userRepository.existsByUsername("new.user")).thenReturn(false);
            when(userRepository.existsByEmail("new@alloca.com")).thenReturn(false);
            when(departmentRepository.findById(deptEngineering.getId())).thenReturn(Optional.of(deptEngineering));
            when(passwordEncoder.encode("password123")).thenReturn("bcrypt_hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.createUser(request);

            assertThat(response).isNotNull();
            assertThat(response.username()).isEqualTo("new.user");
            assertThat(response.email()).isEqualTo("new@alloca.com");
            assertThat(response.fullname()).isEqualTo("New User");
            assertThat(response.role()).isEqualTo(Role.EMPLOYEE);
            assertThat(response.isActive()).isTrue();
            assertThat(response.department().id()).isEqualTo(deptEngineering.getId());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPasswordHash()).isEqualTo("bcrypt_hash");
            assertThat(captor.getValue().getCredentialsVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("Duplicate username throws 409 USERNAME_ALREADY_EXISTS")
        void createUser_DuplicateUsername_ThrowsConflict() {
            UserCreateRequest request = new UserCreateRequest(
                    "existing.user", "email@alloca.com", "pass1234", "Name", Role.EMPLOYEE, null, true
            );
            when(userRepository.existsByUsername("existing.user")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(apiEx.getCode()).isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS);
                    });

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duplicate email throws 409 EMAIL_ALREADY_EXISTS")
        void createUser_DuplicateEmail_ThrowsConflict() {
            UserCreateRequest request = new UserCreateRequest(
                    "new.user", "existing@alloca.com", "pass1234", "Name", Role.EMPLOYEE, null, true
            );
            when(userRepository.existsByUsername("new.user")).thenReturn(false);
            when(userRepository.existsByEmail("existing@alloca.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(apiEx.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
                    });

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Non-existent departmentId throws 404 DEPARTMENT_NOT_FOUND")
        void createUser_DepartmentNotFound_ThrowsNotFound() {
            UUID unknownDeptId = UUID.randomUUID();
            UserCreateRequest request = new UserCreateRequest(
                    "new.user", "email@alloca.com", "pass1234", "Name", Role.EMPLOYEE, unknownDeptId, true
            );
            when(userRepository.existsByUsername("new.user")).thenReturn(false);
            when(userRepository.existsByEmail("email@alloca.com")).thenReturn(false);
            when(departmentRepository.findById(unknownDeptId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(apiEx.getCode()).isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("ALC-26: listUsers & Scope Enforcement")
    class ListUsersTests {

        @Test
        @DisplayName("ADMIN can search across all departments")
        void listUsers_Admin_SearchesAll() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(List.of(testUser), pageable, 1);

            when(userRepository.searchUsers(null, null, null, null, pageable)).thenReturn(page);

            PageResponse<UserResponse> result = userService.listUsers(adminPrincipal, null, null, null, null, pageable);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).username()).isEqualTo(testUser.getUsername());
        }

        @Test
        @DisplayName("MANAGER scope is strictly confined to own department")
        void listUsers_Manager_ConfinedToOwnDepartment() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(List.of(testUser), pageable, 1);

            when(userRepository.searchUsers(deptEngineering.getId(), null, null, null, pageable)).thenReturn(page);

            PageResponse<UserResponse> result = userService.listUsers(managerEngPrincipal, null, null, null, null, pageable);

            assertThat(result.items()).hasSize(1);
            verify(userRepository).searchUsers(eq(deptEngineering.getId()), any(), any(), any(), eq(pageable));
        }

        @Test
        @DisplayName("MANAGER attempting to query different department gets empty page (cannot broaden scope)")
        void listUsers_Manager_QueryDifferentDept_ReturnsEmpty() {
            Pageable pageable = PageRequest.of(0, 10);

            PageResponse<UserResponse> result = userService.listUsers(
                    managerEngPrincipal, deptSales.getId(), null, null, null, pageable
            );

            assertThat(result.items()).isEmpty();
            verify(userRepository, never()).searchUsers(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("MANAGER without assigned department gets empty page (BR-SCOPE-02)")
        void listUsers_ManagerNoDept_ReturnsEmpty() {
            UserPrincipal managerNoDept = new UserPrincipal(UUID.randomUUID(), "mgr", Role.MANAGER, null);
            Pageable pageable = PageRequest.of(0, 10);

            PageResponse<UserResponse> result = userService.listUsers(managerNoDept, null, null, null, null, pageable);

            assertThat(result.items()).isEmpty();
            verify(userRepository, never()).searchUsers(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("ALC-26: getUser Detail & Scope")
    class GetUserTests {

        @Test
        @DisplayName("ADMIN can view any user detail")
        void getUser_Admin_Success() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            UserResponse response = userService.getUser(adminPrincipal, testUser.getId());
            assertThat(response.id()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("MANAGER can view user in same department")
        void getUser_ManagerSameDept_Success() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            UserResponse response = userService.getUser(managerEngPrincipal, testUser.getId());
            assertThat(response.id()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("MANAGER viewing user in different department throws 403 ACCESS_DENIED")
        void getUser_ManagerDifferentDept_ThrowsForbidden() {
            User salesUser = new User(
                    UUID.randomUUID(), "sales.rep", "sales@alloca.com", "hash", "Sales Rep", Role.EMPLOYEE, deptSales
            );
            when(userRepository.findById(salesUser.getId())).thenReturn(Optional.of(salesUser));

            assertThatThrownBy(() -> userService.getUser(managerEngPrincipal, salesUser.getId()))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                    });
        }

        @Test
        @DisplayName("User not found throws 404 USER_NOT_FOUND")
        void getUser_NotFound_ThrowsNotFound() {
            UUID unknownId = UUID.randomUUID();
            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser(adminPrincipal, unknownId))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(apiEx.getCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                    });
        }
    }

    @Nested
    @DisplayName("ALC-26: updateUser")
    class UpdateUserTests {

        @Test
        @DisplayName("Update password hashes with BCrypt and increments credentialsVersion (BR-AUTH-02)")
        void updateUser_PasswordChange_IncrementsCredentialsVersion() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.encode("brandNewPassword")).thenReturn("new_hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserUpdateRequest request = new UserUpdateRequest(null, null, null, "brandNewPassword");

            UserResponse response = userService.updateUser(testUser.getId(), request);

            assertThat(testUser.getPasswordHash()).isEqualTo("new_hash");
            assertThat(testUser.getCredentialsVersion()).isEqualTo(2); // Invalidation of old refresh tokens!
        }

        @Test
        @DisplayName("Update active to false calls deactivate() and increments credentialsVersion")
        void updateUser_Deactivate_Success() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserUpdateRequest request = new UserUpdateRequest(null, null, false, null);

            userService.updateUser(testUser.getId(), request);

            assertThat(testUser.isActive()).isFalse();
            assertThat(testUser.getCredentialsVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("Update with no fields provided throws 400 VALIDATION_ERROR")
        void updateUser_NoFields_ThrowsBadRequest() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            UserUpdateRequest emptyRequest = new UserUpdateRequest(null, null, null, null);

            assertThatThrownBy(() -> userService.updateUser(testUser.getId(), emptyRequest))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }
    }

    @Nested
    @DisplayName("ALC-26: deactivateUser")
    class DeactivateUserTests {

        @Test
        @DisplayName("Deactivate user soft-deletes and increments credentialsVersion (BR-AUTH-01/02)")
        void deactivateUser_Success() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            userService.deactivateUser(testUser.getId());

            assertThat(testUser.isActive()).isFalse();
            assertThat(testUser.getCredentialsVersion()).isEqualTo(2);
            verify(userRepository).save(testUser);
        }
    }

    @Nested
    @DisplayName("ALC-27: updateUserAccess & Audit Log")
    class UpdateUserAccessTests {

        @Test
        @DisplayName("Admin updates role and department -> updates user and records activity log")
        void updateUserAccess_Success() {
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(departmentRepository.findById(deptSales.getId())).thenReturn(Optional.of(deptSales));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserAccessUpdateRequest request = new UserAccessUpdateRequest(Role.MANAGER, deptSales.getId());

            UserResponse response = userService.updateUserAccess(adminPrincipal.getId(), testUser.getId(), request);

            assertThat(testUser.getRole()).isEqualTo(Role.MANAGER);
            assertThat(testUser.getDepartment()).isEqualTo(deptSales);

            // Verify Audit Log recorded in activity_logs
            ArgumentCaptor<ActivityLog> logCaptor = ArgumentCaptor.forClass(ActivityLog.class);
            verify(activityLogRepository).save(logCaptor.capture());
            ActivityLog log = logCaptor.getValue();

            assertThat(log.getAction()).isEqualTo("USER_ACCESS_UPDATED");
            assertThat(log.getEntityType()).isEqualTo("USER");
            assertThat(log.getEntityId()).isEqualTo(testUser.getId());
            assertThat(log.getUserId()).isEqualTo(adminPrincipal.getId());
            assertThat(log.getMetadata()).contains("Role changed from EMPLOYEE to MANAGER");
            assertThat(log.getMetadata()).contains(deptSales.getId().toString());
        }
    }
}
