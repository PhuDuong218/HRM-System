package com.hrm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    public Long getPermissionId() {
        return permissionId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }
}
