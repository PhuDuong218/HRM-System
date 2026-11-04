-- SQL Server. Select the intended existing HRM database before running.
-- Schema only; existing contracts retain NULL. Never rerun create_database.sql.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.contracts','U') IS NULL
        THROW 51930,N'Select the existing HRM database with dbo.contracts.',1;
    IF COL_LENGTH('dbo.contracts','termination_reason_type') IS NULL
        ALTER TABLE dbo.contracts ADD termination_reason_type varchar(30) NULL;
    IF NOT EXISTS(SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id=OBJECT_ID('dbo.contracts') AND name='CK_contracts_termination_reason_013')
        EXEC(N'ALTER TABLE dbo.contracts WITH CHECK ADD CONSTRAINT CK_contracts_termination_reason_013
            CHECK(termination_reason_type IS NULL OR termination_reason_type IN
                (''HET_HAN'',''THOA_THUAN'',''NLD_DON_PHUONG'',''NSDLD_DON_PHUONG'',''SA_THAI''))');
    ALTER TABLE dbo.contracts WITH CHECK CHECK CONSTRAINT CK_contracts_termination_reason_013;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
