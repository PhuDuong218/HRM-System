SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.work_schedules') IS NULL THROW 52200,N'Cần bảng lịch làm việc.',1;
IF OBJECT_ID('dbo.work_schedule_breaks') IS NULL
 CREATE TABLE dbo.work_schedule_breaks(
  schedule_break_id bigint IDENTITY PRIMARY KEY,schedule_id bigint NOT NULL REFERENCES dbo.work_schedules(schedule_id),
  break_start datetime2(3) NOT NULL,break_end datetime2(3) NOT NULL,superseded_at datetime2(3) NULL,
  CHECK(break_end>break_start)
 );
IF COL_LENGTH('dbo.work_schedule_breaks','superseded_at') IS NULL
 ALTER TABLE dbo.work_schedule_breaks ADD superseded_at datetime2(3) NULL;
IF OBJECT_ID('dbo.work_shift_breaks') IS NOT NULL AND COL_LENGTH('dbo.work_shift_breaks','superseded_at') IS NULL
 ALTER TABLE dbo.work_shift_breaks ADD superseded_at datetime2(3) NULL;
IF COL_LENGTH('dbo.work_schedules','break_detail_status') IS NULL
 ALTER TABLE dbo.work_schedules ADD break_detail_status varchar(24) NOT NULL CONSTRAINT DF_leave_schedule_break_status DEFAULT 'LEGACY_UNVERIFIED' WITH VALUES;
/* Legacy rows remain unverified. This migration never invents the position of a break. */
COMMIT;
