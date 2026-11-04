-- Undo ONLY this deployment, preserving all audit rows. SQLCMD mode, backend root.
-- Restores legacy invalid values AND removes schema 011 atomically.
-- Do not run the new application after rollback: its entity requires the three new columns.
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
IF DB_NAME()='hrm_db' AND '$(AllowMainDatabase)'<>'1' THROW 51710,N'Live rollback disabled.',1;
IF DB_NAME() IN ('master','model','msdb','tempdb') THROW 51710,N'Not an HRM database.',1;
BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @correlation uniqueidentifier='FD409DE2-E66A-40B5-84DA-012851149011';
    SELECT a.* INTO #Undo011 FROM dbo.audit_logs a WITH(UPDLOCK,HOLDLOCK)
    WHERE a.correlation_id=@correlation AND a.target_table='employee_decisions'
      AND a.action='DECISION_LEGACY_NORMALIZE_011'
      AND NOT EXISTS(SELECT 1 FROM dbo.audit_logs newer WHERE newer.correlation_id=a.correlation_id
        AND newer.target_table=a.target_table AND newer.target_record_key=a.target_record_key
        AND newer.action IN ('DECISION_LEGACY_NORMALIZE_011','DECISION_LEGACY_ROLLBACK_011') AND newer.audit_id>a.audit_id);
    IF EXISTS(SELECT 1 FROM #Undo011)
    BEGIN
        IF (SELECT COUNT(*) FROM #Undo011)<>4 THROW 51720,N'Incomplete audit group; rollback stopped.',1;
        SELECT e.employee_id AS locked_employee_id FROM dbo.employees e WITH(UPDLOCK,HOLDLOCK)
        WHERE EXISTS(SELECT 1 FROM #Undo011 a WHERE TRY_CONVERT(bigint,JSON_VALUE(a.before_data_json,'$.employee_id'))=e.employee_id)
        ORDER BY e.employee_id;
        IF EXISTS(SELECT 1 FROM #Undo011 a LEFT JOIN dbo.employee_decisions d WITH(UPDLOCK,HOLDLOCK)
            ON CONVERT(nvarchar(200),d.decision_id)=a.target_record_key
            WHERE d.decision_id IS NULL OR CONVERT(varbinary(max),(SELECT d.* FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER))
                <>CONVERT(varbinary(max),a.after_data_json))
            THROW 51721,N'A normalized row changed or was removed after deployment. Rollback stopped.',1;
        IF EXISTS(SELECT 1 FROM #Undo011 a
            JOIN dbo.payroll_details x WITH(HOLDLOCK) ON x.employee_id=TRY_CONVERT(bigint,JSON_VALUE(a.before_data_json,'$.employee_id'))
            JOIN dbo.payroll_batches b WITH(HOLDLOCK) ON b.batch_id=x.batch_id AND b.status='DA_CHOT'
            JOIN dbo.payroll_periods p WITH(HOLDLOCK) ON p.period_id=b.period_id
            WHERE p.period_start<=COALESCE(TRY_CONVERT(date,JSON_VALUE(a.before_data_json,'$.apply_date')),TRY_CONVERT(date,JSON_VALUE(a.before_data_json,'$.issued_date')))
              AND p.period_end>=COALESCE(TRY_CONVERT(date,JSON_VALUE(a.before_data_json,'$.apply_date')),TRY_CONVERT(date,JSON_VALUE(a.before_data_json,'$.issued_date'))))
            THROW 51722,N'Closed payroll history now exists. Rollback stopped; contact Phuong.',1;
        IF COL_LENGTH('dbo.employee_decisions','discipline_form') IS NULL THROW 51723,N'Schema changed unexpectedly.',1;
        IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_decisions') AND name='CK_decisions_business_011')
            ALTER TABLE dbo.employee_decisions DROP CONSTRAINT CK_decisions_business_011;
        -- Other new decisions/metadata are checked by the standard rollback, within this outer transaction.
        EXEC(N'UPDATE d SET payroll_effect=JSON_VALUE(a.before_data_json,''$.payroll_effect''),
            amount=TRY_CONVERT(decimal(19,4),JSON_VALUE(a.before_data_json,''$.amount'')),
            apply_date=TRY_CONVERT(date,JSON_VALUE(a.before_data_json,''$.apply_date'')),
            content=JSON_VALUE(a.before_data_json,''$.content''),
            updated_at=TRY_CONVERT(datetime2,JSON_VALUE(a.before_data_json,''$.updated_at'')),
            discipline_form=NULL,violation_date=NULL,salary_increase_delay_months=NULL
            FROM dbo.employee_decisions d JOIN #Undo011 a ON CONVERT(nvarchar(200),d.decision_id)=a.target_record_key;');
        INSERT dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json,correlation_id)
        SELECT NULL,SYSUTCDATETIME(),'DECISION_LEGACY_ROLLBACK_011','employee_decisions',a.target_record_key,
            N'Hoàn tác chuẩn hóa dữ liệu cũ nhiệm vụ 1 theo snapshot audit; giữ toàn bộ lịch sử audit. Schema 011 được gỡ trong cùng transaction.',
            a.after_data_json,(SELECT d.* FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER),@correlation
        FROM dbo.employee_decisions d JOIN #Undo011 a ON CONVERT(nvarchar(200),d.decision_id)=a.target_record_key;
    END;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT>0 ROLLBACK;
    THROW;
END CATCH;
GO
:r database/migrations/011_decision_rules.rollback.sql
GO
IF @@TRANCOUNT<>1 THROW 51718,N'Unexpected transaction state.',1;
COMMIT;
SELECT decision_id,decision_number,decision_type,approval_status,payroll_effect,amount,apply_date,content
FROM dbo.employee_decisions WHERE decision_id IN (2,4,5,7) ORDER BY decision_id;
