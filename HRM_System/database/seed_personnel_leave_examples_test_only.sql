SET XACT_ABORT ON;
IF DB_NAME()<>'hrm_test' THROW 52010,N'Dữ liệu mẫu chỉ dành cho hrm_test.',1;
BEGIN TRANSACTION;
DECLARE @actor bigint=(SELECT TOP(1) a.account_id FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id WHERE r.role_code='ADMIN' ORDER BY a.account_id);
IF @actor IS NULL THROW 52011,N'Cần tài khoản ADMIN test sẵn có.',1;
DECLARE @types TABLE(code varchar(50),name nvarchar(200),paid bit,consumes bit,retro int,evidence bit);
INSERT @types VALUES('QA_ANNUAL',N'QA - Phép năm',1,1,0,0),('QA_UNPAID',N'QA - Nghỉ không lương',0,0,7,0),('QA_EVIDENCE',N'QA - Nghỉ cần minh chứng',0,0,7,1);
INSERT dbo.leave_types(type_code,type_name,is_paid,is_active,created_by)
 SELECT code,name,paid,1,@actor FROM @types t WHERE NOT EXISTS(SELECT 1 FROM dbo.leave_types l WHERE l.type_code=t.code);
INSERT dbo.g2_leave_policies(leave_type_id,consumes_entitlement,updated_by)
 SELECT l.leave_type_id,t.consumes,@actor FROM @types t JOIN dbo.leave_types l ON l.type_code=t.code WHERE NOT EXISTS(SELECT 1 FROM dbo.g2_leave_policies p WHERE p.leave_type_id=l.leave_type_id);
INSERT dbo.personnel_leave_rules(leave_type_id,retroactive_days,minimum_notice_hours,updated_by,evidence_required)
 SELECT l.leave_type_id,t.retro,0,@actor,t.evidence FROM @types t JOIN dbo.leave_types l ON l.type_code=t.code WHERE NOT EXISTS(SELECT 1 FROM dbo.personnel_leave_rules p WHERE p.leave_type_id=l.leave_type_id);
COMMIT;
