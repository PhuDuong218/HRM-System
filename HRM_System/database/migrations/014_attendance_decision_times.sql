SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.attendance_portal_requests','accepted_start') IS NULL
    ALTER TABLE dbo.attendance_portal_requests ADD accepted_start datetime2(3) NULL;
IF COL_LENGTH('dbo.attendance_portal_requests','adjustment_reason') IS NULL
    ALTER TABLE dbo.attendance_portal_requests ADD adjustment_reason nvarchar(1000) NULL;
COMMIT;
