USE [hrm_center_full];
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @admin bigint,@employee bigint,@keeper bigint,@correlation uniqueidentifier=NEWID();
 SELECT @admin=role_id FROM dbo.roles WITH(UPDLOCK,HOLDLOCK) WHERE role_code='ADMIN';
 SELECT @employee=role_id FROM dbo.roles WHERE role_code='EMPLOYEE';
 SELECT @keeper=account_id FROM dbo.accounts WITH(UPDLOCK,HOLDLOCK) WHERE username='demo.admin' AND role_id=@admin AND is_active=1;
 IF @keeper IS NULL OR @employee IS NULL THROW 51000,N'Cannot verify active demo.admin and EMPLOYEE role; no changes applied.',1;
 DECLARE @before TABLE(account_id bigint,username nvarchar(100),role_id bigint,token_version int);
 INSERT @before SELECT account_id,username,role_id,token_version FROM dbo.accounts WITH(UPDLOCK,HOLDLOCK)
 WHERE account_id IN(33,34) AND username IN('xuananh123','viet123') AND role_id=@admin;
 IF (SELECT COUNT(*) FROM @before)<>2 THROW 51001,N'Target accounts no longer match reviewed state; no changes applied.',1;
 UPDATE a SET role_id=@employee,token_version=COALESCE(a.token_version,0)+1,updated_at=SYSUTCDATETIME(),updated_by=@keeper
 FROM dbo.accounts a JOIN @before b ON b.account_id=a.account_id;
 IF @@ROWCOUNT<>2 THROW 51002,N'Unexpected account update count.',1;
 INSERT dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json,correlation_id)
 SELECT NULL,SYSUTCDATETIME(),'ACCOUNT_ROLE_CHANGED','accounts',CONVERT(nvarchar(100),b.account_id),
 N'User-authorized maintenance: retain demo.admin as sole ADMIN; demote xuananh123/viet123 to EMPLOYEE and revoke existing sessions.',
 (SELECT b.username AS username,'ADMIN' AS roleCode,b.token_version AS tokenVersion FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),
 (SELECT a.username AS username,'EMPLOYEE' AS roleCode,a.token_version AS tokenVersion FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),@correlation
 FROM @before b JOIN dbo.accounts a ON a.account_id=b.account_id;
 IF (SELECT COUNT(*) FROM dbo.accounts WHERE role_id=@admin)<>1 THROW 51003,N'ADMIN count is not one; rolling back.',1;
 COMMIT;
 SELECT a.account_id,a.username,r.role_code,a.is_active,a.token_version FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
 WHERE a.account_id IN(@keeper,33,34);
 SELECT COUNT(*) AS admin_count FROM dbo.accounts WHERE role_id=@admin;
 SELECT COUNT(*) AS audit_records FROM dbo.audit_logs WHERE correlation_id=@correlation;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
