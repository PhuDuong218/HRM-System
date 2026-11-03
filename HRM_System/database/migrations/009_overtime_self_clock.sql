-- Permit approved OT to retain a server check-in while awaiting check-out.
-- Existing complete/empty-actual rules are retained verbatim. No business rows change.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.overtime_requests','U') IS NULL THROW 51000,'Missing overtime_requests',1;
IF NOT EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID('dbo.CK_overtime_actual') AND name='OT_SELF_CLOCK_V1')
BEGIN
 DECLARE @original nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.overtime_requests') AND name='CK_overtime_actual');
 IF @original IS NULL THROW 51000,'Missing OT actual constraint; manual schema review required',1;
 ALTER TABLE dbo.overtime_requests DROP CONSTRAINT CK_overtime_actual;
 DECLARE @sql nvarchar(max)=N'ALTER TABLE dbo.overtime_requests WITH CHECK ADD CONSTRAINT CK_overtime_actual CHECK (('+@original+N') OR (status=''DA_DUYET'' AND actual_start IS NOT NULL AND actual_end IS NULL AND actual_break_minutes=0 AND confirmed_by IS NULL AND confirmed_at IS NULL));';
 EXEC sys.sp_executesql @sql;
 EXEC sys.sp_addextendedproperty @name=N'OT_SELF_CLOCK_V1',@value=N'Approved OT may await self checkout',@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'overtime_requests',@level2type=N'CONSTRAINT',@level2name=N'CK_overtime_actual';
END;
COMMIT;
