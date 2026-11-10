SET NOCOUNT ON;
IF (SELECT COUNT(*) FROM dbo.employees)<>34 OR (SELECT COUNT(*) FROM dbo.accounts)<>34
    THROW 51901,N'Expected 34 linked staff and accounts.',1;
IF (SELECT COUNT(*) FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
    WHERE r.role_code='ADMIN' AND a.is_active=1)<>1
    THROW 51902,N'Expected exactly one active ADMIN.',1;
IF EXISTS (
    SELECT 1 FROM dbo.employee_assignments a WHERE NOT EXISTS (
        SELECT 1 FROM dbo.department_positions m WHERE m.position_id=a.position_id AND m.department_id=a.department_id
    )
) THROW 51903,N'A historical or current assignment has an invalid department/position pair.',1;
IF (SELECT COUNT(*) FROM dbo.payroll_periods)<>2 OR EXISTS (
    SELECT 1 FROM dbo.work_schedules s CROSS JOIN
        (SELECT MIN(period_start) first_period FROM dbo.payroll_periods) p
    WHERE s.work_date BETWEEN p.first_period AND EOMONTH(p.first_period)
      AND NOT EXISTS (SELECT 1 FROM dbo.attendance_records a
                      WHERE a.schedule_id=s.schedule_id AND a.verification_status='DA_XAC_NHAN')
) THROW 51909,N'Expected current and previous payroll periods with confirmed prior attendance.',1;
IF (SELECT COUNT(DISTINCT a.department_id) FROM dbo.employee_assignments a
    JOIN dbo.positions p ON p.position_id=a.position_id
    WHERE p.position_code='TRUONG_PHONG' AND a.effective_to IS NULL)<>5
    THROW 51904,N'Each department must have a current head.',1;
IF (SELECT COUNT(*) FROM dbo.hr_department_scopes)<>5
    THROW 51905,N'Expected five HR scopes.',1;
IF (SELECT COUNT(*) FROM dbo.personnel_request_approval_stages WHERE stage_status='PENDING')<>3
    OR (SELECT COUNT(*) FROM dbo.leave_request_details)<>2
    OR (SELECT COUNT(*) FROM dbo.resignation_request_details)<>3
    OR (SELECT COUNT(*) FROM dbo.overtime_requests)<>1
    THROW 51906,N'Workflow records are incomplete.',1;
IF (SELECT COUNT(*) FROM dbo.contract_salary_terms)<>34
    OR (SELECT COUNT(*) FROM dbo.leave_entitlements)<>68
    OR NOT EXISTS(SELECT 1 FROM dbo.attendance_records)
    THROW 51907,N'Contracts, leave balances or attendance are incomplete.',1;
IF (SELECT COUNT(*) FROM dbo.personnel_termination_decisions)<>2
    OR (SELECT COUNT(*) FROM dbo.g2_resignation_executions)<>1
    OR NOT EXISTS (SELECT 1 FROM dbo.employees e JOIN dbo.accounts a ON a.employee_id=e.employee_id
                   WHERE e.employee_code='NV034' AND e.employment_status='DA_NGHI' AND a.is_active=0)
    THROW 51910,N'Termination examples are incomplete.',1;
IF (SELECT COUNT(*) FROM dbo.leave_types)<>4
    OR NOT EXISTS(SELECT 1 FROM dbo.personnel_leave_accrual_policies)
    OR OBJECT_ID(N'dbo.employee_code_counters',N'U') IS NULL
    OR OBJECT_ID(N'dbo.contract_number_sequence',N'SO') IS NULL
    OR OBJECT_ID(N'dbo.leave_request_number_pn',N'SO') IS NULL
    OR OBJECT_ID(N'dbo.decision_number_kt',N'SO') IS NULL
    THROW 51908,N'Leave policies or code generators are incomplete.',1;
SELECT d.department_name, p.position_name
FROM dbo.department_positions m
JOIN dbo.departments d ON d.department_id=m.department_id
JOIN dbo.positions p ON p.position_id=m.position_id
ORDER BY d.department_code,p.position_code;
