-- Run on the intended HRM database after 013. Only changes the status CHECK.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR COL_LENGTH('dbo.contracts','termination_reason_type') IS NULL
        THROW 51960,N'Select the intended HRM database and apply migration 013 first.',1;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id=OBJECT_ID('dbo.contracts') AND name='CK_contracts_status')
        THROW 51961,N'Expected CK_contracts_status is missing. Inspect the target schema.',1;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id=OBJECT_ID('dbo.contracts') AND name='CK_contracts_status'
                     AND definition LIKE '%''DA_HUY''%')
    BEGIN
        ALTER TABLE dbo.contracts DROP CONSTRAINT CK_contracts_status;
        ALTER TABLE dbo.contracts WITH CHECK ADD CONSTRAINT CK_contracts_status
            CHECK (status IN ('DA_KY','DA_KET_THUC','DA_HUY'));
    END;
    ALTER TABLE dbo.contracts WITH CHECK CHECK CONSTRAINT CK_contracts_status;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    THROW;
END CATCH;
