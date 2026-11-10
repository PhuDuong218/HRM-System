SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.attendance_records','check_in_reviewed_by') IS NULL
 ALTER TABLE dbo.attendance_records ADD check_in_reviewed_by bigint NULL REFERENCES dbo.accounts(account_id),check_in_reviewed_at datetime2(3) NULL,check_in_reviewed_value datetime2(7) NULL,
 check_out_reviewed_by bigint NULL REFERENCES dbo.accounts(account_id),check_out_reviewed_at datetime2(3) NULL,check_out_reviewed_value datetime2(7) NULL;
COMMIT;
GO
-- Preserve confirmed legacy attendance as reviewed, without fabricating a reviewer.
UPDATE dbo.attendance_records SET check_in_reviewed_by=confirmed_by,check_in_reviewed_at=confirmed_at,check_in_reviewed_value=actual_start,
 check_out_reviewed_by=confirmed_by,check_out_reviewed_at=confirmed_at,check_out_reviewed_value=actual_end
WHERE verification_status='DA_XAC_NHAN' AND confirmed_at IS NOT NULL AND check_in_reviewed_at IS NULL AND check_out_reviewed_at IS NULL;

BEGIN TRANSACTION;
ALTER TABLE dbo.attendance_portal_requests DROP CONSTRAINT CK_attendance_request_type;
ALTER TABLE dbo.attendance_portal_requests ADD CONSTRAINT CK_attendance_request_type CHECK(request_type IN ('MISSING_CHECKOUT','CONFIRMED_CORRECTION','MISSING_ATTENDANCE','UNCONFIRMED_CORRECTION'));
COMMIT;
