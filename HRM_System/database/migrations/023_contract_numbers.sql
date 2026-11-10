-- Server-generated contract numbers. Never renumber existing contracts.
SET XACT_ABORT ON;
BEGIN TRAN;
IF OBJECT_ID('dbo.contract_number_sequence','SO') IS NULL BEGIN
 DECLARE @start bigint,@sql nvarchar(max);
 SELECT @start=COALESCE(MAX(TRY_CONVERT(bigint,SUBSTRING(c.contract_number,LEN(t.type_code)+2,50))),0)+1
 FROM dbo.contracts c JOIN dbo.contract_types t ON LEFT(c.contract_number,LEN(t.type_code)+1)=t.type_code+'-';
 SET @sql=N'CREATE SEQUENCE dbo.contract_number_sequence AS bigint START WITH '+CONVERT(nvarchar(30),@start)+N' INCREMENT BY 1 NO CYCLE NO CACHE;';EXEC sys.sp_executesql @sql;
END;
IF USER_ID('hituyetn') IS NOT NULL GRANT UPDATE ON OBJECT::dbo.contract_number_sequence TO [hituyetn];
COMMIT;
