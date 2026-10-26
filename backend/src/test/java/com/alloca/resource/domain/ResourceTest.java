package com.alloca.resource.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResourceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");

    @Test
    void cannotBeCreatedAsDeleted() {
        assertThatThrownBy(() -> new Resource(UUID.randomUUID(), ResourceStatus.DELETED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeStatusTogglesBetweenAvailableAndSuspended() {
        Resource resource = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);

        resource.changeStatus(ResourceStatus.SUSPENDED);
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.SUSPENDED);
        resource.changeStatus(ResourceStatus.AVAILABLE);
        assertThat(resource.getStatus()).isEqualTo(ResourceStatus.AVAILABLE);
    }

    @Test
    void changeStatusRejectsDeletedTarget() {
        Resource resource = new Resource(UUID.randomUUID(), ResourceStatus.SUSPENDED);

        assertThatThrownBy(() -> resource.changeStatus(ResourceStatus.DELETED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markDeletedRequiresSuspendedAndRecordsTimestamp() {
        Resource available = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);
        assertThatThrownBy(() -> available.markDeleted(NOW)).isInstanceOf(IllegalStateException.class);

        Resource suspended = new Resource(UUID.randomUUID(), ResourceStatus.SUSPENDED);
        suspended.markDeleted(NOW);
        assertThat(suspended.isDeleted()).isTrue();
        assertThat(suspended.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    void deletedResourceCannotChangeStatus() {
        Resource resource = new Resource(UUID.randomUUID(), ResourceStatus.SUSPENDED);
        resource.markDeleted(NOW);

        assertThatThrownBy(() -> resource.changeStatus(ResourceStatus.AVAILABLE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mapPositionCanBePlacedAndCleared() {
        Resource resource = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);
        assertThat(resource.hasMapPosition()).isFalse();

        resource.placeOnMap(1, 2, 3, 4);
        assertThat(resource.hasMapPosition()).isTrue();
        assertThat(resource.getMapWidth()).isEqualTo(3);

        resource.clearMapPosition();
        assertThat(resource.hasMapPosition()).isFalse();
    }
}
