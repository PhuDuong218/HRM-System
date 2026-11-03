-- Run on the selected HRM database before creating custom roles through the API.
-- Existing roles, permissions, accounts and grants are preserved.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.roles', N'U') IS NULL
    THROW 51400, N'Missing dbo.roles; select the HRM database first.', 1;

IF EXISTS (SELECT 1 FROM sys.check_constraints
           WHERE parent_object_id = OBJECT_ID(N'dbo.roles') AND name = N'CK_roles_code')
    ALTER TABLE dbo.roles DROP CONSTRAINT CK_roles_code;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID(N'dbo.roles') AND name = N'CK_roles_code_format')
    ALTER TABLE dbo.roles WITH CHECK ADD CONSTRAINT CK_roles_code_format CHECK (
        LEN(role_code) BETWEEN 1 AND 50
        AND role_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z0-9_]%'
        AND LEFT(role_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'
    );

COMMIT TRANSACTION;
