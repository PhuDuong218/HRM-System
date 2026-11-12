-- HRM-Center demo only. Inserts a separate set of 67 synthetic employees.
-- Rerunning a completed seed returns without modifying records.
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
IF DB_NAME() <> 'hrm_center_full' THROW 52000, N'Chỉ chạy trên bản demo hrm_center_full.', 1;

BEGIN TRAN;
DECLARE @lock int;
EXEC @lock = sys.sp_getapplock @Resource='overview-demo-staff-v1', @LockMode='Exclusive', @LockOwner='Transaction';
IF @lock < 0 THROW 52001, N'Không lấy được khóa tạo dữ liệu mẫu.', 1;
DECLARE @existing int = (SELECT COUNT(*) FROM dbo.employees WHERE employee_code LIKE 'OVD26-%');
IF @existing = 67
BEGIN
    COMMIT;
    RETURN;
END;
IF @existing <> 0 THROW 52002, N'Đã có một phần dữ liệu OVD26. Cần kiểm tra trước khi chạy lại.', 1;

DECLARE @today date = CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time');
DECLARE @month date = DATEFROMPARTS(YEAR(@today),MONTH(@today),1);
DECLARE @first date = DATEADD(month,-5,@month);
DECLARE @admin bigint = (SELECT account_id FROM dbo.accounts WHERE username='demo.admin' AND is_active=1);
DECLARE @role bigint = (SELECT role_id FROM dbo.roles WHERE role_code='EMPLOYEE');
DECLARE @type bigint = (SELECT contract_type_id FROM dbo.contract_types WHERE type_code='XD24');
DECLARE @shift bigint = (SELECT shift_id FROM dbo.work_shifts WHERE shift_code='DEMO_DAY' AND start_time='08:00' AND end_time='17:00');
DECLARE @level bigint = (SELECT qualification_level_id FROM dbo.qualification_levels WHERE level_code='DH');
DECLARE @leave bigint = (SELECT leave_type_id FROM dbo.leave_types WHERE type_code='ANNUAL');
DECLARE @allowance bigint = (SELECT TOP(1) allowance_type_id FROM dbo.allowance_types ORDER BY allowance_type_id);
DECLARE @password varchar(255) = (SELECT password_hash FROM dbo.accounts WHERE username='demo.admin');
IF @admin IS NULL OR @role IS NULL OR @type IS NULL OR @shift IS NULL OR @level IS NULL OR @leave IS NULL OR @allowance IS NULL
    THROW 52003, N'Thiếu danh mục mẫu cần thiết.', 1;

DECLARE @positions TABLE (n int PRIMARY KEY, department_id bigint, position_id bigint);
INSERT @positions
SELECT ROW_NUMBER() OVER (ORDER BY department_id),department_id,MIN(a.position_id)
FROM dbo.employee_assignments a
JOIN dbo.positions p ON p.position_id=a.position_id
WHERE a.effective_to IS NULL AND p.position_code<>'TRUONG_PHONG'
GROUP BY department_id;
DECLARE @groups int = (SELECT COUNT(*) FROM @positions);
IF @groups=0 THROW 52004,N'Không có vị trí nhân viên hợp lệ.',1;

DECLARE @people TABLE (n int PRIMARY KEY, code varchar(50), hire date, exit_date date NULL, contract_end date);
;WITH numbers AS (
    SELECT 1 AS n UNION ALL SELECT n+1 FROM numbers WHERE n<67
)
INSERT @people
SELECT n, CONCAT('OVD26-',RIGHT(CONCAT('000',n),3)), dates.hire, dates.exit_date,
       COALESCE(dates.exit_date,DATEADD(day,-1,DATEADD(month,24,dates.hire)))
FROM numbers
CROSS APPLY (VALUES (
    CASE WHEN n<=12 THEN DATEADD(month,-24,DATEADD(day,n+2,@today))
         ELSE DATEADD(day, n%7, DATEADD(month,(n*7+n/5)%6,@first)) END,
    CASE WHEN n>=56 THEN DATEADD(day,-(n%4),EOMONTH(DATEADD(month,-1-(n%4),@month))) END
)) dates(hire,exit_date);
UPDATE @people
SET hire=DATEADD(day,1,DATEADD(month,-2,exit_date))
WHERE exit_date IS NOT NULL AND hire>=exit_date;
UPDATE @people SET hire=@today WHERE hire>@today;

INSERT dbo.employees(employee_code,full_name,birth_date,hire_date,email,phone,address,identity_number,gender)
SELECT code,CONCAT(CHOOSE(n%6+1,N'Nguyễn',N'Trần',N'Lê',N'Phạm',N'Võ',N'Đặng'),N' ',
    CHOOSE(n%5+1,N'Minh',N'Thanh',N'Hoàng',N'Thu',N'Ngọc'),N' ',
    CHOOSE(n%7+1,N'An',N'Bình',N'Phương',N'Linh',N'Duy',N'Trang',N'Tâm')),
    DATEFROMPARTS(1985+n%15,1+n%12,1+n%27),hire,
    CONCAT(LOWER(code),'@example.invalid'),CONCAT('0887000',RIGHT(CONCAT('000',n),3)),
    N'Hồ sơ mẫu HRM-Center', CONCAT('900770000',RIGHT(CONCAT('000',n),3)),
    CASE WHEN n%2=0 THEN 'FEMALE' ELSE 'MALE' END
FROM @people;

INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active)
SELECT e.employee_id,@role,LOWER(p.code),@password,0
FROM @people p JOIN dbo.employees e ON e.employee_code=p.code;

INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from,effective_to,created_by)
SELECT e.employee_id,a.department_id,a.position_id,p.hire,DATEADD(day,1,p.exit_date),@admin
FROM @people p JOIN dbo.employees e ON e.employee_code=p.code
JOIN @positions a ON a.n=1+(p.n-1)%@groups;

INSERT dbo.contracts(employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status,termination_reason_type,created_by)
SELECT e.employee_id,@type,CONCAT('HD-',p.code),p.hire,p.hire,p.contract_end,
    CASE WHEN p.exit_date IS NULL THEN 'DA_KY' ELSE 'DA_KET_THUC' END,
    CASE WHEN p.exit_date IS NOT NULL THEN 'THOA_THUAN' END,@admin
FROM @people p JOIN dbo.employees e ON e.employee_code=p.code;

INSERT dbo.contract_salary_terms(contract_id,effective_from,effective_to,base_salary,pay_basis,document_reference)
SELECT c.contract_id,c.start_date,c.end_date,9500000+(p.n%18)*450000,'THANG',N'Điều khoản mẫu OVD26'
FROM @people p JOIN dbo.contracts c ON c.contract_number=CONCAT('HD-',p.code);

INSERT dbo.employee_qualifications(employee_id,qualification_level_id,major,institution,graduation_year,is_primary,issued_date,certificate_number)
SELECT e.employee_id,@level,CHOOSE(p.n%4+1,N'Quản trị kinh doanh',N'Kế toán',N'Công nghệ thông tin',N'Ngôn ngữ Anh'),
       N'Cơ sở đào tạo mẫu',2012+p.n%10,1,DATEFROMPARTS(2012+p.n%10,6,1),CONCAT('DEG-',p.code)
FROM @people p JOIN dbo.employees e ON e.employee_code=p.code;

INSERT dbo.leave_entitlements(employee_id,leave_type_id,period_start,period_end,entitled_hours,created_by)
SELECT e.employee_id,@leave,DATEFROMPARTS(YEAR(@today),1,1),DATEFROMPARTS(YEAR(@today)+1,1,1),96,@admin
FROM @people p JOIN dbo.employees e ON e.employee_code=p.code;

INSERT dbo.employee_allowances(employee_id,allowance_type_id,effective_from,effective_to,calculation_basis,amount,amount_type,created_by)
SELECT e.employee_id,@allowance,p.hire,p.exit_date,'THEO_KY',250000+(p.n%5)*50000,'FIXED',@admin
FROM @people p JOIN dbo.employees e ON e.employee_code=p.code
WHERE p.exit_date IS NULL OR p.exit_date>p.hire;

DECLARE @day date=@first;
WHILE @day<=@today
BEGIN
    IF DATEDIFF(day,'19000101',@day)%7<5
    INSERT dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,break_detail_status,note,created_by)
    SELECT e.employee_id,@shift,@day,DATEADD(hour,8,CAST(@day AS datetime2)),DATEADD(hour,17,CAST(@day AS datetime2)),60,'VERIFIED',N'Lịch mẫu OVD26',@admin
    FROM @people p JOIN dbo.employees e ON e.employee_code=p.code
    WHERE @day BETWEEN p.hire AND p.contract_end
      AND NOT EXISTS (SELECT 1 FROM dbo.payroll_periods pp JOIN dbo.payroll_batches pb ON pb.period_id=pp.period_id
          WHERE pb.status='DA_CHOT' AND @day BETWEEN pp.period_start AND pp.period_end);
    SET @day=DATEADD(day,1,@day);
END;

INSERT dbo.work_schedule_breaks(schedule_id,break_start,break_end)
SELECT schedule_id,DATEADD(hour,12,CAST(work_date AS datetime2)),DATEADD(hour,13,CAST(work_date AS datetime2))
FROM dbo.work_schedules WHERE note=N'Lịch mẫu OVD26';

INSERT dbo.attendance_records(employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,attendance_result,verification_status,confirmed_by,confirmed_at,created_by)
SELECT s.employee_id,s.schedule_id,s.work_date,
    CASE WHEN pattern.v%23<>0 THEN DATEADD(minute,CASE WHEN pattern.v%9=0 THEN 22 ELSE 0 END,s.planned_start) END,
    CASE WHEN pattern.v%23<>0 THEN DATEADD(minute,CASE WHEN pattern.v%11=0 THEN -25 ELSE 0 END,s.planned_end) END,
    CASE WHEN pattern.v%23=0 THEN 0 ELSE 60 END,
    CASE WHEN pattern.v%23=0 THEN 'VANG' ELSE 'DI_LAM' END,
    'DA_XAC_NHAN',@admin,SYSUTCDATETIME(),@admin
FROM dbo.work_schedules s JOIN dbo.employees e ON e.employee_id=s.employee_id JOIN @people p ON p.code=e.employee_code
CROSS APPLY (VALUES (p.n+DATEDIFF(day,@first,s.work_date))) pattern(v)
WHERE s.note=N'Lịch mẫu OVD26' AND s.work_date<@today;

INSERT dbo.attendance_actual_breaks(attendance_id,break_start,break_end,source_start,source_end,source_break_minutes,created_by)
SELECT a.attendance_id,b.break_start,b.break_end,a.actual_start,a.actual_end,60,@admin
FROM dbo.attendance_records a JOIN dbo.work_schedules s ON s.schedule_id=a.schedule_id
JOIN dbo.work_schedule_breaks b ON b.schedule_id=s.schedule_id
WHERE s.note=N'Lịch mẫu OVD26' AND a.attendance_result='DI_LAM';

UPDATE e SET employment_status='DA_NGHI',termination_date=p.exit_date
FROM dbo.employees e JOIN @people p ON p.code=e.employee_code WHERE p.exit_date IS NOT NULL;

INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason)
VALUES(@admin,'DEMO_DATABASE_SEED','employees','OVD26',N'Tạo 67 hồ sơ mẫu và dữ liệu công để thử dashboard');
COMMIT;
