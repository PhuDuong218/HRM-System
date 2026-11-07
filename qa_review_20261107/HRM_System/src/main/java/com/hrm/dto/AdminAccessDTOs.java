package com.hrm.dto;

import com.hrm.enums.EmploymentStatus;
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

    public record ChangeAccountStatus(@NotNull Boolean expectedActive, @NotNull Boolean active) {}

    public record EditAccountIdentity(
            @NotBlank @Size(max = 100) String expectedUsername,
            @NotNull @Positive Long expectedRoleId,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._@-]*") String username,
            @NotNull @Positive Long roleId,
            Set<@NotNull @Positive Long> expectedDepartmentIds,
            Set<@NotNull @Positive Long> departmentIds) {}

    public record AccountView(Long accountId, Long employeeId, String employeeCode, String fullName,
            Long roleId, String roleCode, String roleName, String username, boolean active,
            LocalDateTime createdAt, LocalDateTime updatedAt, EmploymentStatus employmentStatus) {}

    public record AccountContact(String email, String phone) {}

    public record EligibleEmployee(Long employeeId, String employeeCode, String fullName) {}

    public record RoleView(Long roleId, String roleCode, String roleName, List<PermissionView> permissions) {}

    public record UpdatePermission(@NotBlank @Size(max = 200) String permissionName,
            @Size(max = 1000) String description) {}
    public record EditPermissionDetails(@NotBlank @Size(max = 200) String expectedName,
            @Size(max = 1000) String expectedDescription,
            @NotBlank @Size(max = 200) String permissionName,
            @Size(max = 1000) String description) {}
    public record PermissionView(Long permissionId, String permissionCode, String permissionName, String description) {}
}
