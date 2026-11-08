/*
  HRM-Center - migration thu cong, chi danh cho hrm_db.
  SECTION A: chi tiet gio nghi ca/lich va xu ly tang ca.
  SECTION B: bang pham vi phong ban cua HR.

  Chi doi database dich va dieu kien kiem tra database.
  Giu nguyen SQL tao bang, cot, rang buoc, index, trigger va transaction.
  Cac phan commit rieng: phan sau loi khong hoan tac phan truoc da commit.
  Kiem tra schema va ban sao luu truoc khi chay tung phan trong SSMS.
  File nay CHUA duoc thuc thi tren database.
*/

/*
  hrm_db - Combined migration script
  Part 1: work shift / work schedule break details
  Part 2: overtime processing and overtime break details
  Run in SSMS SQLCMD-compatible batch editor (GO). Only hrm_db.
  IMPORTANT: Two separate transactions; part 2 failing does NOT undo a committed part 1.
  Review preconditions and take a verified backup before execution.
  Original scripts below are preserved without changing SQL statements.
*/

/*
 VERSION 2 (fixed SQL Server batch binding) - chi hrm_db.
 Adds two break-detail tables plus a state marker for pre-existing work schedules.
 Prerequisites: verified backup; inspect precheck; exclusive maintenance window;
 backend must support detailed break writes before marking any row VERIFIED.
 This script NEVER infers old break windows or updates historic attendance / OT.
*/
USE hrm_db;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME() <> N'hrm_db'
    THROW 55101, N'Only hrm_db is permitted.', 1;
IF OBJECT_ID(N'dbo.work_shifts', N'U') IS NULL OR OBJECT_ID(N'dbo.work_schedules', N'U') IS NULL
    THROW 55102, N'Required work_shifts/work_schedules missing.', 1;
IF COL_LENGTH(N'dbo.work_shifts', N'shift_id') IS NULL
   OR COL_LENGTH(N'dbo.work_schedules', N'schedule_id') IS NULL
   OR COL_LENGTH(N'dbo.work_schedules', N'planned_start') IS NULL
   OR COL_LENGTH(N'dbo.work_schedules', N'planned_end') IS NULL
    THROW 55103, N'Required parent columns missing.', 1;
IF OBJECT_ID(N'dbo.work_shift_breaks') IS NOT NULL OR OBJECT_ID(N'dbo.work_schedule_breaks') IS NOT NULL
    THROW 55104, N'A proposed table already exists; stop to avoid collisions.', 1;
IF COL_LENGTH(N'dbo.work_schedules', N'break_detail_status') IS NOT NULL
    THROW 55105, N'break_detail_status already exists; review migration state.', 1;
IF HAS_PERMS_BY_NAME(N'dbo', N'SCHEMA', N'ALTER') <> 1
    THROW 55106, N'Missing ALTER on dbo schema.', 1;
IF HAS_PERMS_BY_NAME(N'dbo.work_schedules', N'OBJECT', N'ALTER') <> 1
    THROW 55107, N'Missing ALTER on work_schedules.', 1;
BEGIN TRY
 BEGIN TRANSACTION;
 CREATE TABLE dbo.work_shift_breaks (
   shift_break_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_work_shift_breaks PRIMARY KEY,
   shift_id bigint NOT NULL,
   break_start_offset_minutes int NOT NULL,
   break_end_offset_minutes int NOT NULL,
   CONSTRAINT FK_work_shift_breaks_shift FOREIGN KEY (shift_id) REFERENCES dbo.work_shifts(shift_id),
   CONSTRAINT CK_work_shift_breaks_offsets CHECK (
      break_start_offset_minutes >= 0
      AND break_end_offset_minutes > break_start_offset_minutes
      AND break_end_offset_minutes <= 2880
   )
 );
 CREATE INDEX IX_work_shift_breaks_parent ON dbo.work_shift_breaks(shift_id,break_start_offset_minutes);

 CREATE TABLE dbo.work_schedule_breaks (
   schedule_break_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_work_schedule_breaks PRIMARY KEY,
   schedule_id bigint NOT NULL,
   break_start datetime2(3) NOT NULL,
   break_end datetime2(3) NOT NULL,
   CONSTRAINT FK_work_schedule_breaks_schedule FOREIGN KEY (schedule_id) REFERENCES dbo.work_schedules(schedule_id),
   CONSTRAINT CK_work_schedule_breaks_time CHECK (break_end > break_start)
 );
 CREATE INDEX IX_work_schedule_breaks_parent ON dbo.work_schedule_breaks(schedule_id,break_start);

 ALTER TABLE dbo.work_schedules ADD
     break_detail_status varchar(24) NOT NULL
       CONSTRAINT DF_work_schedules_break_detail_status DEFAULT ('LEGACY_UNVERIFIED') WITH VALUES;
 -- Dynamic DDL prevents same-batch binding to a newly added column.
 EXEC sys.sp_executesql N'ALTER TABLE dbo.work_schedules WITH CHECK ADD CONSTRAINT CK_work_schedules_break_detail_status
   CHECK (break_detail_status IN (''LEGACY_UNVERIFIED'',''VERIFIED''))';

 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
-- Postchecks (read only)
SELECT name FROM sys.tables WHERE name IN ('work_shift_breaks','work_schedule_breaks');
IF COL_LENGTH(N'dbo.work_schedules', N'break_detail_status') IS NULL
    THROW 55108, N'Migration did not add break_detail_status; inspect previous errors.', 1;
EXEC sys.sp_executesql N'SELECT break_detail_status, COUNT_BIG(*) AS schedule_count FROM dbo.work_schedules GROUP BY break_detail_status';
SELECT name,is_disabled,is_not_trusted FROM sys.check_constraints WHERE parent_object_id IN
 (OBJECT_ID('dbo.work_shift_breaks'), OBJECT_ID('dbo.work_schedule_breaks'), OBJECT_ID('dbo.work_schedules'));
GO

-- ==============================================================================
-- PART 2: OT MIGRATION (original script follows unchanged)
-- ==============================================================================

/*
  HRM-Center OT: migration có điều kiện, chỉ hrm_db.
  Phạm vi phê duyệt: CK_overtime_processing, tr_overtime_no_self_approval, bảng overtime_breaks.
  Chạy bằng kết nối SQL có ALTER đối với dbo.overtime_requests và CREATE TABLE/ALTER schema.
  Kiểm tra bản sao lưu hrm_db trước khi chạy.
*/
USE hrm_db;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'hrm_db'
    THROW 53101, N'SAI DATABASE: chỉ được chạy trong hrm_db.', 1;
IF OBJECT_ID(N'dbo.overtime_requests', N'U') IS NULL
    THROW 53102, N'Không thấy dbo.overtime_requests; dừng.', 1;
IF OBJECT_ID(N'dbo.audit_logs', N'U') IS NULL
    THROW 53103, N'Không thấy dbo.audit_logs; dừng.', 1;
IF OBJECT_ID(N'dbo.accounts', N'U') IS NULL
    THROW 53104, N'Không thấy dbo.accounts; dừng.', 1;
IF HAS_PERMS_BY_NAME(N'dbo.overtime_requests', 'OBJECT', 'ALTER') <> 1
    THROW 53105, N'Tài khoản chạy thiếu quyền ALTER trên dbo.overtime_requests.', 1;
IF HAS_PERMS_BY_NAME(N'dbo', 'SCHEMA', 'ALTER') <> 1
    THROW 53106, N'Tài khoản chạy thiếu quyền ALTER trên schema dbo để tạo bảng/trigger.', 1;
IF OBJECT_ID(N'dbo.overtime_breaks', N'U') IS NOT NULL
    THROW 53107, N'overtime_breaks đã tồn tại. Dừng để kiểm tra trước, không ghi đè.', 1;
IF OBJECT_ID(N'dbo.tr_overtime_breaks_validate', N'TR') IS NOT NULL
    THROW 53108, N'Trigger nghỉ OT đã tồn tại. Dừng để kiểm tra.', 1;

DECLARE @currentCheck nvarchar(max) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.overtime_requests')
      AND name = N'CK_overtime_processing'
);
DECLARE @currentTrigger nvarchar(max) = OBJECT_DEFINITION(OBJECT_ID(N'dbo.tr_overtime_no_self_approval'));
IF @currentCheck IS NULL OR @currentTrigger IS NULL
    THROW 53109, N'Không đọc được CHECK hoặc trigger. Cần VIEW DEFINITION và kiểm tra schema.', 1;
IF CHARINDEX(N'''CHO_DUYET''', @currentCheck) = 0
   OR CHARINDEX(N'''DA_DUYET''', @currentCheck) = 0
   OR CHARINDEX(N'''TU_CHOI''', @currentCheck) = 0
   OR CHARINDEX(N'''DA_HUY''', @currentCheck) > 0
    THROW 53110, N'CHECK hiện tại không khớp ba trạng thái dự kiến; dừng để rà soát.', 1;
IF CHARINDEX(N'51009', @currentTrigger) = 0
   OR CHARINDEX(N'51010', @currentTrigger) = 0
   OR CHARINDEX(N'inserted', @currentTrigger) = 0
   OR CHARINDEX(N'deleted', @currentTrigger) = 0
    THROW 53111, N'Trigger hiện tại khác bản đã rà soát; dừng.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    ALTER TABLE dbo.overtime_requests DROP CONSTRAINT CK_overtime_processing;
    ALTER TABLE dbo.overtime_requests WITH CHECK ADD CONSTRAINT CK_overtime_processing CHECK (
        (status = 'CHO_DUYET' AND processed_by IS NULL AND processed_at IS NULL AND rejection_reason IS NULL)
        OR (status = 'DA_DUYET' AND processed_by IS NOT NULL AND processed_at IS NOT NULL AND rejection_reason IS NULL)
        OR (status = 'TU_CHOI' AND processed_by IS NOT NULL AND processed_at IS NOT NULL
            AND rejection_reason IS NOT NULL AND LEN(LTRIM(RTRIM(rejection_reason))) > 0)
        OR (status = 'DA_HUY' AND rejection_reason IS NULL
            AND ((processed_by IS NULL AND processed_at IS NULL)
                 OR (processed_by IS NOT NULL AND processed_at IS NOT NULL))
            AND actual_start IS NULL AND actual_end IS NULL AND actual_break_minutes = 0
            AND confirmed_by IS NULL AND confirmed_at IS NULL)
    );

    CREATE TABLE dbo.overtime_breaks (
        break_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_overtime_breaks PRIMARY KEY,
        overtime_id bigint NOT NULL,
        break_start datetime2(3) NOT NULL,
        break_end datetime2(3) NOT NULL,
        is_active bit NOT NULL CONSTRAINT DF_overtime_breaks_is_active DEFAULT (1),
        created_at datetime2(3) NOT NULL CONSTRAINT DF_overtime_breaks_created_at DEFAULT (SYSUTCDATETIME()),
        created_by bigint NULL,
        CONSTRAINT FK_overtime_breaks_request FOREIGN KEY (overtime_id) REFERENCES dbo.overtime_requests(overtime_id),
        CONSTRAINT FK_overtime_breaks_account FOREIGN KEY (created_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_overtime_breaks_duration CHECK (break_end > break_start)
    );
    CREATE INDEX IX_overtime_breaks_request_active
        ON dbo.overtime_breaks(overtime_id, is_active, break_start);

    EXEC(N'
CREATE OR ALTER TRIGGER dbo.tr_overtime_no_self_approval
ON dbo.overtime_requests
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN dbo.accounts a ON a.account_id = i.processed_by
        WHERE a.employee_id = i.employee_id
    )
        THROW 51009, N''Không tự duyệt tăng ca.'', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i JOIN deleted d ON d.overtime_id = i.overtime_id
        WHERE
            i.employee_id <> d.employee_id
            OR (
                d.status <> ''CHO_DUYET''
                AND (
                    -- Chỉ cho phép DA_DUYET -> DA_HUY khi mọi trường nghiệp vụ khác giữ nguyên
                    NOT (
                        d.status = ''DA_DUYET'' AND i.status = ''DA_HUY''
                        AND i.requested_start = d.requested_start
                        AND i.requested_end = d.requested_end
                        AND i.reason = d.reason
                        AND i.processed_by = d.processed_by
                        AND i.processed_at = d.processed_at
                        AND ISNULL(i.rejection_reason, N'''') = ISNULL(d.rejection_reason, N'''')
                        AND d.actual_start IS NULL AND d.actual_end IS NULL
                        AND d.actual_break_minutes = 0
                        AND d.confirmed_by IS NULL AND d.confirmed_at IS NULL
                        AND i.actual_start IS NULL AND i.actual_end IS NULL
                        AND i.actual_break_minutes = 0
                        AND i.confirmed_by IS NULL AND i.confirmed_at IS NULL
                    )
                    AND (
                        i.status <> d.status OR i.requested_start <> d.requested_start
                        OR i.requested_end <> d.requested_end OR i.reason <> d.reason
                        OR ISNULL(i.processed_by, -1) <> ISNULL(d.processed_by, -1)
                        OR ISNULL(i.processed_at, ''19000101'') <> ISNULL(d.processed_at, ''19000101'')
                        OR ISNULL(i.rejection_reason, N'''') <> ISNULL(d.rejection_reason, N'''')
                    )
                )
            )
    ) THROW 51010, N''Không đổi chủ hoặc kết quả duyệt tăng ca đã xử lý.'', 1;
END
');

    EXEC(N'
CREATE TRIGGER dbo.tr_overtime_breaks_validate
ON dbo.overtime_breaks AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
      SELECT 1 FROM inserted b JOIN dbo.overtime_requests o ON o.overtime_id = b.overtime_id
      WHERE b.is_active = 1 AND
         (o.actual_start IS NULL OR o.actual_end IS NULL
          OR b.break_start < o.actual_start OR b.break_end > o.actual_end
          OR o.confirmed_at IS NOT NULL)
    ) THROW 53120, N''Khoảng nghỉ phải thuộc thời gian OT chưa xác nhận.'', 1;

    IF EXISTS (
      SELECT 1 FROM inserted i JOIN dbo.overtime_breaks b
        ON b.overtime_id = i.overtime_id AND b.break_id <> i.break_id
        AND i.is_active = 1 AND b.is_active = 1
        AND i.break_start < b.break_end AND b.break_start < i.break_end
    ) THROW 53121, N''Các khoảng nghỉ OT không được chồng lấn.'', 1;
END
');

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

SELECT DB_NAME() AS database_name,
       OBJECT_ID(N'dbo.overtime_breaks', N'U') AS overtime_breaks_id,
       OBJECT_ID(N'dbo.tr_overtime_no_self_approval', N'TR') AS approval_trigger_id,
       OBJECT_ID(N'dbo.tr_overtime_breaks_validate', N'TR') AS breaks_trigger_id;

/* ===========================================================================
   END SECTION A (hrm_db)
   =========================================================================== */

/* ===========================================================================
   SECTION B: HR DEPARTMENT SCOPES (hrm_db)
   Source: HRM_Center_Huong_dan_hr_department_scopes (1).docx, section 4
   TARGET: This section is for hrm_db, the same database as section A.
   Run ONLY this section by selecting it deliberately in SSMS.
   =========================================================================== */

USE [hrm_db];
GO
SET XACT_ABORT ON;
GO
BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.hr_department_scopes', N'U') IS NOT NULL
    BEGIN
        THROW 51001,
            'hr_department_scopes da ton tai. Dung de kiem tra.',
            1;
    END;

    CREATE TABLE dbo.hr_department_scopes
    (
        scope_id BIGINT IDENTITY(1,1) NOT NULL,
        hr_account_id BIGINT NOT NULL,
        department_id BIGINT NOT NULL,
        effective_from DATETIME2(3) NOT NULL,
        effective_to DATETIME2(3) NULL,
        revoked_at DATETIME2(3) NULL,
        revoked_by BIGINT NULL,
        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_hr_scopes_created_at
            DEFAULT (SYSUTCDATETIME()),
        created_by BIGINT NOT NULL,
        updated_at DATETIME2(3) NULL,
        updated_by BIGINT NULL,

        CONSTRAINT PK_hr_department_scopes
            PRIMARY KEY (scope_id),
        CONSTRAINT FK_hr_scopes_hr_account
            FOREIGN KEY (hr_account_id)
            REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_hr_scopes_department
            FOREIGN KEY (department_id)
            REFERENCES dbo.departments(department_id),
        CONSTRAINT FK_hr_scopes_created_by
            FOREIGN KEY (created_by)
            REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_hr_scopes_updated_by
            FOREIGN KEY (updated_by)
            REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_hr_scopes_revoked_by
            FOREIGN KEY (revoked_by)
            REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_hr_scopes_effective_dates
            CHECK (effective_to IS NULL OR effective_to > effective_from),
        CONSTRAINT CK_hr_scopes_revocation
            CHECK ((revoked_at IS NULL AND revoked_by IS NULL)
                OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL)),
        CONSTRAINT CK_hr_scopes_update_metadata
            CHECK ((updated_at IS NULL AND updated_by IS NULL)
                OR (updated_at IS NOT NULL AND updated_by IS NOT NULL))
    );

    CREATE UNIQUE INDEX UX_hr_scopes_open
    ON dbo.hr_department_scopes (hr_account_id, department_id)
    WHERE effective_to IS NULL AND revoked_at IS NULL;

    CREATE INDEX IX_hr_scopes_account_dates
    ON dbo.hr_department_scopes
        (hr_account_id, effective_from, effective_to)
    INCLUDE (department_id, revoked_at);

    CREATE INDEX IX_hr_scopes_pair_dates
    ON dbo.hr_department_scopes
        (hr_account_id, department_id, effective_from, effective_to)
    INCLUDE (revoked_at);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
