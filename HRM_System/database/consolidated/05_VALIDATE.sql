:ON ERROR EXIT
:setvar DatabaseName "hrm_center_full"
USE [$(DatabaseName)];
GO
SET QUOTED_IDENTIFIER ON; SET NOCOUNT ON;
DECLARE @sql nvarchar(max)=N'';
SELECT @sql+=N'ALTER TABLE '+QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)+N' WITH CHECK CHECK CONSTRAINT ALL;'+CHAR(10) FROM sys.tables;
EXEC sys.sp_executesql @sql;
IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1) THROW 51010,N'Foreign keys must be enabled and trusted.',1;
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE is_disabled=1 OR is_not_trusted=1) THROW 51011,N'CHECK constraints must be enabled and trusted.',1;
IF EXISTS(SELECT 1 FROM dbo.employees e OUTER APPLY(SELECT COUNT(*) n FROM dbo.employee_assignments a WHERE a.employee_id=e.employee_id AND a.effective_from<=CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS date) AND (a.effective_to IS NULL OR a.effective_to>CAST(SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time' AS date))) x WHERE e.employment_status='DANG_LAM' AND x.n<>1) THROW 51012,N'Each active employee requires exactly one current assignment.',1;
IF EXISTS(SELECT 1 FROM dbo.employee_qualifications WHERE is_deleted=0 GROUP BY employee_id HAVING SUM(CASE WHEN is_primary=1 THEN 1 ELSE 0 END)<>1) THROW 51013,N'Each qualified employee must have exactly one primary degree.',1;
DBCC CHECKCONSTRAINTS WITH ALL_CONSTRAINTS;
SELECT DB_NAME() database_name,(SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0) tables_count,(SELECT COUNT(*) FROM sys.triggers WHERE is_ms_shipped=0) triggers_count;
SELECT 'employees' category,COUNT(*) records FROM dbo.employees UNION ALL SELECT 'accounts',COUNT(*) FROM dbo.accounts UNION ALL SELECT 'assignments',COUNT(*) FROM dbo.employee_assignments UNION ALL SELECT 'contracts',COUNT(*) FROM dbo.contracts UNION ALL SELECT 'qualifications',COUNT(*) FROM dbo.employee_qualifications UNION ALL SELECT 'leave_entitlements',COUNT(*) FROM dbo.leave_entitlements UNION ALL SELECT 'schedules',COUNT(*) FROM dbo.work_schedules UNION ALL SELECT 'attendance',COUNT(*) FROM dbo.attendance_records UNION ALL SELECT 'employee_requests',COUNT(*) FROM dbo.employee_requests UNION ALL SELECT 'payroll_details',COUNT(*) FROM dbo.payroll_details;
SELECT N'PASS: constraints trusted, active assignments unique, primary qualifications consistent.' validation_result;
GO
