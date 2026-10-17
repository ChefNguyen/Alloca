package com.alloca.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter 
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullname;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "credentials_version", nullable = false)
    private int credentialsVersion = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "created_at", nullable = false, updatable = false )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(
        UUID id,
        String username,
        String email,
        String passwordHash,
        String fullname,
        Role role,
        Department department
    ) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullname = fullname;
        this.role = role;
        this.department = department;
        this.isActive = true;
        this.credentialsVersion = 1;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void incrementCredentialsVersion() {
        this.credentialsVersion++;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.isActive = false;
        incrementCredentialsVersion();
    }

    public void activate() {
        this.isActive = true;
        this.updatedAt = Instant.now();
    }

    
}
