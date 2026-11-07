package com.hrm.model;

import java.time.LocalDate;
// import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
// import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "employee_assignments")
public class EmployeeAssignment extends BaseEntity { // Kế thừa toàn bộ cột từ BaseEntity

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long assignmentId;

    // 1. CHỐNG N+1 QUERY: Dùng FetchType.LAZY để không tự động kéo dữ liệu Employee lên khi chưa cần thiết.
    // 2. CHỐNG VÒNG LẶP (Infinite Recursion): Dùng @JsonBackReference để khi xuất JSON, nó không gọi ngược lại Employee nữa.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @JsonBackReference
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    // Thay thế kiểu Long bằng thực thể Position thực sự
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @NotNull(message = "Ngày hiệu lực không được để trống")
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    // --- CONSTRUCTORS ---
    public EmployeeAssignment() {
    }

    // public EmployeeAssignment(Employee employee, Department department, Position position, LocalDate effectiveFrom, LocalDate effectiveTo, LocalDateTime createdAt, LocalDateTime updatedAt) {
    //     this.employee = employee;
    //     this.department = department;
    //     this.position = position;
    //     this.effectiveFrom = effectiveFrom;
    //     this.effectiveTo = effectiveTo;
    //     this.createdAt = createdAt;
    //     this.updatedAt = updatedAt;
    // }
    public Long getAssignmentId() {
        return assignmentId;
    }

    // TỰ động viết get set
    public Employee getEmployee() {
        return employee;
    }

    public Department getDepartment() {
        return department;
    }

    // public Long getPositionId() {
    //     return position != null ? position.getPositionId() : null;
    // }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Position getPosition() {
        return position;
    }

}
