-- Existing amounts continue to represent fixed money amounts.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.employee_allowances','U') IS NULL
        THROW 51950,N'Select an existing HRM database with dbo.employee_allowances.',1;
    IF COL_LENGTH('dbo.employee_allowances','amount_type') IS NULL
        ALTER TABLE dbo.employee_allowances ADD amount_type varchar(24) NOT NULL
            CONSTRAINT DF_employee_allowances_amount_type_015 DEFAULT ('FIXED') WITH VALUES;
    IF OBJECT_ID('dbo.CK_employee_allowances_amount_type_015','C') IS NULL
        EXEC(N'ALTER TABLE dbo.employee_allowances WITH CHECK ADD CONSTRAINT CK_employee_allowances_amount_type_015
            CHECK (amount_type=''FIXED'' OR (amount_type=''PERCENT_BASE_SALARY'' AND amount BETWEEN 0 AND 100));');
    ALTER TABLE dbo.employee_allowances WITH CHECK CHECK CONSTRAINT CK_employee_allowances_amount_type_015;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
