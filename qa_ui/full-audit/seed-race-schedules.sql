SET XACT_ABORT ON;
SET NOCOUNT ON;
USE hrm_center_full;
BEGIN TRANSACTION;
DECLARE @employee bigint=(SELECT employee_id FROM dbo.employees WHERE employee_code='QA6-mw4lfj2n');
IF @employee IS NULL THROW 51000,'QA employee missing; stop',1;
IF NOT EXISTS(SELECT 1 FROM dbo.work_schedules WHERE employee_id=@employee AND work_date='2026-11-10')
INSERT dbo.work_schedules(employee_id,work_date,planned_start,planned_end,planned_break_minutes,break_detail_status,note)
VALUES(@employee,'2026-11-10','2026-11-10T08:00:00','2026-11-10T16:00:00',0,'VERIFIED','QA6 race only');
IF NOT EXISTS(SELECT 1 FROM dbo.work_schedules WHERE employee_id=@employee AND work_date='2026-11-11')
INSERT dbo.work_schedules(employee_id,work_date,planned_start,planned_end,planned_break_minutes,break_detail_status,note)
VALUES(@employee,'2026-11-11','2026-11-11T08:00:00','2026-11-11T16:00:00',0,'VERIFIED','QA6 race only');
COMMIT;
