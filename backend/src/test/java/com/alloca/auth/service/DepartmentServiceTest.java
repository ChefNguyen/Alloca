package com.alloca.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alloca.auth.api.dto.DepartmentResponse;
import com.alloca.auth.domain.Department;
import com.alloca.auth.repository.DepartmentRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class DepartmentServiceTest {

    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final DepartmentService service = new DepartmentService(departmentRepository);

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
}
