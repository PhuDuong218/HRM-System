USE [hrm_center_full];
GO
-- DU LIEU GIA LAP cho do an, khong phai bang cong/luong thuc te.
-- Khong ghi de du lieu cu, khong tinh/chot/cong bo luong. Co the chay lai an toan.
SET NOCOUNT ON;SET XACT_ABORT ON;SET QUOTED_IDENTIFIER ON;SET ANSI_NULLS ON;
SET ANSI_WARNINGS ON;SET ANSI_PADDING ON;SET CONCAT_NULL_YIELDS_NULL ON;SET ARITHABORT ON;SET NUMERIC_ROUNDABORT OFF;
DECLARE @Commit bit=1;
DECLARE @tag nvarchar(80)=N'SEED_PAYROLL_2026_08_10_V1',@now datetime2=SYSUTCDATETIME(),@from date='20260801',@to date='20261031';
IF EXISTS(SELECT 1 FROM dbo.audit_logs WHERE action='SEED_PAYROLL_INPUTS' AND target_record_key=@tag)
BEGIN PRINT N'Bo du lieu nay da duoc nap. Khong chen trung.';RETURN;END;
BEGIN TRY
 BEGIN TRAN;
 DECLARE @lock int;EXEC @lock=sys.sp_getapplock @Resource='HRM_G2_WORKFLOW',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=15000;
 IF @lock<0 THROW 51500,N'Khong lay duoc khoa nghiep vu.',1;
 IF EXISTS(SELECT 1 FROM dbo.payroll_periods p JOIN dbo.payroll_batches b ON b.period_id=p.period_id WHERE p.period_start<=@to AND p.period_end>=@from AND (b.locked_at IS NOT NULL OR b.published_at IS NOT NULL OR b.status<>'NHAP'))THROW 51501,N'Ky da khoa/cong bo; khong them du lieu.',1;
 DECLARE @admin bigint=(SELECT account_id FROM dbo.accounts WHERE username='demo.admin' AND is_active=1),
 @hr bigint=(SELECT account_id FROM dbo.accounts WHERE username='demo.hr' AND is_active=1),
 @shift bigint=(SELECT shift_id FROM dbo.work_shifts WHERE shift_code='DEMO_DAY'),
 @ct bigint=(SELECT contract_type_id FROM dbo.contract_types WHERE type_code='XD24' AND is_active=1),
 @phone bigint=(SELECT allowance_type_id FROM dbo.allowance_types WHERE type_code='DEMO_PHONE' AND is_active=1);
 IF @admin IS NULL OR @hr IS NULL OR @shift IS NULL OR @ct IS NULL OR @phone IS NULL THROW 51502,N'Thieu danh muc/tai khoan mau.',1;
 DECLARE @staff TABLE(employee_id bigint PRIMARY KEY,account_id bigint,hire_date date,termination_date date,employee_code varchar(50));
 INSERT @staff SELECT e.employee_id,a.account_id,e.hire_date,e.termination_date,e.employee_code FROM dbo.employees e WITH(UPDLOCK,HOLDLOCK)
 CROSS APPLY(SELECT TOP(1) ac.account_id FROM dbo.accounts ac JOIN dbo.roles r ON r.role_id=ac.role_id WHERE ac.employee_id=e.employee_id AND ac.is_active=1 AND r.role_code IN ('HR','EMPLOYEE') ORDER BY ac.account_id) a
 WHERE e.hire_date<=@to AND (e.termination_date IS NULL OR e.termination_date>=@from);
 -- Three historical periods and empty draft batches; no financial results are generated.
 INSERT dbo.payroll_periods(period_code,period_start,period_end,created_by)
 SELECT CONCAT('2026-',RIGHT(CONCAT('0',m),2)),DATEFROMPARTS(2026,m,1),EOMONTH(DATEFROMPARTS(2026,m,1)),@admin FROM(VALUES(8),(9),(10)) x(m)
 WHERE NOT EXISTS(SELECT 1 FROM dbo.payroll_periods p WHERE p.period_start<=EOMONTH(DATEFROMPARTS(2026,m,1)) AND p.period_end>=DATEFROMPARTS(2026,m,1));
 IF (SELECT COUNT(*) FROM dbo.payroll_periods WHERE period_start>=@from AND period_end<=@to)<>3 THROW 51503,N'Ky luong hien co khong khop 3 thang day du.',1;
 INSERT dbo.payroll_batches(period_id,version_no,status,adjustment_reason,created_by)
 SELECT p.period_id,1,'NHAP',NULL,@admin FROM dbo.payroll_periods p WHERE p.period_start>=@from AND p.period_end<=@to AND NOT EXISTS(SELECT 1 FROM dbo.payroll_batches b WHERE b.period_id=p.period_id);
 -- Fill contracts ONLY for staff with no contract history at all. Keep every existing signed term intact.
 DECLARE @contracts TABLE(contract_id bigint PRIMARY KEY,employee_id bigint);
 DECLARE @employee bigint,@hire date,@number varchar(50),@counter bigint;
 DECLARE missing CURSOR LOCAL FAST_FORWARD FOR SELECT s.employee_id,s.hire_date FROM @staff s WHERE NOT EXISTS(SELECT 1 FROM dbo.contracts c WHERE c.employee_id=s.employee_id);
 OPEN missing;FETCH NEXT FROM missing INTO @employee,@hire;
 WHILE @@FETCH_STATUS=0 BEGIN
  SELECT @counter=NEXT VALUE FOR dbo.contract_number_sequence;SET @number=CONCAT('XD24-',RIGHT(CONCAT('000000',@counter),CASE WHEN @counter<1000000 THEN 6 ELSE LEN(CONVERT(varchar(20),@counter)) END));
  INSERT dbo.contracts(employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status,created_by)
  OUTPUT inserted.contract_id,inserted.employee_id INTO @contracts
  VALUES(@employee,@ct,@number,@hire,@hire,DATEADD(day,-1,DATEADD(month,24,@hire)),'DA_KY',@admin);
  FETCH NEXT FROM missing INTO @employee,@hire;
 END;CLOSE missing;DEALLOCATE missing;
 INSERT dbo.contract_salary_terms(contract_id,effective_from,effective_to,base_salary,pay_basis,document_reference,created_by)
 SELECT c.contract_id,c.start_date,c.end_date,10000000+(c.employee_id%6)*1000000,'THANG',@tag,@admin FROM dbo.contracts c JOIN @contracts n ON n.contract_id=c.contract_id;
 DECLARE @salaryCount int=@@ROWCOUNT;
 -- Phone support is monthly; demonstration meal support is a fixed monthly amount.
 INSERT dbo.employee_allowances(employee_id,allowance_type_id,effective_from,effective_to,calculation_basis,amount,condition_note,created_by)
 SELECT s.employee_id,@phone,CASE WHEN s.hire_date>@from THEN s.hire_date ELSE @from END,CASE WHEN s.termination_date<@to THEN s.termination_date ELSE @to END,'THEO_KY',300000,@tag,@admin FROM @staff s
 WHERE NOT EXISTS(SELECT 1 FROM dbo.employee_allowances a WHERE a.employee_id=s.employee_id AND a.allowance_type_id=@phone AND a.effective_from<=@to AND (a.effective_to IS NULL OR a.effective_to>=@from));
 DECLARE @allowanceCount int=@@ROWCOUNT;
 IF NOT EXISTS(SELECT 1 FROM dbo.allowance_types WHERE type_code='SEED_MEAL_2026') INSERT dbo.allowance_types(type_code,type_name,description,is_taxable,counts_for_social_insurance,created_by) VALUES('SEED_MEAL_2026',N'Hỗ trợ ăn ca (dữ liệu mẫu)',@tag,1,0,@admin);
 DECLARE @meal bigint=(SELECT allowance_type_id FROM dbo.allowance_types WHERE type_code='SEED_MEAL_2026');
 INSERT dbo.employee_allowances(employee_id,allowance_type_id,effective_from,effective_to,calculation_basis,amount,condition_note,created_by)
 SELECT s.employee_id,@meal,CASE WHEN s.hire_date>@from THEN s.hire_date ELSE @from END,CASE WHEN s.termination_date<@to THEN s.termination_date ELSE @to END,'THEO_KY',550000,@tag,@admin FROM @staff s
 WHERE NOT EXISTS(SELECT 1 FROM dbo.employee_allowances a WHERE a.employee_id=s.employee_id AND a.allowance_type_id=@meal AND a.effective_from<=@to AND (a.effective_to IS NULL OR a.effective_to>=@from));
 SET @allowanceCount+=@@ROWCOUNT;
 DECLARE @dates TABLE(day date PRIMARY KEY);
 ;WITH days AS(SELECT @from d UNION ALL SELECT DATEADD(day,1,d) FROM days WHERE d<@to)INSERT @dates SELECT d FROM days WHERE DATEDIFF(day,'19000101',d)%7<5 AND d NOT IN ('20260901','20260902') OPTION(MAXRECURSION 200);
 DECLARE @new TABLE(schedule_id bigint PRIMARY KEY,employee_id bigint,work_date date,planned_start datetime2,planned_end datetime2);
 INSERT dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,break_detail_status,note,created_by)
 OUTPUT inserted.schedule_id,inserted.employee_id,inserted.work_date,inserted.planned_start,inserted.planned_end INTO @new
 SELECT s.employee_id,@shift,d.day,DATEADD(hour,8,CAST(d.day AS datetime2)),DATEADD(hour,17,CAST(d.day AS datetime2)),60,'VERIFIED',@tag,@admin FROM @staff s CROSS JOIN @dates d
 WHERE d.day>=s.hire_date AND (s.termination_date IS NULL OR d.day<=s.termination_date)
 AND EXISTS(SELECT 1 FROM dbo.employee_assignments a WHERE a.employee_id=s.employee_id AND a.effective_from<=d.day AND (a.effective_to IS NULL OR a.effective_to>d.day))
 AND NOT EXISTS(SELECT 1 FROM dbo.work_schedules w WHERE w.employee_id=s.employee_id AND w.cancelled_at IS NULL AND (w.work_date=d.day OR w.planned_start<DATEADD(day,1,CAST(d.day AS datetime2)) AND w.planned_end>CAST(d.day AS datetime2)))
 AND NOT EXISTS(SELECT 1 FROM dbo.employee_requests r JOIN dbo.leave_request_details l ON l.request_id=r.request_id WHERE r.employee_id=s.employee_id AND r.status='DA_DUYET' AND l.leave_start<DATEADD(day,1,CAST(d.day AS datetime2)) AND l.leave_end>CAST(d.day AS datetime2) AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id));
 INSERT dbo.work_schedule_breaks(schedule_id,break_start,break_end)SELECT schedule_id,DATEADD(hour,4,planned_start),DATEADD(hour,5,planned_start) FROM @new;
 DECLARE @attendance TABLE(attendance_id bigint PRIMARY KEY,schedule_id bigint);
 INSERT dbo.attendance_records(employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,attendance_result,verification_status,confirmed_by,confirmed_at,created_by,correction_reason,check_in_reviewed_by,check_in_reviewed_at,check_in_reviewed_value,check_out_reviewed_by,check_out_reviewed_at,check_out_reviewed_value)
 OUTPUT inserted.attendance_id,inserted.schedule_id INTO @attendance
 SELECT n.employee_id,n.schedule_id,n.work_date,v.actual_start,v.actual_end,CASE WHEN v.mode=0 THEN 0 ELSE 60 END,CASE WHEN v.mode=0 THEN 'VANG' ELSE 'DI_LAM' END,'DA_XAC_NHAN',@admin,@now,CASE WHEN v.mode=0 THEN @admin ELSE s.account_id END,
 CONCAT(@tag,N' - ',CASE v.mode WHEN 0 THEN N'Vắng (mẫu, không có giờ vào/ra)' WHEN 1 THEN N'Đi muộn 30 phút' WHEN 2 THEN N'Về sớm 45 phút' WHEN 3 THEN N'Muộn 20 phút, sớm 20 phút' ELSE N'Đi làm bình thường' END),
 CASE WHEN v.mode<>0 THEN @admin END,CASE WHEN v.mode<>0 THEN @now END,v.actual_start,CASE WHEN v.mode<>0 THEN @admin END,CASE WHEN v.mode<>0 THEN @now END,v.actual_end
 FROM @new n JOIN @staff s ON s.employee_id=n.employee_id CROSS APPLY(SELECT (n.employee_id+DAY(n.work_date))%23 mode) m
 CROSS APPLY(SELECT m.mode,CASE WHEN m.mode=0 THEN NULL ELSE DATEADD(minute,CASE m.mode WHEN 1 THEN 30 WHEN 3 THEN 20 ELSE 0 END,n.planned_start) END actual_start,CASE WHEN m.mode=0 THEN NULL ELSE DATEADD(minute,CASE m.mode WHEN 2 THEN -45 WHEN 3 THEN -20 ELSE 0 END,n.planned_end) END actual_end) v;
 INSERT dbo.attendance_actual_breaks(attendance_id,break_start,break_end,source_start,source_end,source_break_minutes,created_by)
 SELECT a.attendance_id,DATEADD(hour,4,n.planned_start),DATEADD(hour,5,n.planned_start),r.actual_start,r.actual_end,60,@admin FROM @attendance a JOIN @new n ON n.schedule_id=a.schedule_id JOIN dbo.attendance_records r ON r.attendance_id=a.attendance_id WHERE r.attendance_result='DI_LAM';
 -- One approved/confirmed OT per EXT employee/month; preserve all old OT states.
 DECLARE @ots TABLE(overtime_id bigint PRIMARY KEY,employee_id bigint,requested_start datetime2,requested_end datetime2);
 INSERT dbo.overtime_requests(employee_id,request_code,requested_start,requested_end,reason,work_content,submitted_at,status,processed_by,processed_at,actual_start,actual_end,actual_break_minutes,confirmed_by,confirmed_at,created_by,requested_break_status)
 OUTPUT inserted.overtime_id,inserted.employee_id,inserted.requested_start,inserted.requested_end INTO @ots
 SELECT s.employee_id,CONCAT('PAY26-OT-',m.month,'-',s.employee_id),v.a,v.z,@tag,N'Chuẩn bị tài liệu/hỗ trợ tuyển sinh ngoài giờ (mẫu)',DATEADD(hour,-39,v.a),'DA_DUYET',@hr,DATEADD(hour,-11,v.a),DATEADD(minute,CASE WHEN s.employee_id%4=1 THEN 10 ELSE 0 END,v.a),DATEADD(minute,CASE WHEN s.employee_id%4=2 THEN -15 ELSE 0 END,v.z),15,NULL,NULL,s.account_id,'VERIFIED'
 FROM @staff s CROSS JOIN(VALUES(8),(9),(10)) m(month)
 CROSS APPLY(SELECT DATEADD(day,4*((s.employee_id%4)),DATEFROMPARTS(2026,m.month,3)) day) d
 CROSS APPLY(SELECT DATEADD(hour,18,CAST(d.day AS datetime2)) a,DATEADD(hour,20,CAST(d.day AS datetime2)) z) v
 WHERE s.employee_code LIKE 'EXT26%' AND s.hire_date<=d.day AND (s.termination_date IS NULL OR s.termination_date>=d.day)
 AND NOT EXISTS(SELECT 1 FROM dbo.overtime_requests o WHERE o.employee_id=s.employee_id AND o.status<>'DA_HUY' AND o.requested_start<v.z AND o.requested_end>v.a)
 AND NOT EXISTS(SELECT 1 FROM dbo.work_schedules w WHERE w.employee_id=s.employee_id AND w.cancelled_at IS NULL AND w.planned_start<v.z AND w.planned_end>v.a);
 INSERT dbo.overtime_requested_breaks(overtime_id,break_start,break_end,created_by)SELECT overtime_id,DATEADD(minute,60,requested_start),DATEADD(minute,75,requested_start),@admin FROM @ots;
 INSERT dbo.overtime_breaks(overtime_id,break_start,break_end,created_by)SELECT overtime_id,DATEADD(minute,60,requested_start),DATEADD(minute,75,requested_start),@admin FROM @ots;
 UPDATE o SET confirmed_by=@admin,confirmed_at=@now FROM dbo.overtime_requests o JOIN @ots n ON n.overtime_id=o.overtime_id;
 -- Explicit import audit, not fabricated historical user action logs.
 INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,after_data_json)
 SELECT @admin,'SEED_PAYROLL_ATTENDANCE','attendance_records',CONVERT(varchar(30),a.attendance_id),@tag,(SELECT a.schedule_id scheduleId FOR JSON PATH,WITHOUT_ARRAY_WRAPPER) FROM @attendance a;
 INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason)
 SELECT @admin,'SEED_PAYROLL_CONTRACT','contracts',CONVERT(varchar(30),contract_id),@tag FROM @contracts;
 INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason)
 SELECT @admin,'SEED_PAYROLL_OT','overtime_requests',CONVERT(varchar(30),overtime_id),@tag FROM @ots;
 -- Integrity checks before committing.
 IF EXISTS(SELECT 1 FROM dbo.attendance_records a JOIN @attendance n ON n.attendance_id=a.attendance_id WHERE a.attendance_result='VANG' AND (a.actual_start IS NOT NULL OR a.actual_end IS NOT NULL))THROW 51504,N'Vang khong duoc co gio cong.',1;
 IF EXISTS(SELECT 1 FROM dbo.attendance_records a JOIN @attendance n ON n.attendance_id=a.attendance_id WHERE a.attendance_result='DI_LAM' AND (SELECT COALESCE(SUM(DATEDIFF_BIG(minute,b.break_start,b.break_end)),0) FROM dbo.attendance_actual_breaks b WHERE b.attendance_id=a.attendance_id AND b.superseded_at IS NULL)<>a.actual_break_minutes)THROW 51505,N'Khoang nghi khong khop.',1;
 IF EXISTS(SELECT 1 FROM @new a JOIN dbo.work_schedules b ON b.employee_id=a.employee_id AND b.schedule_id<>a.schedule_id AND b.cancelled_at IS NULL AND a.planned_start<b.planned_end AND a.planned_end>b.planned_start)THROW 51506,N'Lich moi bi trung.',1;
 INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,after_data_json)
 VALUES(@admin,'SEED_PAYROLL_INPUTS','payroll_periods',@tag,N'Nạp dữ liệu giả lập tháng 8,9,10/2026; giữ dữ liệu cũ; không tính/chốt lương',
 (SELECT (SELECT COUNT(*) FROM @staff) employees,(SELECT COUNT(*) FROM @new) schedules,(SELECT COUNT(*) FROM @attendance) attendance,(SELECT COUNT(*) FROM @contracts) contracts,@salaryCount salaryTerms,@allowanceCount allowances,(SELECT COUNT(*) FROM @ots) overtime FOR JSON PATH,WITHOUT_ARRAY_WRAPPER));
 SELECT @Commit commit_requested,(SELECT COUNT(*) FROM @staff) employees,(SELECT COUNT(*) FROM @new) schedules,(SELECT COUNT(*) FROM @attendance) attendance,(SELECT COUNT(*) FROM @contracts) contracts,@salaryCount salary_terms,@allowanceCount allowances,(SELECT COUNT(*) FROM @ots) overtime;
 SELECT MONTH(n.work_date) month,a.attendance_result,COUNT(*) count FROM @new n JOIN dbo.attendance_records a ON a.schedule_id=n.schedule_id GROUP BY MONTH(n.work_date),a.attendance_result ORDER BY month,a.attendance_result;
 IF @Commit=1 COMMIT;ELSE ROLLBACK;
END TRY
BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK;THROW;END CATCH;
