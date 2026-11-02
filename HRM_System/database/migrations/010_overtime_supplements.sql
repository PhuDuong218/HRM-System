SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.ot_supplement_requests','U') IS NULL
BEGIN
 CREATE TABLE dbo.ot_supplement_requests(
  request_id bigint IDENTITY PRIMARY KEY,overtime_id bigint NOT NULL REFERENCES dbo.overtime_requests(overtime_id),
  employee_id bigint NOT NULL REFERENCES dbo.employees(employee_id),parent_version binary(8) NOT NULL,
  proposed_start datetime2(3) NOT NULL,proposed_end datetime2(3) NOT NULL,reason nvarchar(1000) NOT NULL,
  status varchar(24) NOT NULL DEFAULT 'CHO_XU_LY',created_by bigint NOT NULL REFERENCES dbo.accounts(account_id),
  submitted_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),processed_by bigint NULL REFERENCES dbo.accounts(account_id),processed_at datetime2(3) NULL,decision_reason nvarchar(1000) NULL,
  CONSTRAINT CK_ot_supplement_range CHECK(proposed_end>proposed_start),
  CONSTRAINT CK_ot_supplement_status CHECK(status IN ('CHO_XU_LY','DA_CHAP_NHAN','TU_CHOI')),
  CONSTRAINT CK_ot_supplement_decision CHECK((status='CHO_XU_LY' AND processed_by IS NULL AND processed_at IS NULL) OR (status<>'CHO_XU_LY' AND processed_by IS NOT NULL AND processed_at IS NOT NULL AND processed_by<>created_by))
 );
 CREATE UNIQUE INDEX UX_ot_supplement_open ON dbo.ot_supplement_requests(overtime_id) WHERE status='CHO_XU_LY';
 CREATE TABLE dbo.ot_supplement_breaks(break_id bigint IDENTITY PRIMARY KEY,request_id bigint NOT NULL REFERENCES dbo.ot_supplement_requests(request_id),break_start datetime2(3) NOT NULL,break_end datetime2(3) NOT NULL,CHECK(break_end>break_start));
END;
COMMIT;
