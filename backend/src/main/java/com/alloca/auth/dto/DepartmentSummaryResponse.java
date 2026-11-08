package com.alloca.auth.dto;

import java.util.UUID;

public record DepartmentSummaryResponse (
    UUID id,
    String name
){
}
