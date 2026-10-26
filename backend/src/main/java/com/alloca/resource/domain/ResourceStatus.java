package com.alloca.resource.domain;

/** Resource lifecycle (BR-RESOURCE-01): AVAILABLE <-> SUSPENDED -> DELETED (soft delete). */
public enum ResourceStatus {
    AVAILABLE,
    SUSPENDED,
    DELETED
}
