package com.hrm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;

/** All input times are UTC; effective interval is [from, to). */
public final class HrScopeDTOs {
    private HrScopeDTOs() {
    }

    public record GrantRequest(
            @NotNull @Positive Long hrAccountId,
            @NotNull @Positive Long departmentId,
            @NotNull LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record UpdateRequest(
            @NotNull LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record RevokeRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record ReplaceDepartmentsRequest(
            @NotNull Set<@NotNull @Positive Long> expectedDepartmentIds,
            @NotNull @Size(min = 1) Set<@NotNull @Positive Long> departmentIds,
            @NotBlank @Size(max = 1000) String reason) {}

    public record ScopeView(long scopeId, long hrAccountId, long departmentId,
            LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
            LocalDateTime revokedAt, Long revokedBy,
            LocalDateTime createdAt, long createdBy,
            LocalDateTime updatedAt, Long updatedBy) {
    }

    public record ActiveDepartment(long departmentId, String departmentName) {}
    public record DepartmentOption(long departmentId, String departmentName, boolean active, boolean assigned) {}
}
