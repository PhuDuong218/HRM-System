SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.overtime_requests','work_content') IS NULL ALTER TABLE dbo.overtime_requests ADD work_content nvarchar(2000) NULL;
IF COL_LENGTH('dbo.overtime_requests','requested_break_status') IS NULL
 ALTER TABLE dbo.overtime_requests ADD requested_break_status varchar(24) NOT NULL CONSTRAINT DF_ot_requested_break_status DEFAULT 'LEGACY_UNVERIFIED';
IF OBJECT_ID('dbo.overtime_requested_breaks','U') IS NULL
 CREATE TABLE dbo.overtime_requested_breaks(break_id bigint IDENTITY PRIMARY KEY,overtime_id bigint NOT NULL REFERENCES dbo.overtime_requests(overtime_id),
 break_start datetime2(3) NOT NULL,break_end datetime2(3) NOT NULL,is_active bit NOT NULL DEFAULT 1,created_by bigint NULL REFERENCES dbo.accounts(account_id),created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),CHECK(break_end>break_start));
IF OBJECT_ID('dbo.overtime_breaks','U') IS NULL
 CREATE TABLE dbo.overtime_breaks(break_id bigint IDENTITY PRIMARY KEY,overtime_id bigint NOT NULL REFERENCES dbo.overtime_requests(overtime_id),
 break_start datetime2(3) NOT NULL,break_end datetime2(3) NOT NULL,is_active bit NOT NULL DEFAULT 1,created_by bigint NULL REFERENCES dbo.accounts(account_id),created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),CHECK(break_end>break_start));
IF OBJECT_ID('dbo.overtime_request_details','U') IS NULL
 CREATE TABLE dbo.overtime_request_details(request_id bigint PRIMARY KEY REFERENCES dbo.employee_requests(request_id),overtime_id bigint NOT NULL REFERENCES dbo.overtime_requests(overtime_id),created_by bigint NULL REFERENCES dbo.accounts(account_id),created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME());
IF OBJECT_ID('dbo.overtime_overflow_intervals','U') IS NULL
 CREATE TABLE dbo.overtime_overflow_intervals(interval_id bigint IDENTITY PRIMARY KEY,request_id bigint NOT NULL REFERENCES dbo.overtime_request_details(request_id),work_date date NOT NULL,
 requested_start datetime2(3) NOT NULL,requested_end datetime2(3) NOT NULL,approved_start datetime2(3) NULL,approved_end datetime2(3) NULL,
 created_by bigint NULL REFERENCES dbo.accounts(account_id),created_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),CHECK(requested_end>requested_start),
 CHECK((approved_start IS NULL AND approved_end IS NULL) OR (approved_start>=requested_start AND approved_end<=requested_end AND approved_end>approved_start)));
-- Preserve every existing check predicate; extend only for the newly supported states/types.
DECLARE @definition nvarchar(max),@sql nvarchar(max);
SELECT @definition=definition FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_requests') AND name='CK_requests_type';
IF @definition IS NOT NULL AND @definition NOT LIKE '%HUY_OT%'
BEGIN
 ALTER TABLE dbo.employee_requests DROP CONSTRAINT CK_requests_type;
 SET @sql=N'ALTER TABLE dbo.employee_requests WITH CHECK ADD CONSTRAINT CK_requests_type CHECK (('+@definition+N') OR request_type IN (''HUY_OT'',''OT_VUOT''))';EXEC sp_executesql @sql;
END;
SELECT @definition=definition FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.overtime_requests') AND name='CK_overtime_processing';
IF @definition IS NOT NULL AND @definition NOT LIKE '%DA_HUY%'
BEGIN
 ALTER TABLE dbo.overtime_requests DROP CONSTRAINT CK_overtime_processing;
 SET @sql=N'ALTER TABLE dbo.overtime_requests WITH CHECK ADD CONSTRAINT CK_overtime_processing CHECK (('+@definition+N') OR (status=''DA_HUY'' AND confirmed_by IS NULL AND confirmed_at IS NULL AND actual_start IS NULL AND actual_end IS NULL))';EXEC sp_executesql @sql;
END;
-- ADMIN manages OT, never registers personal OT. HR and EMPLOYEE register their own.
-- Existing grant rows are history-protected. ADMIN is denied registration by live role checks, regardless of a legacy grant.
-- Retain the existing owner/self-approval/immutable-decision protections. Only allow the
-- approved -> cancelled transition when a real pending owner cancellation request exists,
-- before work starts, with neither old nor new actual work/confirmation. The service then
-- closes that request and writes audit in the SAME transaction.
DECLARE @triggerDefinition nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('dbo.tr_overtime_no_self_approval'));
IF @triggerDefinition IS NOT NULL AND @triggerDefinition NOT LIKE '%OT_WORKSPACE_CANCELLATION%'
BEGIN
 IF CHARINDEX(N'i.status<>d.status OR i.requested_start',@triggerDefinition)=0
  THROW 51408,N'Unexpected overtime protection trigger; review cancellation transition before applying.',1;
 SET @triggerDefinition=REPLACE(@triggerDefinition,N'CREATE TRIGGER',N'ALTER TRIGGER');
 SET @triggerDefinition=REPLACE(@triggerDefinition,N'i.status<>d.status OR i.requested_start',N'
 (i.status<>d.status AND NOT ( /* OT_WORKSPACE_CANCELLATION */
   d.status=''DA_DUYET'' AND i.status=''DA_HUY''
   AND d.actual_start IS NULL AND d.actual_end IS NULL AND d.confirmed_by IS NULL AND d.confirmed_at IS NULL
   AND i.actual_start IS NULL AND i.actual_end IS NULL AND i.confirmed_by IS NULL AND i.confirmed_at IS NULL
   AND i.requested_start>DATEADD(hour,7,SYSUTCDATETIME())
   AND EXISTS(SELECT 1 FROM dbo.overtime_request_details detail JOIN dbo.employee_requests request ON request.request_id=detail.request_id
     WHERE detail.overtime_id=i.overtime_id AND request.employee_id=i.employee_id
       AND request.request_type=''HUY_OT'' AND request.status=''CHO_DUYET'')
 )) OR i.requested_start');
 EXEC sp_executesql @triggerDefinition;
END;
INSERT dbo.role_permissions(role_id,permission_id)
 SELECT r.role_id,p.permission_id FROM dbo.roles r CROSS JOIN dbo.permissions p WHERE r.role_code IN ('HR','EMPLOYEE') AND p.permission_code='OVERTIME_REQUEST_CREATE'
 AND NOT EXISTS(SELECT 1 FROM dbo.role_permissions x WHERE x.role_id=r.role_id AND x.permission_id=p.permission_id);
COMMIT;

