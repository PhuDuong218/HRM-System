-- SQLCMD mode. Run from the backend root with -v TargetDatabase="..." AllowMainDatabase="0".
-- A separate, explicitly reviewed data migration BEFORE schema migration 011.
-- Do not use this script as an example for real HR records without reviewing the assumptions.
:ON ERROR EXIT
USE [$(TargetDatabase)];
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME()='hrm_db' AND '$(AllowMainDatabase)'<>'1'
    THROW 51710,N'Live deployment disabled. Review test results before explicitly enabling hrm_db.',1;
IF DB_NAME() IN ('master','model','msdb','tempdb')
    THROW 51710,N'Not an HRM database.',1;

BEGIN TRANSACTION;
-- Kept until migration 011 and the post-check both succeed (including across GO).
CREATE TABLE #Review011(id bigint PRIMARY KEY,number varchar(50));
INSERT #Review011 VALUES (2,'QD-KL-B'),(4,'TEST-QD-HRM105'),(5,'TEST-QD-HRM106'),(7,'TEST-QD-HRM110');
CREATE TABLE #Changes011(id bigint PRIMARY KEY,before_json nvarchar(max));
DECLARE @correlation uniqueidentifier='FD409DE2-E66A-40B5-84DA-012851149011';

BEGIN TRY
    -- Match application write ordering: employee locks, then decision locks.
    SELECT e.employee_id AS locked_employee_id
    FROM dbo.employees e WITH(UPDLOCK,HOLDLOCK)
    WHERE e.employee_id IN (SELECT d.employee_id FROM dbo.employee_decisions d JOIN #Review011 r ON r.id=d.decision_id)
    ORDER BY e.employee_id;
    SELECT d.decision_id INTO #Locked011
    FROM dbo.employee_decisions d WITH(UPDLOCK,HOLDLOCK) JOIN #Review011 r ON r.id=d.decision_id;
    IF (SELECT COUNT(*) FROM #Locked011)<>4 OR EXISTS(
        SELECT 1 FROM #Review011 r JOIN dbo.employee_decisions d ON d.decision_id=r.id
        WHERE d.decision_number<>r.number OR d.decision_type<>'KY_LUAT')
        THROW 51711,N'Reviewed IDs/numbers/types no longer match. No automatic mapping.',1;

    -- Hold payroll range locks until COMMIT so a concurrent payroll close cannot race this guard.
    SELECT d.decision_id,d.decision_number,b.batch_id,p.period_id,p.period_start,p.period_end
    INTO #Closed011
    FROM dbo.employee_decisions d JOIN #Review011 r ON r.id=d.decision_id
    JOIN dbo.payroll_details x WITH(HOLDLOCK) ON x.employee_id=d.employee_id
    JOIN dbo.payroll_batches b WITH(HOLDLOCK) ON b.batch_id=x.batch_id AND b.status='DA_CHOT'
    JOIN dbo.payroll_periods p WITH(HOLDLOCK) ON p.period_id=b.period_id
        AND p.period_start<=COALESCE(d.apply_date,d.issued_date)
        AND p.period_end>=COALESCE(d.apply_date,d.issued_date);
    SELECT d.decision_id,d.decision_number,d.decision_type,d.approval_status,d.payroll_effect,
        d.amount,d.apply_date,d.issued_date,d.content,
        CONVERT(bit,CASE WHEN EXISTS(SELECT 1 FROM #Closed011 c WHERE c.decision_id=d.decision_id) THEN 1 ELSE 0 END) AS in_closed_period
    FROM dbo.employee_decisions d JOIN #Review011 r ON r.id=d.decision_id ORDER BY d.decision_id;
    IF EXISTS(SELECT 1 FROM #Closed011)
    BEGIN
        SELECT N'DA_VAO_LUONG_CHOT_BAO_PHUONG' AS review_status,* FROM #Closed011;
        THROW 51712,N'Closed payroll history: no decisions changed. Ask Phuong for an adjustment plan.',1;
    END;
    IF EXISTS(SELECT 1 FROM dbo.employee_decisions d JOIN #Review011 r ON r.id=d.decision_id
        WHERE d.content COLLATE Latin1_General_100_CI_AI LIKE N'%keo dai%'
           OR d.content COLLATE Latin1_General_100_CI_AI LIKE N'%cach chuc%'
           OR d.content COLLATE Latin1_General_100_CI_AI LIKE N'%sa thai%')
    BEGIN
        SELECT d.decision_id,d.decision_number,d.content FROM dbo.employee_decisions d JOIN #Review011 r ON r.id=d.decision_id;
        THROW 51713,N'Content may specify another discipline form. Stop for manual review.',1;
    END;

    IF COL_LENGTH('dbo.employee_decisions','discipline_form') IS NULL ALTER TABLE dbo.employee_decisions ADD discipline_form varchar(30) NULL;
    IF COL_LENGTH('dbo.employee_decisions','violation_date') IS NULL ALTER TABLE dbo.employee_decisions ADD violation_date date NULL;
    IF COL_LENGTH('dbo.employee_decisions','salary_increase_delay_months') IS NULL ALTER TABLE dbo.employee_decisions ADD salary_increase_delay_months int NULL;
    EXEC sys.sp_executesql N'
        DECLARE @correlation uniqueidentifier=''FD409DE2-E66A-40B5-84DA-012851149011'';
        SELECT d.decision_id,(SELECT d.* FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER) AS current_json,
            a.action AS last_action,a.after_data_json
        INTO #State011
        FROM dbo.employee_decisions d JOIN #Review011 r ON r.id=d.decision_id
        OUTER APPLY(SELECT TOP(1) action,after_data_json FROM dbo.audit_logs a
            WHERE a.correlation_id=@correlation AND a.target_table=''employee_decisions''
              AND a.target_record_key=CONVERT(nvarchar(200),d.decision_id)
              AND a.action IN (''DECISION_LEGACY_NORMALIZE_011'',''DECISION_LEGACY_ROLLBACK_011'') ORDER BY audit_id DESC) a;
        IF EXISTS(SELECT 1 FROM #State011 WHERE last_action=''DECISION_LEGACY_NORMALIZE_011''
            AND CONVERT(varbinary(max),current_json)<>CONVERT(varbinary(max),after_data_json))
            THROW 51714,N''A previously normalized decision changed afterwards. Do not overwrite it.'',1;
        INSERT #Changes011 SELECT decision_id,current_json FROM #State011
            WHERE last_action IS NULL OR last_action=''DECISION_LEGACY_ROLLBACK_011'';
        IF EXISTS(SELECT 1 FROM dbo.employee_decisions d JOIN #Changes011 c ON c.id=d.decision_id
            WHERE d.discipline_form IS NOT NULL OR d.violation_date IS NOT NULL OR d.salary_increase_delay_months IS NOT NULL)
            THROW 51715,N''Existing discipline metadata must be reviewed; assumptions cannot overwrite it.'',1;
        IF EXISTS(SELECT 1 FROM dbo.employee_decisions d JOIN #Changes011 c ON c.id=d.decision_id
            WHERE (d.decision_id IN (2,4) AND (d.payroll_effect<>''NONE'' OR d.amount IS NOT NULL OR d.apply_date IS NOT NULL))
               OR (d.decision_id=5 AND (d.approval_status<>''DA_DUYET'' OR d.payroll_effect<>''DEDUCT'' OR d.amount<>150000 OR d.apply_date<>''20261001''))
               OR (d.decision_id=7 AND (d.approval_status<>''TU_CHOI'' OR d.payroll_effect<>''DEDUCT'' OR d.amount<>500000 OR d.apply_date<>''20261102'')))
            THROW 51716,N''Legacy effects/status/amount/date changed since review.'',1;
        IF EXISTS(SELECT 1 FROM dbo.employee_decisions d JOIN #Changes011 c ON c.id=d.decision_id
            WHERE LEN(d.content)+300>COL_LENGTH(''dbo.employee_decisions'',''content'')/2)
            THROW 51717,N''Insufficient content space for the legacy money/date note.'',1;
        UPDATE d SET discipline_form=''KHIEN_TRACH'',violation_date=d.issued_date,salary_increase_delay_months=NULL,
            content=CONCAT(d.content,NCHAR(13),NCHAR(10),N''[011: lưu thông tin cũ; payroll_effect='',d.payroll_effect,
                N''; amount='',COALESCE(CONVERT(nvarchar(40),d.amount),N''NULL''),
                N''; apply_date='',COALESCE(CONVERT(nvarchar(10),d.apply_date,23),N''NULL''),N'']''),
            payroll_effect=''NONE'',amount=NULL,apply_date=NULL,updated_at=SYSUTCDATETIME()
        FROM dbo.employee_decisions d JOIN #Changes011 c ON c.id=d.decision_id;
        INSERT dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json,correlation_id)
        SELECT NULL,SYSUTCDATETIME(),''DECISION_LEGACY_NORMALIZE_011'',''employee_decisions'',CONVERT(nvarchar(200),d.decision_id),
            N''chuẩn hóa dữ liệu cũ theo BLLĐ 2019 Điều 127. Dữ liệu cũ chưa có hình thức và ngày vi phạm; tạm gán KHIEN_TRACH và violation_date = issued_date theo giả định, cần xác nhận. Không dùng ngày giả định để tính thời hiệu xử lý kỷ luật.'',
            c.before_json,(SELECT d.* FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER),@correlation
        FROM dbo.employee_decisions d JOIN #Changes011 c ON c.id=d.decision_id;
    ';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
GO
-- The existing schema migration is still the single source of the business CHECK.
:r database/migrations/011_decision_rules.sql
GO
BEGIN TRY
    IF @@TRANCOUNT<>1 THROW 51718,N'Unexpected transaction state; deployment not committed.',1;
    IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_decisions')
        AND name='CK_decisions_business_011' AND is_disabled=0 AND is_not_trusted=0)
        THROW 51719,N'Business CHECK must be enabled and trusted.',1;
    SELECT d.decision_id,d.decision_number,d.decision_type,d.approval_status,d.payroll_effect,d.amount,d.apply_date,
        d.discipline_form,d.violation_date,d.salary_increase_delay_months,d.content
    FROM dbo.employee_decisions d JOIN #Review011 r ON r.id=d.decision_id ORDER BY d.decision_id;
    SELECT COUNT(*) AS changed_this_run FROM #Changes011;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
