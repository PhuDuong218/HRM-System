:ON ERROR EXIT
:setvar DatabaseName "hrm_center_full"
USE [$(DatabaseName)];
GO
SET QUOTED_IDENTIFIER ON; SET XACT_ABORT ON; SET NOCOUNT ON;
BEGIN TRY
BEGIN TRAN;
IF NOT EXISTS(SELECT 1 FROM dbo.accounts WHERE username='demo.admin') OR EXISTS(SELECT 1 FROM dbo.employee_requests WHERE request_code LIKE 'DEMO-%') THROW 51002,N'Run demo seed once before workflow examples.',1;
DECLARE @admin bigint=(SELECT account_id FROM dbo.accounts WHERE username='demo.admin'),@hr bigint=(SELECT account_id FROM dbo.accounts WHERE username='demo.hr'),@today date=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time');
INSERT dbo.role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM dbo.roles r CROSS JOIN dbo.permissions p
WHERE ((r.role_code='HR' AND p.permission_code<>'ACCOUNT_MANAGE') OR (r.role_code='EMPLOYEE' AND p.permission_code IN('LEAVE_REQUEST_CREATE','OVERTIME_REQUEST_CREATE')))
AND NOT EXISTS(SELECT 1 FROM dbo.role_permissions x WHERE x.role_id=r.role_id AND x.permission_id=p.permission_id);
DECLARE @worker bigint=(SELECT employee_id FROM dbo.employees WHERE employee_code='DEMO009'),@dt bigint=(SELECT department_id FROM dbo.departments WHERE department_code='DT'),@tv bigint=(SELECT department_id FROM dbo.departments WHERE department_code='TV');
UPDATE dbo.employee_assignments SET department_id=@tv,effective_to='2025-01-01' WHERE employee_id=@worker AND effective_to IS NULL;
INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from,created_by) SELECT @worker,@dt,position_id,'2025-01-01',@admin FROM dbo.positions WHERE position_code='GV';
DECLARE @n int=9;
WHILE @n<=10 BEGIN
 DECLARE @code varchar(50)=CONCAT('DEMO',RIGHT(CONCAT('000',@n),3)),@employee bigint,@schedule bigint,@request bigint,@type bigint=(SELECT leave_type_id FROM dbo.leave_types WHERE type_code='ANNUAL'),@from datetime2,@to datetime2,@department bigint;
 SELECT @employee=employee_id FROM dbo.employees WHERE employee_code=@code;
 SELECT TOP(1) @schedule=schedule_id,@from=planned_start,@to=DATEADD(hour,4,planned_start) FROM dbo.work_schedules WHERE employee_id=@employee AND work_date>=DATEADD(day,3,@today) ORDER BY work_date;
 SELECT @department=department_id FROM dbo.employee_assignments WHERE employee_id=@employee AND effective_to IS NULL;
 INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,created_by) VALUES(@employee,CONCAT('DEMO-LEAVE-',@n),'NGHI_PHEP',N'Đơn nghỉ phép mẫu, xử lý theo quy trình hai cấp',@admin);
 SELECT @request=request_id FROM dbo.employee_requests WHERE request_code=CONCAT('DEMO-LEAVE-',@n);
 INSERT dbo.leave_request_details(request_id,leave_type_id,leave_start,leave_end,created_by) VALUES(@request,@type,@from,@to,@admin);
 INSERT dbo.personnel_request_approval_stages(request_id,department_id,stage_status) VALUES(@request,@department,'PENDING');
 SET @n+=1;
END;
DECLARE @resigner bigint=(SELECT employee_id FROM dbo.employees WHERE employee_code='DEMO011'),@resign bigint;
INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,created_by) VALUES(@resigner,'DEMO-RESIGN-011','NGHI_VIEC',N'Đơn nghỉ việc mẫu đang chờ, chưa thay đổi trạng thái nhân viên',@admin);
SELECT @resign=request_id FROM dbo.employee_requests WHERE request_code='DEMO-RESIGN-011';
INSERT dbo.resignation_request_details(request_id,requested_last_working_date,created_by) VALUES(@resign,DATEADD(day,45,@today),@admin);
INSERT dbo.personnel_request_approval_stages(request_id,department_id,stage_status) SELECT @resign,department_id,'PENDING' FROM dbo.employee_assignments WHERE employee_id=@resigner AND effective_to IS NULL;
INSERT dbo.employee_decisions(employee_id,decision_number,decision_type,content,issued_date,issued_by,approval_status,payroll_effect,created_by)
SELECT @worker,'DEMO-KT-009','KHEN_THUONG',N'Ghi nhận đóng góp chuyên môn (mẫu, không ảnh hưởng lương)',@today,@admin,'CHO_DUYET','NONE',@admin;
INSERT dbo.overtime_requests(employee_id,request_code,requested_start,requested_end,reason,created_by)
VALUES(@worker,'DEMO-OT-009',DATEADD(hour,18,CAST(DATEADD(day,3,@today) AS datetime2)),DATEADD(hour,20,CAST(DATEADD(day,3,@today) AS datetime2)),N'Tăng ca mẫu chờ duyệt, chưa tính lương',@admin);
INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,after_data_json)
SELECT @admin,'EMPLOYEE_CREATE','employees',CONVERT(varchar(100),e.employee_id),N'Khởi tạo hồ sơ giả lập phục vụ đồ án',
 (SELECT e.employee_code employeeCode,e.full_name fullName,e.address address,e.identity_number identityNumber FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)
FROM dbo.employees e;
COMMIT;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK; THROW; END CATCH;
GO
