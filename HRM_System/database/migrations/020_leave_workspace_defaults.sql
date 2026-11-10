-- Add catalog entries and defaults only when absent. Existing configured policies are retained.
SET XACT_ABORT ON; BEGIN TRAN;
DECLARE @actor bigint=(SELECT TOP(1) a.account_id FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id WHERE r.role_code='ADMIN' AND a.is_active=1 ORDER BY a.account_id);
IF @actor IS NULL THROW 51000,N'Active ADMIN required',1;
IF NOT EXISTS(SELECT 1 FROM dbo.leave_types WHERE type_code IN ('SICK','NGHI_OM')) INSERT dbo.leave_types(type_code,type_name,is_paid) VALUES('SICK',N'Nghỉ ốm',0);
IF NOT EXISTS(SELECT 1 FROM dbo.leave_types WHERE type_code IN ('MATERNITY','THAI_SAN')) INSERT dbo.leave_types(type_code,type_name,is_paid) VALUES('MATERNITY',N'Nghỉ thai sản',0);
INSERT dbo.g2_leave_policies(leave_type_id,consumes_entitlement,updated_by)
SELECT leave_type_id,0,@actor FROM dbo.leave_types t WHERE t.type_code IN ('SICK','NGHI_OM','MATERNITY','THAI_SAN') AND NOT EXISTS(SELECT 1 FROM dbo.g2_leave_policies p WHERE p.leave_type_id=t.leave_type_id);
INSERT dbo.personnel_leave_rules(leave_type_id,retroactive_days,minimum_notice_hours,minimum_service_months,evidence_required,updated_by)
SELECT leave_type_id,CASE WHEN type_code IN ('SICK','NGHI_OM') THEN 7 ELSE 0 END,0,0,1,@actor FROM dbo.leave_types t WHERE t.type_code IN ('SICK','NGHI_OM','MATERNITY','THAI_SAN') AND NOT EXISTS(SELECT 1 FROM dbo.personnel_leave_rules p WHERE p.leave_type_id=t.leave_type_id);
INSERT dbo.personnel_leave_accrual_policies(leave_type_id,annual_days,hours_per_day,seniority_extra,updated_by)
SELECT t.leave_type_id,12,8,0,@actor FROM dbo.leave_types t JOIN dbo.g2_leave_policies g ON g.leave_type_id=t.leave_type_id AND g.consumes_entitlement=1 WHERE t.type_code IN ('ANNUAL','PHEP_NAM') AND NOT EXISTS(SELECT 1 FROM dbo.personnel_leave_accrual_policies p WHERE p.leave_type_id=t.leave_type_id);
INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason) VALUES(@actor,'LEAVE_WORKSPACE_DEFAULTS','leave_types','SICK/MATERNITY',N'Bổ sung danh mục nghỉ ốm/thai sản, không trừ quỹ phép năm; giữ chính sách đã cấu hình');
COMMIT;
