package com.hrm.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payroll_details")
public class PayrollDetail {

    @Id
    private Long payrollDetailId;
}
