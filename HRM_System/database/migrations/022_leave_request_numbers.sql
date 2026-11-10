-- Automatic leave request codes. Preserve all existing request codes.
-- Sequences may contain gaps after rollback/cancellation; values are never recycled.
SET XACT_ABORT ON;
BEGIN TRAN;
DECLARE @prefix varchar(2),@sequence sysname,@start bigint,@sql nvarchar(max);
DECLARE numbers CURSOR LOCAL FAST_FORWARD FOR SELECT * FROM (VALUES
 ('PN','leave_request_number_pn'),('PO','leave_request_number_po'),
 ('TS','leave_request_number_ts'),('KP','leave_request_number_kp'),('NP','leave_request_number_np')) x(prefix,sequence_name);
OPEN numbers;FETCH NEXT FROM numbers INTO @prefix,@sequence;
WHILE @@FETCH_STATUS=0 BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.sequences WHERE name=@sequence AND schema_id=SCHEMA_ID('dbo')) BEGIN
  SELECT @start=COALESCE(MAX(TRY_CONVERT(bigint,SUBSTRING(request_code,4,100))),0)+1
   FROM dbo.employee_requests WHERE LEFT(request_code,3)=@prefix+'-';
  SET @sql=N'CREATE SEQUENCE dbo.'+QUOTENAME(@sequence)+N' AS bigint START WITH '+CONVERT(nvarchar(30),@start)+N' INCREMENT BY 1 NO CYCLE NO CACHE;';
  EXEC sys.sp_executesql @sql;
 END;
 IF USER_ID('hituyetn') IS NOT NULL BEGIN
  SET @sql=N'GRANT UPDATE ON OBJECT::dbo.'+QUOTENAME(@sequence)+N' TO [hituyetn];';
  EXEC sys.sp_executesql @sql;
 END;
 FETCH NEXT FROM numbers INTO @prefix,@sequence;
END;
CLOSE numbers;DEALLOCATE numbers;
COMMIT;
