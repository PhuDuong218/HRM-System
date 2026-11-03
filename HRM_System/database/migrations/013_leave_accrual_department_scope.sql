SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.personnel_leave_accrual_policies') IS NULL THROW 52300,N'Chạy migration 009 trước.',1;
IF COL_LENGTH('dbo.personnel_leave_accrual_policies','department_id') IS NULL
 ALTER TABLE dbo.personnel_leave_accrual_policies ADD department_id bigint NULL REFERENCES dbo.departments(department_id);
IF EXISTS(SELECT employee_id,period_start,period_end FROM dbo.personnel_leave_accrual_runs GROUP BY employee_id,period_start,period_end HAVING COUNT(*)>1)
 THROW 52301,N'Có nhiều lần cấp phép năm trong cùng kỳ; cần đối soát, migration không tự xóa dữ liệu.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.personnel_leave_accrual_runs') AND name='UX_personnel_annual_grant')
 CREATE UNIQUE INDEX UX_personnel_annual_grant ON dbo.personnel_leave_accrual_runs(employee_id,period_start,period_end);
COMMIT;
