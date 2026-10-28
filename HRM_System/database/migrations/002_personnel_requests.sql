/* Run after baseline + 001, in the selected HRM database. Additive, re-runnable. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.employee_requests') IS NULL
  THROW 51200,N'Chon database HRM co schema truoc khi chay G2.',1;
 IF COL_LENGTH('dbo.employee_qualifications','is_deleted') IS NULL
  ALTER TABLE dbo.employee_qualifications ADD is_deleted bit NOT NULL CONSTRAINT DF_g2_qualification_deleted DEFAULT(0);
 IF COL_LENGTH('dbo.employee_qualifications','deleted_at') IS NULL
  ALTER TABLE dbo.employee_qualifications ADD deleted_at datetime2(3) NULL;
 IF COL_LENGTH('dbo.employee_qualifications','deleted_by') IS NULL
  ALTER TABLE dbo.employee_qualifications ADD deleted_by bigint NULL REFERENCES dbo.accounts(account_id);
 /* Being paid and consuming an entitlement are separate policies. No automatic policy seed. */
 IF OBJECT_ID('dbo.g2_leave_policies') IS NULL
  CREATE TABLE dbo.g2_leave_policies (
   leave_type_id bigint NOT NULL PRIMARY KEY REFERENCES dbo.leave_types(leave_type_id),
   consumes_entitlement bit NOT NULL,
   updated_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
   updated_by bigint NOT NULL REFERENCES dbo.accounts(account_id)
  );
 /* Approved usage is derived from immutable request allocations; never decrement entitled_hours. */
 IF OBJECT_ID('dbo.g2_leave_allocations') IS NULL
  CREATE TABLE dbo.g2_leave_allocations (
   allocation_id bigint IDENTITY PRIMARY KEY,
   request_id bigint NOT NULL REFERENCES dbo.employee_requests(request_id),
   schedule_id bigint NOT NULL REFERENCES dbo.work_schedules(schedule_id),
   entitlement_id bigint NULL REFERENCES dbo.leave_entitlements(entitlement_id),
   charged_hours decimal(9,4) NOT NULL CHECK(charged_hours>0),
   created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
   CONSTRAINT UQ_g2_leave_schedule UNIQUE(request_id,schedule_id)
  );
 IF OBJECT_ID('dbo.g2_resignation_executions') IS NULL
  CREATE TABLE dbo.g2_resignation_executions (
   request_id bigint NOT NULL PRIMARY KEY REFERENCES dbo.employee_requests(request_id),
   executed_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
   last_working_date date NOT NULL
  );
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.g2_leave_allocations') AND name='IX_g2_leave_entitlement')
  CREATE INDEX IX_g2_leave_entitlement ON dbo.g2_leave_allocations(entitlement_id) INCLUDE(request_id,charged_hours);
 EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_g2_leave_allocations_immutable ON dbo.g2_leave_allocations AFTER UPDATE,DELETE AS
  BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 51201,N''Khong sua/xoa phan bo phep da ghi.'',1; END;');
 EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_g2_execution_immutable ON dbo.g2_resignation_executions AFTER UPDATE,DELETE AS
  BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 51202,N''Khong sua/xoa lich su thuc thi nghi viec.'',1; END;');
 /* Schedule changes cannot invalidate hours already approved by G2. Notes remain editable. */
 EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_g2_schedule_leave ON dbo.work_schedules AFTER INSERT,UPDATE,DELETE AS
 BEGIN SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.schedule_id=d.schedule_id
   JOIN dbo.g2_leave_allocations a ON a.schedule_id=d.schedule_id
   JOIN dbo.employee_requests r ON r.request_id=a.request_id AND r.status=''DA_DUYET''
   WHERE i.schedule_id IS NULL OR i.employee_id<>d.employee_id OR i.planned_start<>d.planned_start
    OR i.planned_end<>d.planned_end OR i.planned_break_minutes<>d.planned_break_minutes OR i.work_date<>d.work_date)
   THROW 51203,N''Lich da co phep duyet; can quy trinh dieu chinh co kiem soat.'',1;
  IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.schedule_id=i.schedule_id
   JOIN dbo.employee_requests r ON r.employee_id=i.employee_id AND r.status=''DA_DUYET''
   JOIN dbo.leave_request_details l ON l.request_id=r.request_id
   WHERE (d.schedule_id IS NULL OR i.employee_id<>d.employee_id OR i.planned_start<>d.planned_start OR i.planned_end<>d.planned_end)
    AND i.planned_start<l.leave_end AND i.planned_end>l.leave_start)
   THROW 51204,N''Lich moi/thay doi trung don phep da duyet.'',1;
 END;');
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
