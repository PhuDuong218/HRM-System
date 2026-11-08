-- Rollback never discards populated task-1 fields or compensation records.
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.employee_decisions') IS NULL
    THROW 51700,N'Select the existing HRM database.',1;
 IF EXISTS(SELECT 1 FROM dbo.employee_decisions WHERE decision_type='BOI_THUONG')
    THROW 51702,N'Compensation records exist. Review rollback manually; no data was deleted.',1;
 IF COL_LENGTH('dbo.employee_decisions','discipline_form') IS NOT NULL
    EXEC(N'IF EXISTS(SELECT 1 FROM dbo.employee_decisions WHERE discipline_form IS NOT NULL) THROW 51703,N''Discipline data exists; rollback stopped.'',1;');
 IF COL_LENGTH('dbo.employee_decisions','violation_date') IS NOT NULL
    EXEC(N'IF EXISTS(SELECT 1 FROM dbo.employee_decisions WHERE violation_date IS NOT NULL) THROW 51703,N''Violation dates exist; rollback stopped.'',1;');
 IF COL_LENGTH('dbo.employee_decisions','salary_increase_delay_months') IS NOT NULL
    EXEC(N'IF EXISTS(SELECT 1 FROM dbo.employee_decisions WHERE salary_increase_delay_months IS NOT NULL) THROW 51703,N''Delay months exist; rollback stopped.'',1;');
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_decisions') AND name='CK_decisions_business_011')
    ALTER TABLE dbo.employee_decisions DROP CONSTRAINT CK_decisions_business_011;
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_decisions') AND name='CK_decisions_type')
    ALTER TABLE dbo.employee_decisions DROP CONSTRAINT CK_decisions_type;
 ALTER TABLE dbo.employee_decisions WITH CHECK ADD CONSTRAINT CK_decisions_type CHECK(decision_type IN ('KHEN_THUONG','KY_LUAT'));
 IF COL_LENGTH('dbo.employee_decisions','discipline_form') IS NOT NULL
    ALTER TABLE dbo.employee_decisions DROP COLUMN discipline_form;
 IF COL_LENGTH('dbo.employee_decisions','violation_date') IS NOT NULL
    ALTER TABLE dbo.employee_decisions DROP COLUMN violation_date;
 IF COL_LENGTH('dbo.employee_decisions','salary_increase_delay_months') IS NOT NULL
    ALTER TABLE dbo.employee_decisions DROP COLUMN salary_increase_delay_months;
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
