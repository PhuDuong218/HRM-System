SELECT
 JSON_QUERY((SELECT name FROM sys.tables ORDER BY name FOR JSON PATH)) AS tables,
 JSON_QUERY((SELECT OBJECT_NAME(object_id) AS tableName,name,type_name(user_type_id) AS dataType,max_length,is_nullable FROM sys.columns ORDER BY object_id,column_id FOR JSON PATH)) AS columns,
 JSON_QUERY((SELECT OBJECT_NAME(object_id) AS tableName,name,is_unique,filter_definition FROM sys.indexes WHERE name IS NOT NULL ORDER BY name FOR JSON PATH)) AS indexes,
 JSON_QUERY((SELECT name,OBJECT_NAME(parent_id) AS tableName,is_disabled,OBJECT_DEFINITION(object_id) AS definition FROM sys.triggers ORDER BY name FOR JSON PATH)) AS triggers,
 JSON_QUERY((SELECT r.role_code,p.permission_code FROM dbo.role_permissions rp JOIN dbo.roles r ON r.role_id=rp.role_id JOIN dbo.permissions p ON p.permission_id=rp.permission_id ORDER BY r.role_code,p.permission_code FOR JSON PATH)) AS rolePermissions
FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
