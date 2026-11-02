-- Run in the existing HRM database, after reviewing the database name.
-- Additive and rerunnable. Never guesses break times for existing shifts.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.work_shifts', N'U') IS NULL
        THROW 51000, N'Missing dbo.work_shifts. Select the correct HRM database.', 1;

    IF OBJECT_ID(N'dbo.work_shift_breaks', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.work_shift_breaks (
            break_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_work_shift_breaks PRIMARY KEY,
            shift_id bigint NOT NULL,
            break_start_offset_minutes int NOT NULL,
            break_end_offset_minutes int NOT NULL,
            superseded_at datetime2(7) NULL,
            CONSTRAINT FK_work_shift_breaks_shift FOREIGN KEY (shift_id) REFERENCES dbo.work_shifts(shift_id),
            CONSTRAINT CK_work_shift_breaks_offsets CHECK (
                break_start_offset_minutes >= 0
                AND break_end_offset_minutes > break_start_offset_minutes
                AND break_end_offset_minutes <= 1440)
        );
    END;

    IF COL_LENGTH(N'dbo.work_shift_breaks', N'shift_id') IS NULL
       OR COL_LENGTH(N'dbo.work_shift_breaks', N'break_start_offset_minutes') IS NULL
       OR COL_LENGTH(N'dbo.work_shift_breaks', N'break_end_offset_minutes') IS NULL
       OR COL_LENGTH(N'dbo.work_shift_breaks', N'superseded_at') IS NULL
        THROW 51001, N'Existing work_shift_breaks schema is incompatible. Review it before applying changes.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.work_shift_breaks') AND name=N'IX_work_shift_breaks_current')
        EXEC(N'CREATE INDEX IX_work_shift_breaks_current ON dbo.work_shift_breaks(shift_id, superseded_at, break_start_offset_minutes)');

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

-- Existing positive aggregate minutes are not proof of detailed break times.
-- These shifts need explicit break intervals entered by the operator.
SELECT s.shift_id, s.shift_code, s.break_minutes,
       COALESCE(b.detailed_minutes, 0) AS detailed_minutes
FROM dbo.work_shifts s
OUTER APPLY (
    SELECT SUM(x.break_end_offset_minutes-x.break_start_offset_minutes) AS detailed_minutes
    FROM dbo.work_shift_breaks x
    WHERE x.shift_id=s.shift_id AND x.superseded_at IS NULL
) b
WHERE s.break_minutes <> COALESCE(b.detailed_minutes, 0);
