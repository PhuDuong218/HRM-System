-- Additive schedule/break/workflow storage. Select the existing HRM database.
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.work_schedules') IS NULL
    OR OBJECT_ID('dbo.employee_requests') IS NULL OR OBJECT_ID('dbo.work_shift_breaks') IS NULL
    THROW 51500,N'Select the HRM database and apply migration 004 first.',1;
 IF COL_LENGTH('dbo.work_schedules','break_detail_status') IS NULL
    ALTER TABLE dbo.work_schedules ADD break_detail_status varchar(24) NOT NULL
       CONSTRAINT DF_schedule_break_detail DEFAULT('LEGACY_UNVERIFIED') WITH VALUES;
 IF OBJECT_ID('dbo.work_schedule_breaks') IS NULL
    CREATE TABLE dbo.work_schedule_breaks(
      break_id bigint IDENTITY PRIMARY KEY,
      schedule_id bigint NOT NULL REFERENCES dbo.work_schedules(schedule_id),
      break_start datetime2(3) NOT NULL,break_end datetime2(3) NOT NULL,superseded_at datetime2(3) NULL,
      CONSTRAINT CK_schedule_break_window CHECK(break_end>break_start)
    );
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.work_schedule_breaks') AND name='IX_schedule_break_current')
    CREATE INDEX IX_schedule_break_current ON dbo.work_schedule_breaks(schedule_id,superseded_at,break_start);
 -- Zero aggregate minutes need no invented break window. Positive old totals stay unresolved.
 EXEC(N'UPDATE dbo.work_schedules SET break_detail_status=''VERIFIED''
        WHERE planned_break_minutes=0 AND break_detail_status=''LEGACY_UNVERIFIED''
          AND NOT EXISTS(SELECT 1 FROM dbo.work_schedule_breaks b WHERE b.schedule_id=work_schedules.schedule_id AND b.superseded_at IS NULL)');
 IF OBJECT_ID('dbo.schedule_change_request_details') IS NULL
    CREATE TABLE dbo.schedule_change_request_details(
      request_id bigint NOT NULL PRIMARY KEY REFERENCES dbo.employee_requests(request_id),
      schedule_id bigint NOT NULL REFERENCES dbo.work_schedules(schedule_id),
      original_start datetime2(3) NOT NULL,original_end datetime2(3) NOT NULL,
      proposed_shift_id bigint NULL REFERENCES dbo.work_shifts(shift_id),
      proposed_start datetime2(3) NOT NULL,proposed_end datetime2(3) NOT NULL,
      original_row_version binary(8) NOT NULL,
      CONSTRAINT CK_schedule_change_time CHECK(original_end>original_start AND proposed_end>proposed_start)
    );
 IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.schedule_change_request_details') AND name='IX_schedule_change_schedule')
    CREATE INDEX IX_schedule_change_schedule ON dbo.schedule_change_request_details(schedule_id,request_id);
 IF OBJECT_ID('dbo.schedule_change_request_breaks') IS NULL
    CREATE TABLE dbo.schedule_change_request_breaks(
      break_id bigint IDENTITY PRIMARY KEY,
      request_id bigint NOT NULL REFERENCES dbo.schedule_change_request_details(request_id),
      break_start datetime2(3) NOT NULL,break_end datetime2(3) NOT NULL,
      CONSTRAINT CK_schedule_change_break CHECK(break_end>break_start)
    );
 IF COL_LENGTH('dbo.attendance_records','invalidated_at') IS NULL
    ALTER TABLE dbo.attendance_records ADD invalidated_at datetime2(3) NULL;
 IF COL_LENGTH('dbo.attendance_records','invalidated_by') IS NULL
    ALTER TABLE dbo.attendance_records ADD invalidated_by bigint NULL REFERENCES dbo.accounts(account_id);
 IF COL_LENGTH('dbo.attendance_records','invalidation_reason') IS NULL
    ALTER TABLE dbo.attendance_records ADD invalidation_reason nvarchar(1000) NULL;
 -- Extend existing predicates, retaining the rules of personnel request types.
 DECLARE @type nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_requests') AND name='CK_requests_type');
 IF @type IS NULL THROW 51501,N'Review employee_requests type constraints before migration.',1;
 IF @type NOT LIKE '%DOI_LICH%'
 BEGIN
    ALTER TABLE dbo.employee_requests DROP CONSTRAINT CK_requests_type;
    EXEC(N'ALTER TABLE dbo.employee_requests WITH CHECK ADD CONSTRAINT CK_requests_type CHECK(('+@type+N') OR request_type=''DOI_LICH'')');
 END;
 DECLARE @processing nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_requests') AND name='CK_requests_processing');
 IF @processing IS NULL THROW 51502,N'Review employee_requests processing constraints before migration.',1;
 IF @processing NOT LIKE '%HET_HIEU_LUC%'
 BEGIN
    ALTER TABLE dbo.employee_requests DROP CONSTRAINT CK_requests_processing;
    EXEC(N'ALTER TABLE dbo.employee_requests WITH CHECK ADD CONSTRAINT CK_requests_processing CHECK(('+@processing+N') OR
      (request_type=''DOI_LICH'' AND status=''HET_HIEU_LUC'' AND processed_by IS NULL AND processed_at IS NOT NULL AND rejection_reason IS NULL))');
 END;
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
