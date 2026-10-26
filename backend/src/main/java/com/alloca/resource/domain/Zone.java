package com.alloca.resource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Building/floor area that contains resources and may carry a floor map image. */
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

    public boolean hasFloorMap() {
        return floorMapUrl != null && floorMapWidth != null && floorMapHeight != null;
    }
}
