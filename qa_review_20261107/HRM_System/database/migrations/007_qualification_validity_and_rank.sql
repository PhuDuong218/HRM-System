SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.employee_qualifications') IS NULL OR OBJECT_ID('dbo.qualification_levels') IS NULL
 THROW 51700,N'Cần schema HRM trước khi bổ sung thông tin bằng cấp.',1;
IF COL_LENGTH('dbo.employee_qualifications','issued_date') IS NULL
 ALTER TABLE dbo.employee_qualifications ADD issued_date date NULL;
IF COL_LENGTH('dbo.employee_qualifications','expiry_date') IS NULL
 ALTER TABLE dbo.employee_qualifications ADD expiry_date date NULL;
IF COL_LENGTH('dbo.qualification_levels','rank_order') IS NULL
 ALTER TABLE dbo.qualification_levels ADD rank_order int NULL;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_qualification_validity')
 EXEC(N'ALTER TABLE dbo.employee_qualifications ADD CONSTRAINT CK_qualification_validity CHECK(expiry_date IS NULL OR (issued_date IS NOT NULL AND expiry_date>=issued_date))');
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_qualification_rank')
 EXEC(N'ALTER TABLE dbo.qualification_levels ADD CONSTRAINT CK_qualification_rank CHECK(rank_order IS NULL OR rank_order BETWEEN 0 AND 1000)');
COMMIT;
