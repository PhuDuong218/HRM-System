SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.attendance_portal_requests','schedule_id') IS NULL
    ALTER TABLE dbo.attendance_portal_requests ADD schedule_id bigint NULL;
GO
EXEC(N'UPDATE r SET schedule_id=a.schedule_id FROM dbo.attendance_portal_requests r JOIN dbo.attendance_records a ON a.attendance_id=r.attendance_id WHERE r.schedule_id IS NULL');
ALTER TABLE dbo.attendance_portal_requests ALTER COLUMN schedule_id bigint NOT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name='FK_attendance_request_schedule')
    ALTER TABLE dbo.attendance_portal_requests ADD CONSTRAINT FK_attendance_request_schedule FOREIGN KEY(schedule_id) REFERENCES dbo.work_schedules(schedule_id);
DROP INDEX IF EXISTS UX_attendance_portal_requests_open ON dbo.attendance_portal_requests;
DROP INDEX IF EXISTS IX_attendance_portal_requests_attendance ON dbo.attendance_portal_requests;
ALTER TABLE dbo.attendance_portal_requests ALTER COLUMN attendance_id bigint NULL;
ALTER TABLE dbo.attendance_portal_requests ALTER COLUMN original_version binary(8) NULL;
DECLARE @name sysname, @sql nvarchar(max);
DECLARE checks CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.attendance_portal_requests') AND definition LIKE '%request_type%';
OPEN checks; FETCH NEXT FROM checks INTO @name;
WHILE @@FETCH_STATUS=0
BEGIN
    SET @sql=N'ALTER TABLE dbo.attendance_portal_requests DROP CONSTRAINT '+QUOTENAME(@name);
    EXEC sp_executesql @sql;
    FETCH NEXT FROM checks INTO @name;
END;
CLOSE checks; DEALLOCATE checks;
ALTER TABLE dbo.attendance_portal_requests ADD CONSTRAINT CK_attendance_request_type CHECK(request_type IN ('MISSING_CHECKOUT','CONFIRMED_CORRECTION','MISSING_ATTENDANCE'));
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_attendance_request_original' AND parent_object_id=OBJECT_ID('dbo.attendance_portal_requests'))
ALTER TABLE dbo.attendance_portal_requests ADD CONSTRAINT CK_attendance_request_original CHECK(request_type='MISSING_ATTENDANCE' OR (attendance_id IS NOT NULL AND original_version IS NOT NULL));
CREATE INDEX IX_attendance_portal_requests_attendance ON dbo.attendance_portal_requests(attendance_id,status);
CREATE UNIQUE INDEX UX_attendance_portal_requests_open ON dbo.attendance_portal_requests(schedule_id) WHERE status IN ('CHO_XU_LY','DANG_XU_LY');
COMMIT;
