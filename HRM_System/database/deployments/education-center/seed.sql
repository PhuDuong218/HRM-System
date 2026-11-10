CREATE TABLE dbo.department_positions (
    department_id bigint NOT NULL REFERENCES dbo.departments(department_id),
    position_id bigint NOT NULL REFERENCES dbo.positions(position_id),
    CONSTRAINT PK_department_positions PRIMARY KEY (position_id, department_id)
);
GO
ALTER TABLE dbo.employee_assignments WITH CHECK
    ADD CONSTRAINT FK_assignment_department_position FOREIGN KEY (position_id, department_id)
    REFERENCES dbo.department_positions(position_id, department_id);
GO
/* ROLES_AND_PERMISSIONS */
DECLARE @today date = CONVERT(date, SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time');
DECLARE @year int = YEAR(@today), @monthStart date = DATEFROMPARTS(YEAR(@today), MONTH(@today), 1);
DECLARE @previousMonth date = DATEADD(month,-1,@monthStart);
DECLARE @futureExit date = DATEADD(day,10,@today), @pastExit date = EOMONTH(@previousMonth);
DECLARE @hire date = DATEFROMPARTS(@year - 2, 1, 1);

DECLARE @organization TABLE (
    department_code varchar(50) PRIMARY KEY, department_name nvarchar(200),
    description nvarchar(1000), major nvarchar(200)
);
INSERT @organization VALUES
('HCNS', N'Hành chính nhân sự', N'Quản lý hồ sơ, tuyển dụng và vận hành hành chính.', N'Quản trị nhân lực'),
('DT', N'Đào tạo', N'Giảng dạy, quản lý lớp học và chất lượng đào tạo.', N'Sư phạm Anh'),
('TV', N'Tư vấn tuyển sinh', N'Tư vấn khóa học, tiếp nhận và hỗ trợ học viên.', N'Quản trị dịch vụ'),
('KT', N'Kế toán', N'Quản lý học phí, công nợ và đối soát thu chi.', N'Kế toán'),
('CNTT', N'Công nghệ thông tin', N'Quản trị hệ thống và hỗ trợ kỹ thuật.', N'Công nghệ thông tin');
INSERT dbo.departments(department_code, department_name, description)
SELECT department_code, department_name, description FROM @organization;

DECLARE @jobs TABLE (
    code varchar(50) PRIMARY KEY, name nvarchar(200), dept varchar(50), salary decimal(19,4)
);
INSERT @jobs VALUES
('TRUONG_PHONG', N'Trưởng phòng', NULL, 24000000),
('ADMIN_SYS', N'Quản trị hệ thống', 'CNTT', 23000000),
('IT', N'Kỹ thuật viên hệ thống', 'CNTT', 17000000),
('IT_HOTRO', N'Chuyên viên hỗ trợ kỹ thuật', 'CNTT', 14500000),
('HR', N'Chuyên viên nhân sự', 'HCNS', 15500000),
('HANH_CHINH', N'Nhân viên hành chính', 'HCNS', 12500000),
('GV', N'Giáo viên', 'DT', 18000000),
('GIAO_VU', N'Chuyên viên giáo vụ', 'DT', 14000000),
('TV', N'Tư vấn viên tuyển sinh', 'TV', 13000000),
('CS_HOC_VIEN', N'Chuyên viên chăm sóc học viên', 'TV', 13000000),
('KT', N'Kế toán viên', 'KT', 14500000),
('KT_TONG_HOP', N'Kế toán tổng hợp', 'KT', 18000000),
('KT_HOC_PHI', N'Kế toán học phí', 'KT', 14000000);
INSERT dbo.positions(position_code, position_name) SELECT code, name FROM @jobs;
INSERT dbo.department_positions(department_id, position_id)
SELECT d.department_id, p.position_id FROM @jobs j
JOIN dbo.positions p ON p.position_code=j.code JOIN dbo.departments d ON d.department_code=j.dept;
INSERT dbo.department_positions(department_id, position_id)
SELECT d.department_id, p.position_id FROM dbo.departments d
CROSS JOIN dbo.positions p WHERE p.position_code='TRUONG_PHONG';

DECLARE @staff TABLE (
    n int, code varchar(50) PRIMARY KEY, name nvarchar(200), dept varchar(50), pos varchar(50),
    username nvarchar(100), role varchar(20), gender varchar(20), hash varchar(255), token_version int
);
INSERT @staff VALUES
/* STAFF_ROWS */;
INSERT dbo.employees(employee_code, full_name, birth_date, hire_date, email, phone, address, identity_number, gender)
SELECT code, name, DATEFROMPARTS(@year - 27 - n % 12, 1 + n % 12, 1 + n % 27), @hire,
       CONCAT(username, '@hrm.example.invalid'), CONCAT('090', RIGHT(CONCAT('0000000', n), 7)),
       CONCAT(N'TP. Hồ Chí Minh — địa chỉ giả lập ', n), RIGHT(CONCAT('000000000000', n), 12), gender
FROM @staff;
INSERT dbo.accounts(employee_id, role_id, username, password_hash, token_version)
SELECT e.employee_id, r.role_id, s.username, s.hash, s.token_version
FROM @staff s JOIN dbo.employees e ON e.employee_code=s.code JOIN dbo.roles r ON r.role_code=s.role;
DECLARE @admin bigint=(SELECT account_id FROM dbo.accounts WHERE username='quantri');
DECLARE @hr bigint=(SELECT account_id FROM dbo.accounts WHERE username='nhansu.thanhha');
DECLARE @move date=DATEFROMPARTS(@year-1, 1, 1);

-- NV009 began in student services and moved into teaching. Both dated assignments fit their departments.
INSERT dbo.employee_assignments(employee_id, department_id, position_id, effective_from, effective_to, created_by)
SELECT e.employee_id, d.department_id, p.position_id, @hire, @move, @admin
FROM dbo.employees e CROSS JOIN dbo.departments d CROSS JOIN dbo.positions p
WHERE e.employee_code='NV009' AND d.department_code='TV' AND p.position_code='CS_HOC_VIEN';
INSERT dbo.employee_assignments(employee_id, department_id, position_id, effective_from, effective_to, created_by)
SELECT e.employee_id, d.department_id, p.position_id, CASE WHEN s.code='NV009' THEN @move ELSE @hire END,
       CASE WHEN s.code='NV034' THEN DATEADD(day,1,@pastExit) END, @admin
FROM @staff s JOIN dbo.employees e ON e.employee_code=s.code
JOIN dbo.departments d ON d.department_code=s.dept JOIN dbo.positions p ON p.position_code=s.pos;

INSERT dbo.hr_department_scopes(hr_account_id, department_id, effective_from, created_by)
SELECT a.account_id, d.department_id, @hire, @admin
FROM (VALUES ('nhansu.thanhha','DT'),('nhansu.thanhha','TV'),('nhansu.minhchau','HCNS'),
             ('nhansu.minhchau','KT'),('nhansu.minhchau','CNTT')) v(username, dept)
JOIN dbo.accounts a ON a.username=v.username JOIN dbo.departments d ON d.department_code=v.dept;

INSERT dbo.qualification_levels(level_code, level_name, rank_order) VALUES
('CC',N'Chứng chỉ',1),('CD',N'Cao đẳng',2),('DH',N'Đại học',3),('THS',N'Thạc sĩ',4);
INSERT dbo.employee_qualifications(employee_id, qualification_level_id, major, institution,
                                   graduation_year, is_primary, issued_date, certificate_number)
SELECT e.employee_id, q.qualification_level_id, o.major, N'Cơ sở đào tạo giả lập',
       YEAR(e.birth_date) + 22, 1, DATEFROMPARTS(YEAR(e.birth_date)+22,6,1), CONCAT('BANG-',e.employee_code)
FROM @staff s JOIN dbo.employees e ON e.employee_code=s.code
JOIN @organization o ON o.department_code=s.dept JOIN dbo.qualification_levels q ON q.level_code='DH';
INSERT dbo.employee_qualifications(employee_id, qualification_level_id, major, institution,
                                   is_primary, issued_date, expiry_date, certificate_number)
SELECT e.employee_id, q.qualification_level_id, v.major, N'Trung tâm bồi dưỡng giả lập',
       0, DATEADD(year,-1,@today), DATEADD(day,v.days_remaining,@today), CONCAT('CC-',e.employee_code)
FROM (VALUES ('NV008',N'Ứng dụng phần mềm kế toán',120),('NV009',N'Nghiệp vụ sư phạm',20),
             ('NV012',N'Quản trị mạng',-5)) v(code,major,days_remaining)
JOIN dbo.employees e ON e.employee_code=v.code JOIN dbo.qualification_levels q ON q.level_code='CC';

INSERT dbo.contract_types(type_code, type_name, term_kind, max_months) VALUES
('XD24',N'Hợp đồng xác định thời hạn 24 tháng','XAC_DINH',24),
('KXD',N'Hợp đồng không xác định thời hạn','KHONG_XAC_DINH',NULL);
INSERT dbo.contracts(employee_id, contract_type_id, contract_number, signed_date, start_date, end_date, status, termination_reason_type, created_by)
SELECT e.employee_id, t.contract_type_id, CONCAT('HD-',e.employee_code),
       dates.starts, dates.starts,
       CASE WHEN t.type_code='KXD' THEN NULL WHEN s.code='NV031' THEN DATEADD(day,20,@today)
            WHEN s.code='NV033' THEN @futureExit WHEN s.code IN ('NV032','NV034') THEN @pastExit
            ELSE DATEADD(day,-1,DATEADD(month,24,dates.starts)) END,
       CASE WHEN s.code IN ('NV032','NV034') THEN 'DA_KET_THUC' ELSE 'DA_KY' END,
       CASE WHEN s.code='NV032' THEN 'HET_HAN' WHEN s.code IN ('NV033','NV034') THEN 'THOA_THUAN' END, @admin
FROM @staff s JOIN dbo.employees e ON e.employee_code=s.code
JOIN dbo.contract_types t ON t.type_code=CASE WHEN s.pos='TRUONG_PHONG' OR s.role='ADMIN' THEN 'KXD' ELSE 'XD24' END
CROSS APPLY (VALUES (CASE WHEN s.code='NV031' THEN DATEADD(month,-12,DATEADD(day,20,@today))
                          WHEN t.type_code='KXD' THEN @hire ELSE DATEFROMPARTS(@year-1,1,1) END)) dates(starts);
INSERT dbo.contract_salary_terms(contract_id, effective_from, effective_to, base_salary, pay_basis, document_reference)
SELECT c.contract_id,c.start_date,c.end_date,j.salary,'THANG',CONCAT(N'Điều khoản lương ',c.contract_number)
FROM dbo.contracts c JOIN dbo.employees e ON e.employee_id=c.employee_id
JOIN @staff s ON s.code=e.employee_code JOIN @jobs j ON j.code=s.pos;

INSERT dbo.allowance_types(type_code,type_name,is_taxable,counts_for_social_insurance)
VALUES ('DIEN_THOAI',N'Hỗ trợ điện thoại',1,0), ('TRACH_NHIEM',N'Phụ cấp trách nhiệm',1,1);
INSERT dbo.employee_allowances(employee_id,allowance_type_id,effective_from,effective_to,calculation_basis,amount,amount_type,created_by)
SELECT e.employee_id,a.allowance_type_id,c.start_date,c.end_date,'THEO_KY',
       CASE a.type_code WHEN 'TRACH_NHIEM' THEN 1500000 ELSE 300000 END,'FIXED',@admin
FROM @staff s JOIN dbo.employees e ON e.employee_code=s.code JOIN dbo.contracts c ON c.employee_id=e.employee_id
JOIN dbo.allowance_types a ON (a.type_code='TRACH_NHIEM' AND s.pos='TRUONG_PHONG')
                            OR (a.type_code='DIEN_THOAI' AND s.dept='TV');

INSERT dbo.leave_types(type_code,type_name,is_paid) VALUES
('ANNUAL',N'Phép năm',1),('SICK',N'Nghỉ ốm',0),('THAI_SAN',N'Nghỉ thai sản',0),
('VIEC_RIENG',N'Nghỉ việc riêng không lương',0);
INSERT dbo.g2_leave_policies(leave_type_id,consumes_entitlement,updated_by)
SELECT leave_type_id,CASE WHEN type_code='ANNUAL' THEN 1 ELSE 0 END,@admin FROM dbo.leave_types;
INSERT dbo.personnel_leave_rules(leave_type_id,retroactive_days,minimum_notice_hours,minimum_service_months,evidence_required,updated_by)
SELECT leave_type_id,CASE WHEN type_code IN ('SICK','THAI_SAN') THEN 7 ELSE 0 END,
       0,0,CASE WHEN type_code IN ('SICK','THAI_SAN') THEN 1 ELSE 0 END,@admin FROM dbo.leave_types;
INSERT dbo.leave_entitlements(employee_id,leave_type_id,period_start,period_end,entitled_hours,created_by)
SELECT e.employee_id,t.leave_type_id,DATEFROMPARTS(y.calendar_year,1,1),
       DATEFROMPARTS(y.calendar_year+1,1,1),96,@admin
FROM dbo.employees e CROSS JOIN dbo.leave_types t CROSS APPLY (VALUES(@year),(@year+1)) y(calendar_year)
WHERE t.type_code='ANNUAL';

INSERT dbo.work_shifts(shift_code,shift_name,start_time,end_time,break_minutes) VALUES
('HC_NGAY',N'Hành chính 08:00–17:00','08:00','17:00',60),
('DT_CHIEU',N'Đào tạo 13:00–21:00','13:00','21:00',30);
INSERT dbo.work_shift_breaks(shift_id,break_start_offset_minutes,break_end_offset_minutes)
SELECT shift_id,240,240+break_minutes FROM dbo.work_shifts;

DECLARE @date date=@previousMonth;
WHILE @date<=DATEADD(day,14,@today)
BEGIN
    IF DATEDIFF(day,'20000103',@date)%7 BETWEEN 0 AND 4
        INSERT dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,break_detail_status,note,created_by)
        SELECT e.employee_id,sh.shift_id,@date,DATEADD(hour,CASE WHEN sh.shift_code='HC_NGAY' THEN 8 ELSE 13 END,CAST(@date AS datetime2)),
               DATEADD(hour,CASE WHEN sh.shift_code='HC_NGAY' THEN 17 ELSE 21 END,CAST(@date AS datetime2)),
               sh.break_minutes,'VERIFIED',N'Lịch làm việc theo bộ phận',@admin
        FROM @staff s JOIN dbo.employees e ON e.employee_code=s.code
        JOIN dbo.work_shifts sh ON sh.shift_code=CASE WHEN s.pos='GV' THEN 'DT_CHIEU' ELSE 'HC_NGAY' END
        WHERE (s.code<>'NV033' OR @date<=@futureExit) AND (s.code<>'NV034' OR @date<=@pastExit);
    SET @date=DATEADD(day,1,@date);
END;
INSERT dbo.work_schedule_breaks(schedule_id,break_start,break_end)
SELECT schedule_id,DATEADD(hour,4,planned_start),DATEADD(minute,240+planned_break_minutes,planned_start)
FROM dbo.work_schedules;
INSERT dbo.attendance_records(employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,
                              attendance_result,verification_status,confirmed_by,confirmed_at,created_by)
SELECT s.employee_id,s.schedule_id,s.work_date,
       DATEADD(minute,CASE WHEN e.employee_code='NV008' AND s.work_date=last_day.work_date THEN 22 ELSE 0 END,s.planned_start),
       DATEADD(minute,CASE WHEN e.employee_code='NV016' AND s.work_date=last_day.work_date THEN -35 ELSE 0 END,s.planned_end),
       s.planned_break_minutes,'DI_LAM','DA_XAC_NHAN',@admin,SYSUTCDATETIME(),@admin
FROM dbo.work_schedules s JOIN dbo.employees e ON e.employee_id=s.employee_id
CROSS JOIN (SELECT MAX(work_date) work_date FROM dbo.work_schedules WHERE work_date<@today) last_day
WHERE s.work_date<@today;
INSERT dbo.attendance_actual_breaks(attendance_id,break_start,break_end,source_start,source_end,source_break_minutes,created_by)
SELECT a.attendance_id,b.break_start,b.break_end,a.actual_start,a.actual_end,a.actual_break_minutes,@admin
FROM dbo.attendance_records a JOIN dbo.work_schedule_breaks b ON b.schedule_id=a.schedule_id;

INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,created_by)
SELECT e.employee_id,v.code,v.kind,v.reason,a.account_id
FROM (VALUES ('NV009','NP-NV009','NGHI_PHEP',N'Xin nghỉ bốn giờ để giải quyết việc gia đình'),
             ('NV010','NP-NV010','NGHI_PHEP',N'Xin nghỉ bốn giờ vì có lịch hẹn cá nhân'),
             ('NV011','NVIEC-NV011','NGHI_VIEC',N'Xin thôi việc theo kế hoạch cá nhân')) v(emp,code,kind,reason)
JOIN dbo.employees e ON e.employee_code=v.emp JOIN dbo.accounts a ON a.employee_id=e.employee_id;
INSERT dbo.leave_request_details(request_id,leave_type_id,leave_start,leave_end,created_by)
SELECT r.request_id,t.leave_type_id,s.planned_start,DATEADD(hour,4,s.planned_start),r.created_by
FROM dbo.employee_requests r CROSS JOIN dbo.leave_types t
CROSS APPLY (SELECT TOP (1) planned_start FROM dbo.work_schedules
             WHERE employee_id=r.employee_id AND work_date>=DATEADD(day,3,@today) ORDER BY work_date) s
WHERE r.request_type='NGHI_PHEP' AND t.type_code='ANNUAL';
INSERT dbo.resignation_request_details(request_id,requested_last_working_date,created_by)
SELECT request_id,DATEADD(day,45,@today),created_by FROM dbo.employee_requests WHERE request_type='NGHI_VIEC';
INSERT dbo.personnel_request_approval_stages(request_id,department_id,stage_status)
SELECT r.request_id,a.department_id,'PENDING'
FROM dbo.employee_requests r JOIN dbo.employee_assignments a ON a.employee_id=r.employee_id AND a.effective_to IS NULL;

-- Two direct termination decisions: one scheduled, one already executed.
INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,submitted_at,created_by)
SELECT employee_id, CASE employee_code WHEN 'NV033' THEN 'NVIEC-NV033' ELSE 'NVIEC-NV034' END,
       'NGHI_VIEC', N'Hai bên đã thống nhất ngày làm việc cuối',
       CASE WHEN employee_code='NV034' THEN CAST(DATEADD(day,-7,@pastExit) AS datetime2) ELSE SYSUTCDATETIME() END, @admin
FROM dbo.employees WHERE employee_code IN ('NV033','NV034');
INSERT dbo.resignation_request_details(request_id,requested_last_working_date,approved_last_working_date,created_by)
SELECT request_id, v.last_day, v.last_day, @admin
FROM dbo.employee_requests r CROSS APPLY (VALUES(CASE r.request_code WHEN 'NVIEC-NV033' THEN @futureExit ELSE @pastExit END)) v(last_day)
WHERE r.request_code IN ('NVIEC-NV033','NVIEC-NV034');
UPDATE dbo.employee_requests SET status='DA_DUYET', processed_by=@admin, processed_at=submitted_at
WHERE request_code IN ('NVIEC-NV033','NVIEC-NV034');
INSERT dbo.personnel_termination_decisions(request_id,decision_type,decision_number,created_by)
SELECT request_id,'MUTUAL_AGREEMENT',CONCAT(N'QĐ-',request_code),@admin
FROM dbo.employee_requests WHERE request_code IN ('NVIEC-NV033','NVIEC-NV034');
INSERT dbo.g2_resignation_executions(request_id,last_working_date,executed_at)
SELECT request_id,@pastExit,CAST(DATEADD(day,1,@pastExit) AS datetime2)
FROM dbo.employee_requests WHERE request_code='NVIEC-NV034';
UPDATE dbo.employees SET employment_status='DA_NGHI',termination_date=@pastExit WHERE employee_code='NV034';
UPDATE a SET is_active=0 FROM dbo.accounts a JOIN dbo.employees e ON e.employee_id=a.employee_id WHERE e.employee_code='NV034';

INSERT dbo.employee_decisions(employee_id,decision_number,decision_type,content,issued_date,issued_by,approval_status,payroll_effect,created_by)
SELECT employee_id,'KT-NV009','KHEN_THUONG',N'Ghi nhận đóng góp xây dựng chương trình học',@today,@hr,'CHO_DUYET','NONE',@hr
FROM dbo.employees WHERE employee_code='NV009';
INSERT dbo.overtime_requests(employee_id,request_code,requested_start,requested_end,reason,created_by,requested_break_status)
SELECT e.employee_id,'OT-NV012',s.planned_end,DATEADD(hour,2,s.planned_end),N'Bảo trì thiết bị sau ca làm việc',a.account_id,'VERIFIED'
FROM dbo.employees e JOIN dbo.accounts a ON a.employee_id=e.employee_id
CROSS APPLY (SELECT TOP(1) planned_end FROM dbo.work_schedules
             WHERE employee_id=e.employee_id AND work_date>=DATEADD(day,3,@today) ORDER BY work_date) s
WHERE e.employee_code='NV012';

INSERT dbo.salary_components(component_code,component_name,direction) VALUES
('LUONG_CONG',N'Lương theo công','ADD'),('TANG_CA',N'Tiền tăng ca','ADD'),
('PHU_CAP',N'Phụ cấp','ADD'),('THUONG',N'Tiền thưởng','ADD'),
('KHAU_TRU',N'Khấu trừ','DEDUCT'),('BAO_HIEM',N'Bảo hiểm','DEDUCT'),('THUE_TNCN',N'Thuế thu nhập','DEDUCT');
INSERT dbo.payroll_periods(period_code,period_start,period_end)
VALUES(CONCAT('LUONG-',CONVERT(char(7),@previousMonth,126)),@previousMonth,EOMONTH(@previousMonth)),
      (CONCAT('LUONG-',CONVERT(char(7),@monthStart,126)),@monthStart,EOMONTH(@monthStart));

INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,after_data_json)
SELECT @admin,'EMPLOYEE_CREATE','employees',CONVERT(varchar(100),e.employee_id),N'Khởi tạo hồ sơ giả lập',
       (SELECT e.employee_code employeeCode,e.full_name fullName FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)
FROM dbo.employees e;
