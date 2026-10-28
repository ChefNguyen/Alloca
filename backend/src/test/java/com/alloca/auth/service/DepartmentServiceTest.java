package com.alloca.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alloca.auth.api.dto.DepartmentResponse;
import com.alloca.auth.api.dto.DepartmentWriteRequest;
import com.alloca.auth.domain.Department;
import com.alloca.auth.repository.DepartmentRepository;
import com.alloca.auth.repository.UserRepository;
import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.resource.repository.ResourceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class DepartmentServiceTest {

    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
    private final DepartmentService service =
            new DepartmentService(departmentRepository, userRepository, resourceRepository);

    @BeforeEach
    void saveReturnsArgument() {
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listMapsDepartments() {
        Department engineering = new Department(UUID.randomUUID(), "Engineering");
        when(departmentRepository.findAll(any(Sort.class))).thenReturn(List.of(engineering));

        List<DepartmentResponse> result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(engineering.getId());
        assertThat(result.get(0).name()).isEqualTo("Engineering");
        assertThat(result.get(0).createdAt()).isNotNull();
    }

    @Test
    void listIsEmptyWhenNoDepartments() {
        when(departmentRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(service.list()).isEmpty();
    }

    @Test
    void createTrimsName() {
        when(departmentRepository.findByName("Engineering")).thenReturn(Optional.empty());

        DepartmentResponse created = service.create(new DepartmentWriteRequest("  Engineering "));

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Engineering");
    }

    @Test
    void createWithDuplicateNameIsRejected() {
        when(departmentRepository.findByName("Engineering"))
                .thenReturn(Optional.of(new Department(UUID.randomUUID(), "Engineering")));

        assertThatThrownBy(() -> service.create(new DepartmentWriteRequest("Engineering")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.DEPARTMENT_NAME_ALREADY_EXISTS);
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    void updateKeepingOwnNameIsAllowed() {
        Department engineering = new Department(UUID.randomUUID(), "Engineering");
        when(departmentRepository.findByIdForUpdate(engineering.getId())).thenReturn(Optional.of(engineering));
        when(departmentRepository.findByName("Engineering")).thenReturn(Optional.of(engineering));

        assertThat(service.update(engineering.getId(), new DepartmentWriteRequest("Engineering")).name())
                .isEqualTo("Engineering");
    }

    @Test
    void updateToAnotherDepartmentsNameIsRejected() {
        Department engineering = new Department(UUID.randomUUID(), "Engineering");
        when(departmentRepository.findByIdForUpdate(engineering.getId())).thenReturn(Optional.of(engineering));
        when(departmentRepository.findByName("Sales"))
                .thenReturn(Optional.of(new Department(UUID.randomUUID(), "Sales")));

        assertThatThrownBy(() -> service.update(engineering.getId(), new DepartmentWriteRequest("Sales")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.DEPARTMENT_NAME_ALREADY_EXISTS);
        assertThat(engineering.getName()).isEqualTo("Engineering");
    }

    @Test
    void updateUnknownDepartmentThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(departmentRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new DepartmentWriteRequest("X")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    @Test
    void deleteUnreferencedDepartmentRemovesIt() {
        Department sales = new Department(UUID.randomUUID(), "Sales");
        when(departmentRepository.findByIdForUpdate(sales.getId())).thenReturn(Optional.of(sales));

        service.delete(sales.getId());

        verify(departmentRepository).delete(sales);
    }

    @Test
    void deleteDepartmentStillReferencedIsBlocked() {
        Department sales = new Department(UUID.randomUUID(), "Sales");
        when(departmentRepository.findByIdForUpdate(sales.getId())).thenReturn(Optional.of(sales));
        when(userRepository.countByDepartment_Id(sales.getId())).thenReturn(2L);
        when(resourceRepository.countByDepartment_Id(sales.getId())).thenReturn(0L);

        assertThatThrownBy(() -> service.delete(sales.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.DEPARTMENT_IN_USE);
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getDetails()).containsEntry("userCount", 2L).containsEntry("resourceCount", 0L);
                });
        verify(departmentRepository, never()).delete(any(Department.class));
    }
}
