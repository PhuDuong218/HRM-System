-- Run manually after reviewing legacy records. No automatic data conversion.
-- SQL Server: dynamic SQL avoids compiling references before ADD COLUMN.
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.employee_decisions') IS NULL
    THROW 51700,N'Select the existing HRM database.',1;
 IF COL_LENGTH('dbo.employee_decisions','discipline_form') IS NULL
    ALTER TABLE dbo.employee_decisions ADD discipline_form varchar(30) NULL;
 IF COL_LENGTH('dbo.employee_decisions','violation_date') IS NULL
    ALTER TABLE dbo.employee_decisions ADD violation_date date NULL;
 IF COL_LENGTH('dbo.employee_decisions','salary_increase_delay_months') IS NULL
    ALTER TABLE dbo.employee_decisions ADD salary_increase_delay_months int NULL;
 DECLARE @predicate nvarchar(max)=N'
   (decision_type=''KY_LUAT'' AND payroll_effect=''NONE'' AND amount IS NULL AND apply_date IS NULL
     AND discipline_form IS NOT NULL AND discipline_form IN (''KHIEN_TRACH'',''KEO_DAI_NANG_LUONG'',''CACH_CHUC'',''SA_THAI'')
     AND violation_date IS NOT NULL
     AND ((discipline_form=''KEO_DAI_NANG_LUONG'' AND salary_increase_delay_months IS NOT NULL AND salary_increase_delay_months BETWEEN 1 AND 6)
       OR (discipline_form<>''KEO_DAI_NANG_LUONG'' AND salary_increase_delay_months IS NULL)))
   OR (decision_type=''KHEN_THUONG'' AND discipline_form IS NULL AND violation_date IS NULL AND salary_increase_delay_months IS NULL
     AND ((payroll_effect=''NONE'' AND amount IS NULL AND apply_date IS NULL)
       OR (payroll_effect=''ADD'' AND amount IS NOT NULL AND amount>0 AND apply_date IS NOT NULL)))
   OR (decision_type=''BOI_THUONG'' AND discipline_form IS NULL AND violation_date IS NULL AND salary_increase_delay_months IS NULL
     AND payroll_effect=''DEDUCT'' AND amount IS NOT NULL AND amount>0 AND apply_date IS NOT NULL)';
 DECLARE @check nvarchar(max)=N'
   IF EXISTS(SELECT 1 FROM dbo.employee_decisions WHERE NOT ('+@predicate+N'))
   BEGIN
     SELECT decision_id,decision_number,decision_type,payroll_effect,approval_status
       FROM dbo.employee_decisions WHERE NOT ('+@predicate+N');
     THROW 51701,N''Legacy decisions violate task 1. Migration stopped; HR must review. No records were converted.'',1;
   END;';
 EXEC sys.sp_executesql @check;
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_decisions') AND name='CK_decisions_type')
    ALTER TABLE dbo.employee_decisions DROP CONSTRAINT CK_decisions_type;
 ALTER TABLE dbo.employee_decisions WITH CHECK ADD CONSTRAINT CK_decisions_type
    CHECK(decision_type IN ('KHEN_THUONG','KY_LUAT','BOI_THUONG'));
 IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_decisions') AND name='CK_decisions_business_011')
    EXEC(N'ALTER TABLE dbo.employee_decisions WITH CHECK ADD CONSTRAINT CK_decisions_business_011 CHECK('+@predicate+N')');
 ALTER TABLE dbo.employee_decisions WITH CHECK CHECK CONSTRAINT CK_decisions_business_011;
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
