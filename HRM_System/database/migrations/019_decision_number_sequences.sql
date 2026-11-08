-- Automatic decision numbers. Existing decisions are retained unchanged.
-- Run on the target database as an administrator before starting the new backend.
SET XACT_ABORT ON;
BEGIN TRAN;
DECLARE @prefix varchar(2),@sequence sysname,@start bigint,@sql nvarchar(max);
DECLARE numbers CURSOR LOCAL FAST_FORWARD FOR SELECT * FROM (VALUES('KT','decision_number_kt'),('KL','decision_number_kl'),('BT','decision_number_bt')) x(prefix,sequence_name);
OPEN numbers;FETCH NEXT FROM numbers INTO @prefix,@sequence;
WHILE @@FETCH_STATUS=0 BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.sequences WHERE name=@sequence AND schema_id=SCHEMA_ID('dbo')) BEGIN
  SELECT @start=COALESCE(MAX(TRY_CONVERT(bigint,SUBSTRING(decision_number,4,47))),0)+1 FROM dbo.employee_decisions WHERE LEFT(decision_number,3)=@prefix+'-';
  SET @sql=N'CREATE SEQUENCE dbo.'+QUOTENAME(@sequence)+N' AS bigint START WITH '+CONVERT(nvarchar(30),@start)+N' INCREMENT BY 1 NO CYCLE NO CACHE;';
  EXEC sys.sp_executesql @sql;
 END;
 FETCH NEXT FROM numbers INTO @prefix,@sequence;
END;
CLOSE numbers;DEALLOCATE numbers;
-- UPDATE on a sequence permits NEXT VALUE FOR; restricted to the runtime user.
IF USER_ID('hituyetn') IS NOT NULL BEGIN
 GRANT UPDATE ON OBJECT::dbo.decision_number_kt TO [hituyetn];
 GRANT UPDATE ON OBJECT::dbo.decision_number_kl TO [hituyetn];
 GRANT UPDATE ON OBJECT::dbo.decision_number_bt TO [hituyetn];
END;
COMMIT;
