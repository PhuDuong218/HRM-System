SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.employee_requests') IS NULL THROW 51800,N'Cần schema HRM.',1;
IF OBJECT_ID('dbo.personnel_termination_decisions') IS NULL
 CREATE TABLE dbo.personnel_termination_decisions(
  request_id bigint PRIMARY KEY REFERENCES dbo.employee_requests(request_id),
  decision_type varchar(40) NOT NULL CHECK(decision_type IN ('MUTUAL_AGREEMENT','CONTRACT_EXPIRY','EMPLOYEE_RESIGNATION','EMPLOYER_DECISION','OTHER')),
  decision_number nvarchar(100) NOT NULL,
  created_by bigint NOT NULL REFERENCES dbo.accounts(account_id),
  created_at datetime2 NOT NULL DEFAULT SYSUTCDATETIME()
 );
COMMIT;
