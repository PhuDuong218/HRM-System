SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.work_schedules') IS NULL
    OR OBJECT_ID('dbo.schedule_change_request_details') IS NULL
    THROW 51600,N'Select the HRM database and apply migration 005 first.',1;
 IF COL_LENGTH('dbo.work_schedules','cancelled_at') IS NULL
    ALTER TABLE dbo.work_schedules ADD cancelled_at datetime2(3) NULL;
 IF COL_LENGTH('dbo.work_schedules','cancelled_by') IS NULL
    ALTER TABLE dbo.work_schedules ADD cancelled_by bigint NULL REFERENCES dbo.accounts(account_id);
 IF COL_LENGTH('dbo.work_schedules','cancellation_reason') IS NULL
    ALTER TABLE dbo.work_schedules ADD cancellation_reason nvarchar(1000) NULL;
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
