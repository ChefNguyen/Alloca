package com.alloca.auth.service;

import com.alloca.auth.api.dto.DepartmentResponse;
import com.alloca.auth.api.dto.DepartmentWriteRequest;
import com.alloca.auth.domain.Department;
import com.alloca.auth.repository.DepartmentRepository;
import com.alloca.auth.repository.UserRepository;
import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.resource.repository.ResourceRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Departments that own resources (for reports/alerts) and group users.
 *
 * <p>A department still referenced by a user or resource cannot be deleted (BR-RESOURCE-02); the
 * row is locked so a concurrent insert referencing it cannot slip in. Assigning a user's
 * department is ALC-27.
 */
@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;

    public DepartmentService(
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            ResourceRepository resourceRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        return departmentRepository.findAll(Sort.by("name")).stream().map(DepartmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(UUID id) {
        return DepartmentResponse.from(departmentRepository.findById(id).orElseThrow(() -> departmentNotFound(id)));
    }

    @Transactional
    public DepartmentResponse create(DepartmentWriteRequest request) {
        String name = request.name().trim();
        requireUniqueName(name, null);
        return DepartmentResponse.from(departmentRepository.save(new Department(UuidCreator.getTimeOrderedEpoch(), name)));
    }

    @Transactional
    public DepartmentResponse update(UUID id, DepartmentWriteRequest request) {
        Department department = departmentRepository.findByIdForUpdate(id).orElseThrow(() -> departmentNotFound(id));
        String name = request.name().trim();
        requireUniqueName(name, id);
        department.setName(name);
        return DepartmentResponse.from(departmentRepository.save(department));
    }

    @Transactional
    public void delete(UUID id) {
        Department department = departmentRepository.findByIdForUpdate(id).orElseThrow(() -> departmentNotFound(id));
        long userCount = userRepository.countByDepartment_Id(id);
        long resourceCount = resourceRepository.countByDepartment_Id(id);
        if (userCount > 0 || resourceCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DEPARTMENT_IN_USE,
                    "Department is still assigned to " + userCount + " user(s) and " + resourceCount
                            + " resource(s); reassign them first",
                    null, Map.of("userCount", userCount, "resourceCount", resourceCount));
        }
        departmentRepository.delete(department);
    }

    private void requireUniqueName(String name, UUID self) {
        departmentRepository.findByName(name)
                .filter(existing -> !existing.getId().equals(self))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DEPARTMENT_NAME_ALREADY_EXISTS,
                            "A department named \"" + name + "\" already exists");
                });
    }

    private static ApiException departmentNotFound(UUID id) {
        return ApiException.notFound(ErrorCode.DEPARTMENT_NOT_FOUND, "Department not found: " + id);
    }
}
