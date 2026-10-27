package com.alloca.auth.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * ALC-26: Tạo tài khoản mới (Admin only).
     */
    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict(ErrorCode.USERNAME_ALREADY_EXISTS, "Username already exists: " + request.username());
        }

        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already exists: " + request.email());
        }

        Department department = null;
        if (request.departmentId() != null) {
            department = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> ApiException.notFound(ErrorCode.DEPARTMENT_NOT_FOUND, "Department not found: " + request.departmentId()));
        }

        String passwordHash = passwordEncoder.encode(request.password());
        UUID userId = UUID.randomUUID();

        User user = new User(
                userId,
                request.username(),
                request.email(),
                passwordHash,
                request.fullName(),
                request.role(),
                department
        );

        if (!request.resolvedIsActive()) {
            user.setActive(false);
        }

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    /**
     * ALC-26: Liệt kê người dùng có phân trang và tìm kiếm (Admin & Manager).
     * Enforce Scope: Manager chỉ được xem user trong department của mình.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(
            UserPrincipal principal,
            UUID queryDepartmentId,
            Role role,
            Boolean isActive,
            String q,
            Pageable pageable
    ) {
        UUID effectiveDepartmentId = queryDepartmentId;

        // Manager scope enforcement
        if (principal.isManager()) {
            UUID managerDeptId = principal.getDepartmentId();
            if (managerDeptId == null) {
                // Manager không có department sẽ không thấy user của phòng ban nào (BR-SCOPE-02)
                return PageResponse.of(Page.empty(pageable), UserResponse::from);
            }
            if (queryDepartmentId != null && !queryDepartmentId.equals(managerDeptId)) {
                // Query param không thể mở rộng phạm vi ra ngoài department của Manager
                return PageResponse.of(Page.empty(pageable), UserResponse::from);
            }
            effectiveDepartmentId = managerDeptId;
        }

        Page<User> page = userRepository.searchUsers(effectiveDepartmentId, role, isActive, q, pageable);
        return PageResponse.of(page, UserResponse::from);
    }

    /**
     * ALC-26: Lấy chi tiết user (Admin hoặc Manager cùng phòng ban).
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(UserPrincipal principal, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));

        // Manager chỉ được xem user cùng department
        if (principal.isManager()) {
            UUID userDeptId = user.getDepartment() != null ? user.getDepartment().getId() : null;
            if (principal.getDepartmentId() == null || !principal.getDepartmentId().equals(userDeptId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied: user is outside your department");
            }
        } else if (principal.isEmployee() && !principal.getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied: employees can only view own profile");
        }

        return UserResponse.from(user);
    }

    /**
     * ALC-26: Cập nhật thông tin user (Admin only).
     */
    @Transactional
    public UserResponse updateUser(UUID userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));

        if (!request.hasUpdates()) {
            throw ApiException.invalidField("request", "At least one field must be provided for update");
        }

        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already exists: " + request.email());
            }
            user.setEmail(request.email());
        }

        if (request.fullName() != null) {
            user.setFullname(request.fullName());
        }

        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            user.incrementCredentialsVersion(); // Thu hồi ngay các refresh token cũ
        }

        if (request.isActive() != null) {
            if (!request.isActive() && user.isActive()) {
                user.deactivate();
            } else if (request.isActive() && !user.isActive()) {
                user.activate();
            }
        }

        User updatedUser = userRepository.save(user);
        return UserResponse.from(updatedUser);
    }

    /**
     * ALC-26: Vô hiệu hóa tài khoản (Admin only).
     * Soft-delete: isActive = false, tăng credentials_version, bảo toàn lịch sử booking.
     */
    @Transactional
    public void deactivateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));

        user.deactivate();
        userRepository.save(user);
    }

    /**
     * ALC-27: Cập nhật Role và Department cho User (Admin only).
     * Ghi nhận audit log và không tự động hủy booking cũ (BR-SCOPE-02).
     */
    @Transactional
    public UserResponse updateUserAccess(UUID adminId, UUID userId, UserAccessUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));

        Department newDept = null;
        if (request.departmentId() != null) {
            newDept = departmentRepository.findById(request.departmentId())
                    .orElseThrow(() -> ApiException.notFound(ErrorCode.DEPARTMENT_NOT_FOUND, "Department not found: " + request.departmentId()));
        }

        Role oldRole = user.getRole();
        UUID oldDeptId = user.getDepartment() != null ? user.getDepartment().getId() : null;

        user.setRole(request.role());
        user.setDepartment(newDept);

        // Ghi nhận Audit Log (ALC-27, BR-SCOPE-02)
        String auditMetadata = String.format("Role changed from %s to %s; Department changed from %s to %s",
                oldRole, request.role(), oldDeptId, request.departmentId());

        ActivityLog activityLog = new ActivityLog(
                UUID.randomUUID(),
                adminId,
                "USER_ACCESS_UPDATED",
                "USER",
                userId,
                auditMetadata
        );
        activityLogRepository.save(activityLog);

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }
}
