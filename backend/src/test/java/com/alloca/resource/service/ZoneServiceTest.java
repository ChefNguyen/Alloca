package com.alloca.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.Zone;
import com.alloca.resource.dto.ZoneResponse;
import com.alloca.resource.dto.ZoneWriteRequest;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ZoneRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class ZoneServiceTest {

    private final ZoneRepository zoneRepository = mock(ZoneRepository.class);
    private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
    private final Instant now = Instant.parse("2026-10-28T03:00:00Z");
    private ZoneService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ZoneService(zoneRepository, resourceRepository, Clock.fixed(now, ZoneOffset.UTC));
        when(zoneRepository.save(any(Zone.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(zoneRepository.findByDeletedAtIsNull(any(Sort.class))).thenReturn(List.of(
                zone("West Wing", "Level 3", "Tower A", "https://example.com/map.png", 1600, 1000),
                zone("East Wing", "Level 3", "Tower B", null, null, null),
                zone("Lobby", "Level 1", "Tower A", null, null, null)));
    }

    @Test
    void listWithoutFiltersReturnsAllZones() {
        assertThat(service.list(null, null)).extracting(ZoneResponse::name)
                .containsExactly("West Wing", "East Wing", "Lobby");
    }

    @Test
    void listFiltersByBuildingAndFloor() {
        assertThat(service.list("Tower A", null)).extracting(ZoneResponse::name)
                .containsExactly("West Wing", "Lobby");
        assertThat(service.list("Tower A", "Level 3")).extracting(ZoneResponse::name)
                .containsExactly("West Wing");
        assertThat(service.list("Tower C", null)).isEmpty();
    }

    @Test
    void responseExposesFloorMapDetails() {
        ZoneResponse withMap = service.list("Tower A", "Level 3").get(0);

        assertThat(withMap.floorMapUrl()).isEqualTo("https://example.com/map.png");
        assertThat(withMap.floorMapWidth()).isEqualTo(1600);
        assertThat(service.list("Tower B", null).get(0).floorMapUrl()).isNull();
    }

    @Test
    void createTrimsFieldsAndAssignsId() {
        ZoneResponse created = service.create(new ZoneWriteRequest("  West Wing ", " Level 3", "Tower A  "));

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("West Wing");
        assertThat(created.floor()).isEqualTo("Level 3");
        assertThat(created.building()).isEqualTo("Tower A");
        assertThat(created.floorMapUrl()).isNull();
    }

    @Test
    void updateRenamesZoneAndKeepsFloorMap() throws Exception {
        Zone existing = zone("West Wing", "Level 3", "Tower A", "https://example.com/map.png", 1600, 1000);
        when(zoneRepository.findByIdForUpdate(existing.getId())).thenReturn(Optional.of(existing));

        ZoneResponse updated = service.update(existing.getId(), new ZoneWriteRequest("North Wing", "Level 4", "Tower B"));

        assertThat(updated.name()).isEqualTo("North Wing");
        assertThat(updated.floor()).isEqualTo("Level 4");
        assertThat(updated.building()).isEqualTo("Tower B");
        assertThat(updated.floorMapUrl()).isEqualTo("https://example.com/map.png");
    }

    @Test
    void updateUnknownZoneThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(zoneRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new ZoneWriteRequest("A", "B", "C")))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.ZONE_NOT_FOUND);
    }

    @Test
    void getUnknownZoneThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(zoneRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(ErrorCode.ZONE_NOT_FOUND);
    }

    @Test
    void deleteZoneWithoutActiveResourcesSoftDeletesIt() throws Exception {
        Zone existing = zone("Lobby", "Level 1", "Tower A", null, null, null);
        when(zoneRepository.findByIdForUpdate(existing.getId())).thenReturn(Optional.of(existing));
        // Soft-deleted resources may still point at it; only non-deleted ones are counted.
        when(resourceRepository.countByZone_IdAndStatusNot(existing.getId(), ResourceStatus.DELETED)).thenReturn(0L);

        service.delete(existing.getId());

        assertThat(existing.isDeleted()).isTrue();
        assertThat(existing.getDeletedAt()).isEqualTo(now);
        verify(zoneRepository).save(existing);
        verify(zoneRepository, never()).delete(any(Zone.class));
    }

    @Test
    void deletedZoneBehavesAsNotFound() throws Exception {
        Zone deleted = zone("Old Wing", "Level 9", "Tower Z", null, null, null);
        deleted.markDeleted(now);
        when(zoneRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        when(zoneRepository.findByIdForUpdate(deleted.getId())).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.get(deleted.getId()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.ZONE_NOT_FOUND);
        assertThatThrownBy(() -> service.update(deleted.getId(), new ZoneWriteRequest("A", "B", "C")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.ZONE_NOT_FOUND);
        assertThatThrownBy(() -> service.delete(deleted.getId()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.ZONE_NOT_FOUND);
    }

    @Test
    void deleteZoneWithResourcesIsBlockedWithResourceCount() throws Exception {
        Zone existing = zone("Lobby", "Level 1", "Tower A", null, null, null);
        when(zoneRepository.findByIdForUpdate(existing.getId())).thenReturn(Optional.of(existing));
        when(resourceRepository.countByZone_IdAndStatusNot(existing.getId(), ResourceStatus.DELETED)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(existing.getId()))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.ZONE_IN_USE);
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getDetails()).containsEntry("resourceCount", 3L);
                });
        assertThat(existing.isDeleted()).isFalse();
        verify(zoneRepository, never()).save(any(Zone.class));
    }

    private static Zone zone(String name, String floor, String building, String url, Integer width, Integer height)
            throws Exception {
        Constructor<Zone> constructor = Zone.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Zone zone = constructor.newInstance();
        set(zone, "id", UUID.randomUUID());
        set(zone, "name", name);
        set(zone, "floor", floor);
        set(zone, "building", building);
        set(zone, "floorMapUrl", url);
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
