package com.hrm.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class AdminAccessDTOs {
    private AdminAccessDTOs() {}

    public record CreateAccount(
            @NotNull @Positive Long employeeId,
            @NotNull @Positive Long roleId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._@-]*") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank String confirmPassword,
            @NotNull Boolean active) {}

    // Employee ownership is immutable; password changes use the existing password endpoints.
    public record UpdateAccount(
            @NotNull @Positive Long roleId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._@-]*") String username,
            @NotNull Boolean active) {}

    public record AccountView(Long accountId, Long employeeId, String employeeCode, String fullName,
            Long roleId, String roleCode, String roleName, String username, boolean active,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record CreateRole(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String roleCode,
            @NotBlank @Size(max = 100) String roleName,
            @NotNull @Size(max = 500) Set<@NotNull @Positive Long> permissionIds) {}

    public record UpdateRole(@NotBlank @Size(max = 100) String roleName) {}
    public record ReplacePermissions(@NotNull @Size(max = 500) Set<@NotNull @Positive Long> permissionIds) {}
    public record RoleView(Long roleId, String roleCode, String roleName, List<PermissionView> permissions) {}

    public record CreatePermission(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]*") String permissionCode,
            @NotBlank @Size(max = 200) String permissionName,
            @Size(max = 1000) String description) {}
    public record UpdatePermission(@NotBlank @Size(max = 200) String permissionName,
            @Size(max = 1000) String description) {}
    public record PermissionView(Long permissionId, String permissionCode, String permissionName, String description) {}
}
