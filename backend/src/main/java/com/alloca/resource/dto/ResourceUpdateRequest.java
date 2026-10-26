package com.alloca.resource.dto;

import com.alloca.resource.domain.ResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * OpenAPI {@code ResourceUpdateRequest} (partial update).
 *
 * <p>Jackson calls a setter for every property present in the JSON, including explicit
 * {@code null}, so {@link #isPresent} distinguishes "omitted" from "set to null"
 * (e.g. {@code "departmentId": null} turns a resource into a shared resource).
 */
public class ResourceUpdateRequest {

    private final Set<String> present = new HashSet<>();

    @Size(min = 1, max = 100)
    private String name;

    private ResourceType type;

    @Min(1)
    @Max(10000)
    private Integer capacity;

    @Size(max = 2000)
    private String description;

    private UUID zoneId;

    private UUID departmentId;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal utilizationThreshold;

    @Valid
    private MapPosition mapPosition;

    public boolean isPresent(String field) {
        return present.contains(field);
    }

    public boolean isEmpty() {
        return present.isEmpty();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        present.add("name");
    }

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType type) {
        this.type = type;
        present.add("type");
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
        present.add("capacity");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        present.add("description");
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
        present.add("zoneId");
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
        present.add("departmentId");
    }

    public BigDecimal getUtilizationThreshold() {
        return utilizationThreshold;
    }

    public void setUtilizationThreshold(BigDecimal utilizationThreshold) {
        this.utilizationThreshold = utilizationThreshold;
        present.add("utilizationThreshold");
    }

    public MapPosition getMapPosition() {
        return mapPosition;
    }

    public void setMapPosition(MapPosition mapPosition) {
        this.mapPosition = mapPosition;
        present.add("mapPosition");
    }
}
