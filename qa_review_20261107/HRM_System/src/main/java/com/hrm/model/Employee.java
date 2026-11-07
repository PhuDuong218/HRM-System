package com.hrm.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.hrm.enums.EmploymentStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "employees")
public class Employee extends BaseEntity { // Kế thừa toàn bộ cột từ BaseEntity

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id") //
    private Long employeeId;

    @Column(name = "employee_code", length = 255) //
    private String employeeCode;

    @NotBlank(message = "Họ tên không được để trống")
    @Column(name = "full_name", nullable = false, length = 200) //[cite: 3]
    private String fullName;

    @Column(name = "birth_date") //[cite: 3]
    private LocalDate birthDate;

    @Column(name = "email", length = 255) //[cite: 3]
    private String email;

    @Column(name = "phone", length = 30) //[cite: 3]
    private String phone;

    @Column(name = "address", length = 500) //[cite: 3]
    private String address;

    @Column(name = "identity_number", length = 50) //[cite: 3]
    private String identityNumber;

    @NotNull(message = "Ngày vào làm không được để trống")
    @Column(name = "hire_date", nullable = false) //[cite: 3]
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", length = 255)
    private EmploymentStatus employmentStatus;

    @Column(name = "termination_date") //[cite: 3]
    private LocalDate terminationDate;

    // --- CONSTRUCTORS ---
    public Employee() {
    }

    // --- GETTERS & SETTERS ---
    // (Sử dụng tính năng Generate Getters and Setters của VS Code cho tất cả các biến)
    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public LocalDate getTerminationDate() {
        return terminationDate;
    }

    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = terminationDate;
    }

    public List<EmployeeAssignment> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<EmployeeAssignment> assignments) {
        this.assignments = assignments;
    }

    @OneToMany(mappedBy = "employee", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<EmployeeAssignment> assignments = new ArrayList<>();

}
