package com.hrm.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "payroll_lines")
public class PayrollLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payroll_line_id")
    private Long payrollLineId;

    @Column(name = "payroll_detail_id", nullable = false)
    private Long payrollDetailId;

    @Column(name = "component_id", nullable = false)
    private Long componentId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "component_code_snapshot", nullable = false, length = 50)
    private String componentCodeSnapshot;

    @Column(name = "component_name_snapshot", nullable = false, length = 200)
    private String componentNameSnapshot;

    @Column(name = "direction_snapshot", nullable = false, length = 10)
    private String directionSnapshot; // ADD hoặc DEDUCT

    @Column(name = "quantity", precision = 9, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_rate", precision = 19, scale = 4)
    private BigDecimal unitRate;

    @Column(name = "multiplier", precision = 9, scale = 4)
    private BigDecimal multiplier;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "explanation", length = 2000)
    private String explanation;

    public PayrollLine() {}

    // Getters & Setters
    public Long getPayrollLineId() { return payrollLineId; }
    public void setPayrollLineId(Long payrollLineId) { this.payrollLineId = payrollLineId; }

    public Long getPayrollDetailId() { return payrollDetailId; }
    public void setPayrollDetailId(Long payrollDetailId) { this.payrollDetailId = payrollDetailId; }

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long componentId) { this.componentId = componentId; }

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }

    public String getComponentCodeSnapshot() { return componentCodeSnapshot; }
    public void setComponentCodeSnapshot(String componentCodeSnapshot) { this.componentCodeSnapshot = componentCodeSnapshot; }

    public String getComponentNameSnapshot() { return componentNameSnapshot; }
    public void setComponentNameSnapshot(String componentNameSnapshot) { this.componentNameSnapshot = componentNameSnapshot; }

    public String getDirectionSnapshot() { return directionSnapshot; }
    public void setDirectionSnapshot(String directionSnapshot) { this.directionSnapshot = directionSnapshot; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getUnitRate() { return unitRate; }
    public void setUnitRate(BigDecimal unitRate) { this.unitRate = unitRate; }

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}