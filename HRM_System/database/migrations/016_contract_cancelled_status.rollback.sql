-- Stop if a cancellation has been recorded. Preserve contract and audit history.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.contracts','U') IS NULL
        THROW 51960,N'Select the intended HRM database.',1;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id=OBJECT_ID('dbo.contracts') AND name='CK_contracts_status')
        THROW 51961,N'Expected CK_contracts_status is missing. Inspect the target schema.',1;

    IF EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id=OBJECT_ID('dbo.contracts') AND name='CK_contracts_status'
                 AND definition LIKE '%''DA_HUY''%')
    BEGIN
        -- Take the schema lock first so a concurrent cancellation cannot race the check.
        ALTER TABLE dbo.contracts DROP CONSTRAINT CK_contracts_status;
        IF EXISTS (SELECT 1 FROM dbo.contracts WHERE status='DA_HUY')
            THROW 51962,N'Cancelled contracts exist. Rollback stopped to preserve history.',1;
        ALTER TABLE dbo.contracts WITH CHECK ADD CONSTRAINT CK_contracts_status
            CHECK (status IN ('DA_KY','DA_KET_THUC'));
    END;
    ALTER TABLE dbo.contracts WITH CHECK CHECK CONSTRAINT CK_contracts_status;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    THROW;
END CATCH;
