SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.leave_types') IS NULL
 THROW 51600,N'Chọn database HRM có schema trước khi chạy.',1;
IF OBJECT_ID('dbo.personnel_leave_rules') IS NULL
 CREATE TABLE dbo.personnel_leave_rules (
  leave_type_id bigint NOT NULL PRIMARY KEY REFERENCES dbo.leave_types(leave_type_id),
  retroactive_days int NOT NULL DEFAULT 0 CHECK(retroactive_days BETWEEN 0 AND 7),
  minimum_notice_hours int NOT NULL DEFAULT 0 CHECK(minimum_notice_hours>=0),
  maximum_hours decimal(9,4) NULL CHECK(maximum_hours>0),
  updated_by bigint NOT NULL REFERENCES dbo.accounts(account_id),
  updated_at datetime2 NOT NULL DEFAULT SYSUTCDATETIME()
 );
COMMIT;
