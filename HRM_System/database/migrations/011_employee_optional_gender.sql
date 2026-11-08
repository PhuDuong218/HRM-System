SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.employees') IS NULL THROW 52100,N'Cần schema HRM.',1;
IF COL_LENGTH('dbo.employees','gender') IS NULL ALTER TABLE dbo.employees ADD gender varchar(20) NULL;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_employee_optional_gender')
 EXEC(N'ALTER TABLE dbo.employees ADD CONSTRAINT CK_employee_optional_gender CHECK(gender IS NULL OR gender IN (''MALE'',''FEMALE'',''OTHER'',''UNDISCLOSED''))');
COMMIT;
