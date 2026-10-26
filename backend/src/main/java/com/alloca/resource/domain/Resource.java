package com.alloca.resource.domain;

import com.alloca.auth.domain.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bookable room, desk or piece of equipment. Never hard-deleted; see {@link #markDeleted}. */
@Entity
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resource {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType type;

    @Column(nullable = false)
    private int capacity;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceStatus status;

    @Column(columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    /** Owning department for reports/alerts; {@code null} means a shared resource. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "utilization_threshold", precision = 5, scale = 2)
    private BigDecimal utilizationThreshold;

    @Setter(AccessLevel.NONE)
    @Column(name = "map_x")
    private Integer mapX;

    @Setter(AccessLevel.NONE)
    @Column(name = "map_y")
    private Integer mapY;

    @Setter(AccessLevel.NONE)
    @Column(name = "map_width")
    private Integer mapWidth;

    @Setter(AccessLevel.NONE)
    @Column(name = "map_height")
    private Integer mapHeight;

    @Setter(AccessLevel.NONE)
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Resource(UUID id, ResourceStatus initialStatus) {
        if (initialStatus == ResourceStatus.DELETED) {
            throw new IllegalArgumentException("A resource cannot be created as DELETED");
        }
        this.id = id;
        this.status = initialStatus;
    }

    public boolean isDeleted() {
        return status == ResourceStatus.DELETED;
    }

    public boolean hasMapPosition() {
        return mapX != null;
    }

    public void placeOnMap(int x, int y, int width, int height) {
        this.mapX = x;
        this.mapY = y;
        this.mapWidth = width;
        this.mapHeight = height;
    }

    public void clearMapPosition() {
        this.mapX = null;
        this.mapY = null;
        this.mapWidth = null;
        this.mapHeight = null;
    }

    /** Only AVAILABLE <-> SUSPENDED; deletion goes through {@link #markDeleted}. */
    public void changeStatus(ResourceStatus next) {
        if (next == ResourceStatus.DELETED || isDeleted()) {
            throw new IllegalStateException("Use markDeleted for soft deletion");
        }
        this.status = next;
    }

    public void markDeleted(Instant now) {
        if (status != ResourceStatus.SUSPENDED) {
            throw new IllegalStateException("Resource must be SUSPENDED before deletion");
        }
        this.status = ResourceStatus.DELETED;
        this.deletedAt = now;
    }
}
