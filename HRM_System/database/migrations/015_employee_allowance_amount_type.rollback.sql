-- Stop before a percentage could be reinterpreted as a fixed money amount.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.employee_allowances','U') IS NULL
        THROW 51950,N'Select an existing HRM database with dbo.employee_allowances.',1;
    IF COL_LENGTH('dbo.employee_allowances','amount_type') IS NOT NULL
    BEGIN
        EXEC(N'IF EXISTS(SELECT 1 FROM dbo.employee_allowances WITH (TABLOCKX,HOLDLOCK) WHERE amount_type<>''FIXED'')
            THROW 51951,N''Percentage allowances exist. Rollback stopped.'',1;');
        ALTER TABLE dbo.employee_allowances DROP CONSTRAINT CK_employee_allowances_amount_type_015,
            DF_employee_allowances_amount_type_015;
        ALTER TABLE dbo.employee_allowances DROP COLUMN amount_type;
    END;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
