SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.attendance_portal_requests','U') IS NULL
BEGIN
 CREATE TABLE dbo.attendance_portal_requests (
  request_id bigint IDENTITY PRIMARY KEY,
  attendance_id bigint NOT NULL REFERENCES dbo.attendance_records(attendance_id),
  request_code varchar(40) NOT NULL UNIQUE,
  request_type varchar(24) NOT NULL CHECK(request_type IN ('MISSING_CHECKOUT','CONFIRMED_CORRECTION')),
  status varchar(20) NOT NULL DEFAULT 'CHO_XU_LY' CHECK(status IN ('CHO_XU_LY','DANG_XU_LY','DA_CHAP_NHAN','TU_CHOI')),
  proposed_start datetime2(3) NULL, proposed_end datetime2(3) NULL,
  proposed_break_minutes int NOT NULL CHECK(proposed_break_minutes>=0),
  proposed_result varchar(12) NOT NULL CHECK(proposed_result IN ('DI_LAM','VANG')),
  original_version binary(8) NOT NULL,
  original_start datetime2(3) NULL, original_end datetime2(3) NULL,
  original_break_minutes int NOT NULL, original_result varchar(12) NOT NULL,
  original_verification varchar(20) NOT NULL, original_confirmed_by bigint NULL,
  reason nvarchar(1000) NOT NULL,
  submitted_by bigint NOT NULL REFERENCES dbo.accounts(account_id),
  submitted_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  submission_deadline datetime2(3) NOT NULL,
  received_by bigint NULL REFERENCES dbo.accounts(account_id), received_at datetime2(3) NULL, processing_deadline datetime2(3) NULL,
  processed_by bigint NULL REFERENCES dbo.accounts(account_id), processed_at datetime2(3) NULL,
  decision_reason nvarchar(1000) NULL, accepted_end datetime2(3) NULL,
  CHECK ((status='CHO_XU_LY' AND received_by IS NULL AND processed_by IS NULL)
      OR (status='DANG_XU_LY' AND received_by IS NOT NULL AND processed_by IS NULL)
      OR (status IN ('DA_CHAP_NHAN','TU_CHOI') AND received_by IS NOT NULL AND processed_by IS NOT NULL)),
  CHECK(status<>'TU_CHOI' OR LEN(LTRIM(RTRIM(decision_reason)))>0)
 );
 CREATE INDEX IX_attendance_portal_requests_attendance ON dbo.attendance_portal_requests(attendance_id,status);
 CREATE UNIQUE INDEX UX_attendance_portal_requests_open ON dbo.attendance_portal_requests(attendance_id)
  WHERE status IN ('CHO_XU_LY','DANG_XU_LY');
END;
IF COL_LENGTH('dbo.attendance_portal_requests','processing_deadline') IS NULL
 ALTER TABLE dbo.attendance_portal_requests ADD processing_deadline datetime2(3) NULL;
-- New permission definitions receive initial grants only once. Reruns preserve later revocations.
IF NOT EXISTS(SELECT 1 FROM dbo.permissions WHERE permission_code='ATTENDANCE_CONFIRM')
BEGIN
 INSERT dbo.permissions(permission_code,permission_name) VALUES('ATTENDANCE_CONFIRM',N'Xác nhận chấm công');
 INSERT dbo.role_permissions(role_id,permission_id)
 SELECT r.role_id,p.permission_id FROM dbo.roles r CROSS JOIN dbo.permissions p
 WHERE r.role_code IN ('ADMIN','HR') AND p.permission_code='ATTENDANCE_CONFIRM';
END;
IF NOT EXISTS(SELECT 1 FROM dbo.permissions WHERE permission_code='ATTENDANCE_UNCONFIRM')
BEGIN
 INSERT dbo.permissions(permission_code,permission_name) VALUES('ATTENDANCE_UNCONFIRM',N'Hủy xác nhận chấm công');
 INSERT dbo.role_permissions(role_id,permission_id)
 SELECT r.role_id,p.permission_id FROM dbo.roles r CROSS JOIN dbo.permissions p
 WHERE r.role_code IN ('ADMIN','HR') AND p.permission_code='ATTENDANCE_UNCONFIRM';
END;
COMMIT;
