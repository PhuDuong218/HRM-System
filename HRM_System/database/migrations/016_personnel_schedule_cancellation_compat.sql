-- Personnel exit/leave guards need cancellation metadata even when the optional
-- schedule-change workflow (005) has not been installed. Same columns as 006;
-- Existing schedules and cancellation data are preserved. Also adds the
-- attendance invalidation metadata from 005 used by the same exit guard.
-- Safe to rerun; does not install the optional schedule-change workflow.
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF DB_NAME() IN ('master','model','msdb','tempdb')
    OR OBJECT_ID('dbo.work_schedules') IS NULL OR OBJECT_ID('dbo.accounts') IS NULL
    OR OBJECT_ID('dbo.attendance_records') IS NULL
    THROW 51616,N'Select the HRM database containing work_schedules, attendance_records and accounts.',1;
 IF COL_LENGTH('dbo.work_schedules','cancelled_at') IS NULL
    ALTER TABLE dbo.work_schedules ADD cancelled_at datetime2(3) NULL;
 IF COL_LENGTH('dbo.work_schedules','cancelled_by') IS NULL
    ALTER TABLE dbo.work_schedules ADD cancelled_by bigint NULL REFERENCES dbo.accounts(account_id);
 IF COL_LENGTH('dbo.work_schedules','cancellation_reason') IS NULL
    ALTER TABLE dbo.work_schedules ADD cancellation_reason nvarchar(1000) NULL;
 IF COL_LENGTH('dbo.attendance_records','invalidated_at') IS NULL
    ALTER TABLE dbo.attendance_records ADD invalidated_at datetime2(3) NULL;
 IF COL_LENGTH('dbo.attendance_records','invalidated_by') IS NULL
    ALTER TABLE dbo.attendance_records ADD invalidated_by bigint NULL REFERENCES dbo.accounts(account_id);
 IF COL_LENGTH('dbo.attendance_records','invalidation_reason') IS NULL
    ALTER TABLE dbo.attendance_records ADD invalidation_reason nvarchar(1000) NULL;
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
