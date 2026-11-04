-- Preserve classifications entered by HR. Roll back only while all rows have the original defaults.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.allowance_types','U') IS NULL
        THROW 51940,N'Select an existing HRM database with dbo.allowance_types.',1;
    IF COL_LENGTH('dbo.allowance_types','is_taxable') IS NOT NULL
    BEGIN
        EXEC(N'IF EXISTS(SELECT 1 FROM dbo.allowance_types WITH (TABLOCKX,HOLDLOCK) WHERE is_taxable<>1)
            THROW 51941,N''Tax flags have been configured. Rollback stopped.'',1;');
        ALTER TABLE dbo.allowance_types DROP CONSTRAINT DF_allowance_types_is_taxable_014;
        ALTER TABLE dbo.allowance_types DROP COLUMN is_taxable;
    END;
    IF COL_LENGTH('dbo.allowance_types','counts_for_social_insurance') IS NOT NULL
    BEGIN
        EXEC(N'IF EXISTS(SELECT 1 FROM dbo.allowance_types WITH (TABLOCKX,HOLDLOCK) WHERE counts_for_social_insurance<>0)
            THROW 51941,N''Social insurance flags have been configured. Rollback stopped.'',1;');
        ALTER TABLE dbo.allowance_types DROP CONSTRAINT DF_allowance_types_counts_for_social_insurance_014;
        ALTER TABLE dbo.allowance_types DROP COLUMN counts_for_social_insurance;
    END;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
