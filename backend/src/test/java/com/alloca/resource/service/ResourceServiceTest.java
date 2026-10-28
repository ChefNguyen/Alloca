package com.alloca.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alloca.auth.domain.Department;
import com.alloca.auth.repository.DepartmentRepository;
import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import com.alloca.resource.domain.Zone;
import com.alloca.resource.dto.MapPosition;
import com.alloca.resource.dto.ResourceCreateRequest;
import com.alloca.resource.dto.ResourceResponse;
import com.alloca.resource.dto.ResourceStatusUpdateRequest;
import com.alloca.resource.dto.ResourceUpdateRequest;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import com.alloca.resource.repository.ZoneRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ResourceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");

    private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
    private final ResourceScheduleRepository scheduleRepository = mock(ResourceScheduleRepository.class);
    private final ZoneRepository zoneRepository = mock(ZoneRepository.class);
    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);

    private ResourceService service;
    private Zone zone;

    @BeforeEach
    void setUp() throws Exception {
        service = new ResourceService(resourceRepository, scheduleRepository, zoneRepository, departmentRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        zone = zone(UUID.randomUUID(), 1600, 1000);
        when(zoneRepository.findById(zone.getId())).thenReturn(Optional.of(zone));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(any())).thenReturn(List.of());
    }

    @Test
    void createDefaultsToAvailableSharedResourceWithDefaultThreshold() {
        ResourceResponse created = service.create(new ResourceCreateRequest(
                "  Boardroom 301 ", ResourceType.ROOM, 12, null, null, zone.getId(), null, null));

        assertThat(created.name()).isEqualTo("Boardroom 301");
        assertThat(created.status()).isEqualTo(ResourceStatus.AVAILABLE);
        assertThat(created.department()).isNull();
        assertThat(created.utilizationThreshold()).isEqualByComparingTo("85");
        assertThat(created.zone().id()).isEqualTo(zone.getId());
    }

    @Test
    void createRejectsDeletedInitialStatus() {
        assertThatThrownBy(() -> service.create(new ResourceCreateRequest(
                "Projector", ResourceType.EQUIPMENT, 1, ResourceStatus.DELETED, null, zone.getId(), null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void createRejectsUnknownZone() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(new ResourceCreateRequest(
                "Projector", ResourceType.EQUIPMENT, 1, null, null, unknown, null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.ZONE_NOT_FOUND));
    }

    @Test
    void createRejectsDeletedZone() {
        zone.markDeleted(NOW);
        assertThatThrownBy(() -> service.create(new ResourceCreateRequest(
                "Projector", ResourceType.EQUIPMENT, 1, null, null, zone.getId(), null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.ZONE_NOT_FOUND));
    }

    @Test
    void updateOnlyTouchesPresentFieldsAndExplicitNullMakesResourceShared() {
        Resource resource = existing(ResourceStatus.AVAILABLE);
        resource.setDescription("keep me");

        ResourceUpdateRequest request = new ResourceUpdateRequest();
        request.setCapacity(20);
        request.setDepartmentId(null);

        var updated = service.update(resource.getId(), request);

        assertThat(updated.capacity()).isEqualTo(20);
        assertThat(updated.description()).isEqualTo("keep me");
        assertThat(updated.department()).isNull();
        assertThat(updated.name()).isEqualTo("Pod A");
    }

    @Test
    void updateRejectsMapPositionOutsideFloorMap() {
        Resource resource = existing(ResourceStatus.AVAILABLE);
        ResourceUpdateRequest request = new ResourceUpdateRequest();
        request.setMapPosition(new MapPosition(1500, 0, 200, 100));

        assertThatThrownBy(() -> service.update(resource.getId(), request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getFieldErrors()).extracting("field").containsExactly("mapPosition"));
    }

    @Test
    void updateRejectsNullForNonNullableField() {
        Resource resource = existing(ResourceStatus.AVAILABLE);
        ResourceUpdateRequest request = new ResourceUpdateRequest();
        request.setName(null);

        assertThatThrownBy(() -> service.update(resource.getId(), request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getFieldErrors()).extracting("field").containsExactly("name"));
    }

    @Test
    void suspendAndReactivate() {
        Resource resource = existing(ResourceStatus.AVAILABLE);

        assertThat(service.updateStatus(resource.getId(),
                new ResourceStatusUpdateRequest(ResourceStatus.SUSPENDED, "maintenance")).status())
                .isEqualTo(ResourceStatus.SUSPENDED);
        assertThat(service.updateStatus(resource.getId(),
                new ResourceStatusUpdateRequest(ResourceStatus.AVAILABLE, null)).status())
                .isEqualTo(ResourceStatus.AVAILABLE);
        verify(resourceRepository, org.mockito.Mockito.times(2)).findByIdForUpdate(resource.getId());
    }

    @Test
    void deleteFromAvailableIsRejected() {
        Resource resource = existing(ResourceStatus.AVAILABLE);

        assertThatThrownBy(() -> service.delete(resource.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_RESOURCE_STATUS_TRANSITION);
                });
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.AVAILABLE);
    }

    @Test
    void deleteWithActiveBookingsReturnsConflictWithBookingIds() {
        Resource resource = existing(ResourceStatus.SUSPENDED);
        UUID bookingId = UUID.randomUUID();
        when(resourceRepository.findActiveConfirmedBookingIds(resource.getId(), NOW)).thenReturn(List.of(bookingId));

        assertThatThrownBy(() -> service.delete(resource.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_HAS_ONGOING_OR_FUTURE_BOOKINGS);
                    assertThat(ex.getDetails()).isEqualTo(Map.of("conflictingBookingIds", List.of(bookingId)));
                });
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.SUSPENDED);
    }

    @Test
    void deleteSuspendedResourceWithoutBookingsSoftDeletes() {
        Resource resource = existing(ResourceStatus.SUSPENDED);
        when(resourceRepository.findActiveConfirmedBookingIds(resource.getId(), NOW)).thenReturn(List.of());

        service.delete(resource.getId());

        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.DELETED);
        assertThat(resource.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    void deletedResourceIsNotFound() {
        Resource resource = existing(ResourceStatus.SUSPENDED);
        resource.markDeleted(NOW);
        when(resourceRepository.findWithRelationsById(resource.getId())).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> service.get(resource.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(() -> service.delete(resource.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void createRejectsUnknownDepartment() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(new ResourceCreateRequest(
                "Projector", ResourceType.EQUIPMENT, 1, null, null, zone.getId(), unknown, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    @Test
    void createAssignsDepartmentAndKeepsExplicitThreshold() {
        Department department = new Department(UUID.randomUUID(), "Engineering");
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));

        ResourceResponse created = service.create(new ResourceCreateRequest(
                "Pod B", ResourceType.DESK, 1, ResourceStatus.SUSPENDED, "quiet", zone.getId(),
                department.getId(), new BigDecimal("70")));

        assertThat(created.department().name()).isEqualTo("Engineering");
        assertThat(created.status()).isEqualTo(ResourceStatus.SUSPENDED);
        assertThat(created.utilizationThreshold()).isEqualByComparingTo("70");
        assertThat(created.id()).isNotNull();
    }

    @Test
    void updateRejectsEmptyRequest() {
        Resource resource = existing(ResourceStatus.AVAILABLE);

        assertThatThrownBy(() -> service.update(resource.getId(), new ResourceUpdateRequest()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateRejectsBlankName() {
        Resource resource = existing(ResourceStatus.AVAILABLE);
        ResourceUpdateRequest request = new ResourceUpdateRequest();
        request.setName("   ");

        assertThatThrownBy(() -> service.update(resource.getId(), request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getFieldErrors()).extracting("field").containsExactly("name"));
    }

    @Test
    void updateUnknownResourceIsNotFound() {
        UUID id = UUID.randomUUID();
        when(resourceRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        ResourceUpdateRequest request = new ResourceUpdateRequest();
        request.setCapacity(2);

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                });
    }

    @Test
    void updateSetsAndClearsMapPositionInsideFloorMap() {
        Resource resource = existing(ResourceStatus.AVAILABLE);
        ResourceUpdateRequest place = new ResourceUpdateRequest();
        place.setMapPosition(new MapPosition(100, 50, 200, 100));

        assertThat(service.update(resource.getId(), place).mapPosition())
                .isEqualTo(new MapPosition(100, 50, 200, 100));

        ResourceUpdateRequest clear = new ResourceUpdateRequest();
        clear.setMapPosition(null);
        assertThat(service.update(resource.getId(), clear).mapPosition()).isNull();
    }

    @Test
    void updateChangesZoneAndRejectsPositionOutsideNewZone() throws Exception {
        Resource resource = existing(ResourceStatus.AVAILABLE);
        resource.placeOnMap(1000, 0, 200, 100);
        Zone small = zone(UUID.randomUUID(), 800, 600);
        when(zoneRepository.findById(small.getId())).thenReturn(Optional.of(small));
        ResourceUpdateRequest request = new ResourceUpdateRequest();
        request.setZoneId(small.getId());

        assertThatThrownBy(() -> service.update(resource.getId(), request))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getFieldErrors()).extracting("field").containsExactly("mapPosition"));
    }

    @Test
    void updateStatusToDeletedIsRejected() {
        Resource resource = existing(ResourceStatus.SUSPENDED);

        assertThatThrownBy(() -> service.updateStatus(resource.getId(),
                new ResourceStatusUpdateRequest(ResourceStatus.DELETED, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_RESOURCE_STATUS_TRANSITION));
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.SUSPENDED);
    }

    @Test
    void getReturnsDetailForActiveResource() {
        Resource resource = existing(ResourceStatus.AVAILABLE);
        when(resourceRepository.findWithRelationsById(resource.getId())).thenReturn(Optional.of(resource));

        assertThat(service.get(resource.getId()).name()).isEqualTo("Pod A");
    }

    private Resource existing(ResourceStatus status) {
        Resource resource = new Resource(UUID.randomUUID(), status);
        resource.setName("Pod A");
        resource.setType(ResourceType.ROOM);
        resource.setCapacity(4);
        resource.setZone(zone);
        when(resourceRepository.findByIdForUpdate(resource.getId())).thenReturn(Optional.of(resource));
        return resource;
    }

    private static Zone zone(UUID id, int width, int height) throws Exception {
        Constructor<Zone> constructor = Zone.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Zone zone = constructor.newInstance();
        set(zone, "id", id);
        set(zone, "name", "Level 3 West Wing");
        set(zone, "floor", "Level 3");
        set(zone, "building", "Tower A");
        set(zone, "floorMapUrl", "https://example.com/map.png");
        set(zone, "floorMapWidth", width);
        set(zone, "floorMapHeight", height);
        return zone;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
