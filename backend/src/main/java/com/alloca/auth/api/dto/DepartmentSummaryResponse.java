package com.alloca.auth.api.dto;

import java.util.UUID;

public record DepartmentSummaryResponse (
    UUID id,
    String name
){
}
