-- Stop rather than discard termination reasons already used by the application.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.contracts','U') IS NULL
        THROW 51930,N'Select the existing HRM database with dbo.contracts.',1;
    IF COL_LENGTH('dbo.contracts','termination_reason_type') IS NOT NULL
    BEGIN
        EXEC(N'IF EXISTS(SELECT 1 FROM dbo.contracts WHERE termination_reason_type IS NOT NULL)
            THROW 51931,N''Termination reasons exist; rollback stopped to preserve history.'',1;');
        IF EXISTS(SELECT 1 FROM sys.check_constraints
            WHERE parent_object_id=OBJECT_ID('dbo.contracts') AND name='CK_contracts_termination_reason_013')
            ALTER TABLE dbo.contracts DROP CONSTRAINT CK_contracts_termination_reason_013;
        ALTER TABLE dbo.contracts DROP COLUMN termination_reason_type;
    END;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
