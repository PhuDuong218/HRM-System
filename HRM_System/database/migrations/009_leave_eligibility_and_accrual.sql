SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.personnel_leave_rules') IS NULL THROW 51900,N'Chạy migration 006 trước.',1;
IF COL_LENGTH('dbo.personnel_leave_rules','minimum_service_months') IS NULL
 ALTER TABLE dbo.personnel_leave_rules ADD minimum_service_months int NOT NULL DEFAULT 0 CHECK(minimum_service_months BETWEEN 0 AND 1200), evidence_required bit NOT NULL DEFAULT 0;
IF OBJECT_ID('dbo.personnel_leave_evidence') IS NULL
 CREATE TABLE dbo.personnel_leave_evidence(request_id bigint PRIMARY KEY REFERENCES dbo.employee_requests(request_id),evidence_reference nvarchar(500) NOT NULL);
IF OBJECT_ID('dbo.personnel_leave_accrual_policies') IS NULL
 CREATE TABLE dbo.personnel_leave_accrual_policies(
  leave_type_id bigint PRIMARY KEY REFERENCES dbo.leave_types(leave_type_id),
  annual_days int NOT NULL CHECK(annual_days BETWEEN 1 AND 366),
  hours_per_day decimal(6,4) NOT NULL CHECK(hours_per_day>0 AND hours_per_day<=24),
  seniority_extra bit NOT NULL DEFAULT 0,
  updated_by bigint NOT NULL REFERENCES dbo.accounts(account_id),updated_at datetime2 NOT NULL DEFAULT SYSUTCDATETIME()
 );
IF OBJECT_ID('dbo.personnel_leave_accrual_runs') IS NULL
 CREATE TABLE dbo.personnel_leave_accrual_runs(
  entitlement_id bigint PRIMARY KEY REFERENCES dbo.leave_entitlements(entitlement_id),
  employee_id bigint NOT NULL REFERENCES dbo.employees(employee_id),leave_type_id bigint NOT NULL REFERENCES dbo.leave_types(leave_type_id),
  period_start date NOT NULL,period_end date NOT NULL,granted_hours decimal(12,4) NOT NULL,
  policy_snapshot nvarchar(1000) NOT NULL,created_by bigint NOT NULL REFERENCES dbo.accounts(account_id),created_at datetime2 NOT NULL DEFAULT SYSUTCDATETIME(),
  UNIQUE(employee_id,leave_type_id,period_start,period_end)
 );
COMMIT;
