/* Additive. Old requests retain their original workflow; new requests snapshot the first-stage requirement. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.employee_requests') IS NULL
    THROW 51500,N'Chọn database HRM có schema trước khi chạy.',1;
IF OBJECT_ID('dbo.personnel_request_approval_stages') IS NULL
BEGIN
 CREATE TABLE dbo.personnel_request_approval_stages (
  request_id bigint NOT NULL PRIMARY KEY REFERENCES dbo.employee_requests(request_id),
  department_id bigint NULL REFERENCES dbo.departments(department_id),
  stage_status varchar(16) NOT NULL CHECK(stage_status IN ('PENDING','APPROVED','REJECTED','SKIPPED')),
  decided_by bigint NULL REFERENCES dbo.accounts(account_id),
  decided_at datetime2 NULL,
  note nvarchar(1000) NULL,
  created_at datetime2 NOT NULL DEFAULT SYSUTCDATETIME()
 );
END;
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('dbo.personnel_request_approval_stages') AND name='department_id' AND is_nullable=0)
 ALTER TABLE dbo.personnel_request_approval_stages ALTER COLUMN department_id bigint NULL;
COMMIT;
