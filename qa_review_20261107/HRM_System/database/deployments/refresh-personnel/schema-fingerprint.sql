SET NOCOUNT ON;
DECLARE @definition nvarchar(max)=(SELECT SCHEMA_NAME(t.schema_id) s,t.name t,c.column_id,c.name c,ty.name type,c.max_length,c.precision,c.scale,c.is_nullable,c.is_identity,dc.definition default_expression,cc.definition computed_expression FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id LEFT JOIN sys.default_constraints dc ON dc.object_id=c.default_object_id LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE t.is_ms_shipped=0 ORDER BY t.object_id,c.column_id FOR JSON PATH);
SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',@definition),2) columns_fingerprint;
SELECT COUNT(*) tables_count FROM sys.tables WHERE is_ms_shipped=0;
SELECT COUNT(*) foreign_keys,SUM(CONVERT(int,is_disabled)) disabled,SUM(CONVERT(int,is_not_trusted)) untrusted FROM sys.foreign_keys;
SELECT COUNT(*) triggers_count,SUM(CONVERT(int,is_disabled)) disabled, SUM(CONVERT(bigint,CHECKSUM(OBJECT_DEFINITION(object_id)))) definitions_checksum FROM sys.triggers WHERE parent_class=1;
SELECT COUNT(*) indexes_count FROM sys.indexes WHERE object_id IN(SELECT object_id FROM sys.tables WHERE is_ms_shipped=0) AND index_id>0;
