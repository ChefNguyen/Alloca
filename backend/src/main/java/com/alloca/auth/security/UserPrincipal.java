package com.alloca.auth.security;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.alloca.auth.domain.Role;

import lombok.Getter;

@Getter
public class UserPrincipal implements UserDetails {
    private final UUID id;
    private final String username;
    private final Role role;
    private final UUID departmentId;
    private final Collection<? extends GrantedAuthority> authorities;
    
    public UserPrincipal(UUID id, String username, Role role, UUID departmentId){
        this.id = id;
        this.username = username;
        this.role = role;
        this.departmentId = departmentId;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isAdmin(){
        return this.role == Role.ADMIN;
    }

    public boolean isManager(){
        return this.role == Role.MANAGER;
    }

    public boolean isEmployee(){
        return this.role == Role.EMPLOYEE;
    }

    /**
     * BR-SCOPE-01: Kiểm tra xem User này có quyền thao tác trên tài nguyên của owner hay không.
     * - ADMIN: toàn quyền.
     * - MANAGER: cùng department với owner.
     * - EMPLOYEE: chính chủ (ownerId).
     */
    public boolean canAccessOwner(UUID ownerId, UUID ownerDepartmentId){
        if (isAdmin()){
            return true;
        }
        if (isManager()){
            return this.departmentId != null && this.departmentId.equals(ownerDepartmentId);
        }
        return this.id.equals(ownerId);
    }

    @Override
    public String getPassword(){
        return null;
    }

    @Override
    public String getUsername(){
        return username;
    }

    @Override
    public boolean isAccountNonExpired(){
        return true;
    }

    @Override
    public boolean isAccountNonLocked(){
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired(){
        return true;
    }

    @Override
    public boolean isEnabled(){
        return true;
    }
}
