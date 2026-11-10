-- Department approval for OT and attendance explanations. Existing final authorities remain unchanged.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.department_workflow_reviews','U') IS NULL
BEGIN
 CREATE TABLE dbo.department_workflow_reviews(
  review_id bigint IDENTITY PRIMARY KEY,
  source_type varchar(20) NOT NULL CHECK(source_type IN ('OT','ATTENDANCE')),
  source_id bigint NOT NULL,
  employee_id bigint NOT NULL REFERENCES dbo.employees(employee_id),
  reviewer_id bigint NOT NULL REFERENCES dbo.accounts(account_id),
  decision varchar(20) NOT NULL CHECK(decision IN ('APPROVED','REJECTED')),
  reason nvarchar(1000) NOT NULL,
  source_fingerprint varchar(64) NOT NULL,
  decided_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  CONSTRAINT UQ_department_review UNIQUE(source_type,source_id,source_fingerprint)
 );
END;
COMMIT;
