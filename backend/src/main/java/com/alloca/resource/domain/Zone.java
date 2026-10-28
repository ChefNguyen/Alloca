package com.alloca.resource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Building/floor area that contains resources and may carry a floor map image.
 * Never hard-deleted; see {@link #markDeleted}.
 */
@Entity
@Table(name = "zones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Zone {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String floor;

    @Column(nullable = false, length = 100)
    private String building;

    @Column(name = "floor_map_url", columnDefinition = "text")
    private String floorMapUrl;

    @Column(name = "floor_map_width")
    private Integer floorMapWidth;

    @Column(name = "floor_map_height")
    private Integer floorMapHeight;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Zone(UUID id, String name, String floor, String building) {
        this.id = id;
        rename(name, floor, building);
    }

    /** Changes the location labels; the floor map and resource positions stay as they are. */
    public void rename(String name, String floor, String building) {
        this.name = name;
        this.floor = floor;
        this.building = building;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Soft-deleted resources still reference the zone, so the row stays for history. */
    public void markDeleted(Instant now) {
        if (isDeleted()) {
            throw new IllegalStateException("Zone is already deleted");
        }
        this.deletedAt = now;
    }

    public boolean hasFloorMap() {
        return floorMapUrl != null && floorMapWidth != null && floorMapHeight != null;
    }
}
