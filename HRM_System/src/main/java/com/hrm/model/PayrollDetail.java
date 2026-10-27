package com.hrm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "payroll_details", uniqueConstraints = {
    @UniqueConstraint(name = "UQ_payroll_details_batch_employee", columnNames = {"batch_id", "employee_id"})
})
public class PayrollDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payroll_detail_id")
    private Long payrollDetailId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    public PayrollDetail() {}

    public PayrollDetail(Long batchId, Long employeeId) {
        this.batchId = batchId;
        this.employeeId = employeeId;
    }

    public Long getPayrollDetailId() { return payrollDetailId; }
    public void setPayrollDetailId(Long payrollDetailId) { this.payrollDetailId = payrollDetailId; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
}