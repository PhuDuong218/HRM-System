-- Run on the application database. No existing employee code is renamed.
SET XACT_ABORT ON; SET NOCOUNT ON;
BEGIN TRAN;
IF OBJECT_ID('dbo.employee_code_counters','U') IS NULL
 CREATE TABLE dbo.employee_code_counters(prefix varchar(20) NOT NULL PRIMARY KEY,last_number int NOT NULL CHECK(last_number BETWEEN 0 AND 99999));
IF OBJECT_ID('dbo.employee_code_state','U') IS NULL
 CREATE TABLE dbo.employee_code_state(employee_id bigint NOT NULL PRIMARY KEY REFERENCES dbo.employees(employee_id),position_id bigint NOT NULL REFERENCES dbo.positions(position_id));
-- Reserve numbers from codes already present. Existing counters never decrease.
DECLARE @prefix varchar(20);
DECLARE prefixes CURSOR LOCAL FAST_FORWARD FOR SELECT prefix FROM (VALUES('TVV'),('GV'),('TP'),('NS'),('KT'),('KTV'),('QT')) t(prefix);
OPEN prefixes; FETCH NEXT FROM prefixes INTO @prefix;
WHILE @@FETCH_STATUS=0 BEGIN
 DECLARE @maximum int=(SELECT COALESCE(MAX(TRY_CONVERT(int,SUBSTRING(employee_code,LEN(@prefix)+1,5))),0) FROM dbo.employees WHERE LEFT(employee_code,LEN(@prefix))=@prefix AND LEN(employee_code)=LEN(@prefix)+5 AND SUBSTRING(employee_code,LEN(@prefix)+1,5) NOT LIKE '%[^0-9]%');
 IF NOT EXISTS(SELECT 1 FROM dbo.employee_code_counters WITH(UPDLOCK,HOLDLOCK) WHERE prefix=@prefix) INSERT dbo.employee_code_counters VALUES(@prefix,@maximum);
 ELSE UPDATE dbo.employee_code_counters SET last_number=CASE WHEN last_number<@maximum THEN @maximum ELSE last_number END WHERE prefix=@prefix;
 FETCH NEXT FROM prefixes INTO @prefix;
END;
CLOSE prefixes; DEALLOCATE prefixes;
COMMIT;
