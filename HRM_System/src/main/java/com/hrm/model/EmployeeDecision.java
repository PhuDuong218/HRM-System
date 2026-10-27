package com.hrm.model;

import java.math.BigDecimal;
import java.time.*;
import com.hrm.enums.*;
import jakarta.persistence.*;

@Entity
@Table(name = "employee_decisions", schema = "dbo")
public class EmployeeDecision extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long decisionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    @Column(name = "decision_number", nullable = false, unique = true, length = 50)
    private String decisionNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_type", nullable = false, length = 20)
    private DecisionType decisionType;
    @Column(name = "content", nullable = false, length = 2000)
    private String content;
    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;
    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;
    @Column(name = "approved_by")
    private Long approvedBy;
    @Column(name = "approved_at", columnDefinition = "datetime2(3)")
    private LocalDateTime approvedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private DecisionApprovalStatus approvalStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "payroll_effect", nullable = false, length = 10)
    private DecisionPayrollEffect payrollEffect;
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(name = "apply_date")
    private LocalDate applyDate;

    public EmployeeDecision() {
    }

    public Long getDecisionId() {
        return decisionId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee v) {
        employee = v;
    }

    public String getDecisionNumber() {
        return decisionNumber;
    }

    public void setDecisionNumber(String v) {
        decisionNumber = v;
    }

    public DecisionType getDecisionType() {
        return decisionType;
    }

    public void setDecisionType(DecisionType v) {
        decisionType = v;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String v) {
        content = v;
    }

    public LocalDate getIssuedDate() {
        return issuedDate;
    }

    public void setIssuedDate(LocalDate v) {
        issuedDate = v;
    }

    public Long getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(Long v) {
        issuedBy = v;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long v) {
        approvedBy = v;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime v) {
        approvedAt = v;
    }

    public DecisionApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(DecisionApprovalStatus v) {
        approvalStatus = v;
    }

    public DecisionPayrollEffect getPayrollEffect() {
        return payrollEffect;
    }

    public void setPayrollEffect(DecisionPayrollEffect v) {
        payrollEffect = v;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal v) {
        amount = v;
    }

    public LocalDate getApplyDate() {
        return applyDate;
    }

    public void setApplyDate(LocalDate v) {
        applyDate = v;
    }
}