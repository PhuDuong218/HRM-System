package com.hrm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

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

    public record ScopeView(long scopeId, long hrAccountId, long departmentId,
            LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
            LocalDateTime revokedAt, Long revokedBy,
            LocalDateTime createdAt, long createdBy,
            LocalDateTime updatedAt, Long updatedBy) {
    }
}
