/* Xuân Anh: pending leave/resignation withdrawal ledger. Run after 001 and 002.
   Keeps the three existing request statuses and preserves submission history. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF DB_NAME() IN ('master','model','msdb','tempdb')
    OR OBJECT_ID('dbo.employee_requests') IS NULL
    OR OBJECT_ID('dbo.g2_resignation_executions') IS NULL
  THROW 51300,N'Chon database HRM da chay migrations 001 va 002.',1;
 IF OBJECT_ID('dbo.employee_request_withdrawals') IS NULL
  CREATE TABLE dbo.employee_request_withdrawals (
   request_id bigint NOT NULL PRIMARY KEY REFERENCES dbo.employee_requests(request_id),
   withdrawn_by bigint NOT NULL REFERENCES dbo.accounts(account_id),
   withdrawn_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
   reason nvarchar(1000) NOT NULL CHECK(LEN(LTRIM(RTRIM(reason)))>0)
  );
 EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_withdrawal_pending_owner ON dbo.employee_request_withdrawals AFTER INSERT AS
 BEGIN SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id
    JOIN dbo.accounts a ON a.account_id=i.withdrawn_by
    WHERE r.status<>''CHO_DUYET'' OR a.employee_id<>r.employee_id)
   THROW 51304,N''Chi chu don duoc rut don dang cho duyet.'',1;
 END;');
 EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_request_withdrawal_guard ON dbo.employee_requests AFTER UPDATE AS
 BEGIN SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_request_withdrawals w ON w.request_id=i.request_id WHERE i.status<>''CHO_DUYET'')
   THROW 51301,N''Don da rut khong duoc duyet/tu choi.'',1;
 END;');
 EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_withdrawal_immutable ON dbo.employee_request_withdrawals AFTER UPDATE,DELETE AS
 BEGIN SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM deleted) THROW 51302,N''Khong sua/xoa lich su rut don.'',1;
 END;');
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
