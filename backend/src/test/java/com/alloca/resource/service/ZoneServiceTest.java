package com.alloca.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alloca.resource.domain.Zone;
import com.alloca.resource.dto.ZoneResponse;
import com.alloca.resource.repository.ZoneRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class ZoneServiceTest {

    private final ZoneRepository zoneRepository = mock(ZoneRepository.class);
    private ZoneService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ZoneService(zoneRepository);
        when(zoneRepository.findAll(any(Sort.class))).thenReturn(List.of(
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
