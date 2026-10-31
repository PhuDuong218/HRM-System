package com.hrm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Column(name = "permission_name", nullable = false, length = 200)
    private String permissionName;

    @Column(name = "description", length = 1000)
    private String description;

    public Long getPermissionId() {
        return permissionId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public String getPermissionName() { return permissionName; }
    public String getDescription() { return description; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
    public void setDescription(String description) { this.description = description; }
}
