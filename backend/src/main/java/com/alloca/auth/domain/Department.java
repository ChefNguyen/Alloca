package com.alloca.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "departments")

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {
    @Id
    @Column(name = "id", nullable = false, updatable = false )
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Department(UUID id, String name){
        this.id = id;
        this.name = name;
        this.createdAt = Instant.now();
    }
}
