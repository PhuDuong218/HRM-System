-- Backfill existing types using the same defaults as newly inserted types.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.allowance_types','U') IS NULL
        THROW 51940,N'Select an existing HRM database with dbo.allowance_types.',1;
    IF COL_LENGTH('dbo.allowance_types','is_taxable') IS NULL
        ALTER TABLE dbo.allowance_types ADD is_taxable bit NOT NULL
            CONSTRAINT DF_allowance_types_is_taxable_014 DEFAULT (1) WITH VALUES;
    IF COL_LENGTH('dbo.allowance_types','counts_for_social_insurance') IS NULL
        ALTER TABLE dbo.allowance_types ADD counts_for_social_insurance bit NOT NULL
            CONSTRAINT DF_allowance_types_counts_for_social_insurance_014 DEFAULT (0) WITH VALUES;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
