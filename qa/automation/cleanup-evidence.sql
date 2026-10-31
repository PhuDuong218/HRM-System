SELECT DB_NAME() AS databaseName,
 COUNT_BIG(*) AS qaAccounts,
 SUM(CASE WHEN is_active=1 THEN 1 ELSE 0 END) AS activeQaAccounts
FROM dbo.accounts WHERE username LIKE 'QA[_]20261031[_]%'
FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;
