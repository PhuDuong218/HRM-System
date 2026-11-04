SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.attendance_records','U') IS NULL THROW 51200,N'Missing attendance_records.',1;
IF OBJECT_ID('dbo.attendance_actual_breaks','U') IS NULL
BEGIN
 CREATE TABLE dbo.attendance_actual_breaks(
  break_id bigint IDENTITY PRIMARY KEY,
  attendance_id bigint NOT NULL REFERENCES dbo.attendance_records(attendance_id),
  break_start datetime2(7) NOT NULL,break_end datetime2(7) NOT NULL,
  source_start datetime2(7) NOT NULL,source_end datetime2(7) NOT NULL,
  source_break_minutes int NOT NULL CHECK(source_break_minutes>=0),
  created_by bigint NOT NULL REFERENCES dbo.accounts(account_id),
  created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  superseded_at datetime2(3) NULL,
  CHECK(break_end>break_start AND source_end>source_start
        AND break_start>=source_start AND break_end<=source_end)
 );
 CREATE INDEX IX_attendance_actual_breaks_current
 ON dbo.attendance_actual_breaks(attendance_id,superseded_at,break_start);
END;
COMMIT TRANSACTION;
