-- Tạo cấu trúc phạm vi HR cho database được chọn; không tự cấp phạm vi.
-- Dựa trên phần B của HRM_GOP_KHOANG_NGHI_OT_VA_HR_SCOPES.sql.
-- Chỉ dùng cho database chưa có bảng này. Không tự chuyển database bằng USE.
SET XACT_ABORT ON;
SET NOCOUNT ON;

IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 55130, N'Hãy chọn database HRM trước khi chạy.', 1;
IF OBJECT_ID(N'dbo.accounts', N'U') IS NULL OR OBJECT_ID(N'dbo.departments', N'U') IS NULL
    THROW 55131, N'Thiếu bảng accounts hoặc departments; dừng.', 1;
IF OBJECT_ID(N'dbo.hr_department_scopes', N'U') IS NOT NULL
    THROW 55132, N'Bảng hr_department_scopes đã tồn tại; dừng để kiểm tra cấu trúc.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    CREATE TABLE dbo.hr_department_scopes (
        scope_id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_hr_department_scopes PRIMARY KEY,
        hr_account_id bigint NOT NULL,
        department_id bigint NOT NULL,
        effective_from datetime2(3) NOT NULL,
        effective_to datetime2(3) NULL,
        revoked_at datetime2(3) NULL,
        revoked_by bigint NULL,
        created_at datetime2(3) NOT NULL CONSTRAINT DF_hr_scopes_created_at DEFAULT (SYSUTCDATETIME()),
        created_by bigint NOT NULL,
        updated_at datetime2(3) NULL,
        updated_by bigint NULL,
        CONSTRAINT FK_hr_scopes_hr_account FOREIGN KEY (hr_account_id) REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_hr_scopes_department FOREIGN KEY (department_id) REFERENCES dbo.departments(department_id),
        CONSTRAINT FK_hr_scopes_created_by FOREIGN KEY (created_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_hr_scopes_updated_by FOREIGN KEY (updated_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_hr_scopes_revoked_by FOREIGN KEY (revoked_by) REFERENCES dbo.accounts(account_id),
        CONSTRAINT CK_hr_scopes_effective_dates CHECK (effective_to IS NULL OR effective_to > effective_from),
        CONSTRAINT CK_hr_scopes_revocation CHECK ((revoked_at IS NULL AND revoked_by IS NULL)
            OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL)),
        CONSTRAINT CK_hr_scopes_update_metadata CHECK ((updated_at IS NULL AND updated_by IS NULL)
            OR (updated_at IS NOT NULL AND updated_by IS NOT NULL))
    );
    CREATE UNIQUE INDEX UX_hr_scopes_open ON dbo.hr_department_scopes (hr_account_id, department_id)
        WHERE effective_to IS NULL AND revoked_at IS NULL;
    CREATE INDEX IX_hr_scopes_account_dates ON dbo.hr_department_scopes (hr_account_id, effective_from, effective_to)
        INCLUDE (department_id, revoked_at);
    CREATE INDEX IX_hr_scopes_pair_dates ON dbo.hr_department_scopes (hr_account_id, department_id, effective_from, effective_to)
        INCLUDE (revoked_at);
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
