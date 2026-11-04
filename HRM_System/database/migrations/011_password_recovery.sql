-- Chạy trên database HRM đã chọn.
SET XACT_ABORT ON;
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 55140, N'Hãy chọn database HRM trước khi chạy.', 1;
IF OBJECT_ID(N'dbo.accounts', N'U') IS NULL
    THROW 55141, N'Thiếu bảng accounts.', 1;
IF OBJECT_ID(N'dbo.password_recovery', N'U') IS NOT NULL
    THROW 55142, N'Bảng password_recovery đã tồn tại; hãy kiểm tra cấu trúc.', 1;
CREATE TABLE dbo.password_recovery (
    account_id bigint NOT NULL PRIMARY KEY REFERENCES dbo.accounts(account_id),
    code_hash varchar(64) NOT NULL,
    expires_at datetime2(6) NOT NULL,
    sent_at datetime2(6) NOT NULL,
    attempts int NOT NULL DEFAULT 0,
    token_version int NOT NULL
);
