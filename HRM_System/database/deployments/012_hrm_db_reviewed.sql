-- hrm_db deployment authorized by user, with verified COPY_ONLY backup.
USE [hrm_db];
SET XACT_ABORT ON;
BEGIN TRANSACTION;
SELECT contract_id,employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status,row_version
 INTO #BeforeContracts012 FROM dbo.contracts WITH(TABLOCKX,HOLDLOCK);
SELECT salary_term_id,contract_id,effective_from,effective_to,base_salary,pay_basis,document_reference,row_version
 INTO #BeforeSalary012 FROM dbo.contract_salary_terms WITH(TABLOCKX,HOLDLOCK);
GO
-- MANUAL REVIEW REQUIRED. Fill mappings using actual IDs; never classify by display name.
-- Example only: INSERT @Types VALUES (123,'XAC_DINH',36);
-- Example only: INSERT @Probation VALUES (456,'CAO_DANG_TRO_LEN');
SET XACT_ABORT ON;
DECLARE @Types TABLE(id bigint PRIMARY KEY,kind varchar(20),months int NULL);
DECLARE @Probation TABLE(id bigint PRIMARY KEY,grp varchar(30));
DECLARE @ReviewedOver36 TABLE(id bigint PRIMARY KEY);
IF DB_NAME()<>'hrm_db' THROW 51910,N'This deployment targets hrm_db only.',1;
IF NOT EXISTS(SELECT 1 FROM dbo.contract_types WHERE contract_type_id=1 AND type_code='XAC_DINH_THOI_HAN')
 OR NOT EXISTS(SELECT 1 FROM dbo.contract_types WHERE contract_type_id=2 AND type_code='THU_VIEC')
 OR NOT EXISTS(SELECT 1 FROM dbo.contract_types WHERE contract_type_id=3 AND type_code='KHONG_XAC_DINH')
 OR NOT EXISTS(SELECT 1 FROM dbo.contract_types WHERE contract_type_id=4 AND type_code='CONG_TAC_VIEN')
 THROW 51911,N'Type IDs/codes changed; stop and review.',1;
INSERT @Types VALUES (1,'XAC_DINH',36),(2,'THU_VIEC',NULL),(3,'KHONG_XAC_DINH',NULL),(4,'CTV_CAN_RA_SOAT',NULL);
INSERT @ReviewedOver36 VALUES (5),(6),(12),(15),(21),(23),(26),(27);

BEGIN TRY
 BEGIN TRANSACTION;
 IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.contract_types') IS NULL
    OR OBJECT_ID('dbo.contracts') IS NULL OR OBJECT_ID('dbo.contract_salary_terms') IS NULL
    THROW 51800,N'Select HRM database with existing contract tables.',1;
 IF COL_LENGTH('dbo.contract_types','term_kind') IS NULL ALTER TABLE dbo.contract_types ADD term_kind varchar(20) NULL;
 IF COL_LENGTH('dbo.contract_types','max_months') IS NULL ALTER TABLE dbo.contract_types ADD max_months int NULL;
 IF COL_LENGTH('dbo.contracts','probation_group') IS NULL ALTER TABLE dbo.contracts ADD probation_group varchar(30) NULL;
 IF COL_LENGTH('dbo.contract_salary_terms','official_reference_salary') IS NULL ALTER TABLE dbo.contract_salary_terms ADD official_reference_salary decimal(19,4) NULL;
 IF COL_LENGTH('dbo.contract_salary_terms','official_reference_pay_basis') IS NULL ALTER TABLE dbo.contract_salary_terms ADD official_reference_pay_basis varchar(10) NULL;
 -- Temp tables allow the dynamic batch to read the reviewed mappings.
 SELECT * INTO #Types012 FROM @Types;
 SELECT * INTO #Probation012 FROM @Probation;
 SELECT * INTO #Legacy012 FROM @ReviewedOver36;
 EXEC(N'
 INSERT dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json)
 SELECT NULL,SYSUTCDATETIME(),''CONTRACT_TYPE_CLASSIFY'',''contract_types'',CONVERT(varchar(30),t.contract_type_id),
 N''Task 02: user-authorized reviewed mapping. CTV remains pending legal classification.'',
 (SELECT t.type_code AS typeCode,t.term_kind AS termKind,t.max_months AS maxMonths FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),
 (SELECT m.kind AS termKind,m.months AS maxMonths FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)
 FROM dbo.contract_types t JOIN #Types012 m ON m.id=t.contract_type_id WHERE t.term_kind IS NULL;
 INSERT dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json)
 SELECT NULL,SYSUTCDATETIME(),''CONTRACT_LEGACY_REVIEW'',''contracts'',CONVERT(varchar(30),c.contract_id),
 N''User authorized preserving legacy duration over 36 months; review later. New writes still validate limits.'',
 (SELECT c.start_date AS startDate,c.end_date AS endDate FOR JSON PATH,WITHOUT_ARRAY_WRAPPER),NULL
 FROM dbo.contracts c JOIN #Legacy012 old ON old.id=c.contract_id
 WHERE NOT EXISTS(SELECT 1 FROM dbo.audit_logs a WHERE a.target_table=''contracts'' AND a.target_record_key=CONVERT(varchar(30),c.contract_id) AND a.action=''CONTRACT_LEGACY_REVIEW'');
 UPDATE t SET term_kind=m.kind,max_months=m.months FROM dbo.contract_types t JOIN #Types012 m ON m.id=t.contract_type_id WHERE t.term_kind IS NULL;
 UPDATE c SET probation_group=m.grp FROM dbo.contracts c JOIN #Probation012 m ON m.id=c.contract_id WHERE c.probation_group IS NULL;
 IF EXISTS(SELECT 1 FROM dbo.contract_types WHERE term_kind IS NULL)
 BEGIN
   SELECT contract_type_id,type_code,type_name FROM dbo.contract_types WHERE term_kind IS NULL;
   THROW 51801,N''Missing reviewed type mappings; all migration changes rolled back.'',1;
 END;
 IF EXISTS(SELECT 1 FROM dbo.contracts c JOIN dbo.contract_types t ON t.contract_type_id=c.contract_type_id
   WHERE (t.term_kind=''KHONG_XAC_DINH'' AND c.end_date IS NOT NULL AND c.status<>''DA_KET_THUC''
     AND NOT EXISTS(SELECT 1 FROM dbo.audit_logs a WHERE a.target_table=''contracts''
       AND a.target_record_key=CONVERT(varchar(30),c.contract_id) AND a.action=''CONTRACT_TERMINATE''))
    OR (t.term_kind IN (''XAC_DINH'',''THU_VIEC'') AND c.end_date IS NULL)
    OR (t.term_kind=''XAC_DINH'' AND c.end_date>=DATEADD(month,t.max_months,c.start_date)
       AND NOT EXISTS(SELECT 1 FROM #Legacy012 old WHERE old.id=c.contract_id))
    OR (t.term_kind=''THU_VIEC'' AND c.probation_group IS NULL)
    OR (t.term_kind<>''THU_VIEC'' AND c.probation_group IS NOT NULL))
   THROW 51802,N''Legacy contracts conflict with reviewed type rules; review separately.'',1;
 ALTER TABLE dbo.contract_types ALTER COLUMN term_kind varchar(20) NOT NULL;
 IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name=''CK_contract_types_term_012'' AND parent_object_id=OBJECT_ID(''dbo.contract_types''))
   ALTER TABLE dbo.contract_types WITH CHECK ADD CONSTRAINT CK_contract_types_term_012 CHECK(
     (term_kind=''XAC_DINH'' AND max_months IS NOT NULL AND max_months BETWEEN 1 AND 36)
     OR (term_kind IN (''KHONG_XAC_DINH'',''THU_VIEC'',''CTV_CAN_RA_SOAT'') AND max_months IS NULL));
 IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name=''CK_contracts_probation_012'' AND parent_object_id=OBJECT_ID(''dbo.contracts''))
   ALTER TABLE dbo.contracts WITH CHECK ADD CONSTRAINT CK_contracts_probation_012 CHECK(probation_group IS NULL OR probation_group IN (''QUAN_LY_DN'',''CAO_DANG_TRO_LEN'',''TRUNG_CAP_NGHIEP_VU'',''CONG_VIEC_KHAC''));
 IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name=''CK_salary_reference_012'' AND parent_object_id=OBJECT_ID(''dbo.contract_salary_terms''))
   ALTER TABLE dbo.contract_salary_terms WITH CHECK ADD CONSTRAINT CK_salary_reference_012 CHECK(
    (official_reference_salary IS NULL AND official_reference_pay_basis IS NULL)
    OR (official_reference_salary IS NOT NULL AND official_reference_salary>0 AND official_reference_pay_basis IS NOT NULL AND official_reference_pay_basis IN (''THANG'',''NGAY'',''GIO'')));
 ALTER TABLE dbo.contract_types WITH CHECK CHECK CONSTRAINT CK_contract_types_term_012;
 ALTER TABLE dbo.contracts WITH CHECK CHECK CONSTRAINT CK_contracts_probation_012;
 ALTER TABLE dbo.contract_salary_terms WITH CHECK CHECK CONSTRAINT CK_salary_reference_012;
 ');
 DROP TABLE #Types012;
 DROP TABLE #Probation012;
 DROP TABLE #Legacy012;
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;

GO
IF XACT_STATE()<>1 THROW 51912,N'Migration did not leave a committable deployment transaction.',1;
IF EXISTS(SELECT * FROM #BeforeContracts012 EXCEPT SELECT contract_id,employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status,row_version FROM dbo.contracts)
 OR EXISTS(SELECT contract_id,employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status,row_version FROM dbo.contracts EXCEPT SELECT * FROM #BeforeContracts012)
 OR EXISTS(SELECT * FROM #BeforeSalary012 EXCEPT SELECT salary_term_id,contract_id,effective_from,effective_to,base_salary,pay_basis,document_reference,row_version FROM dbo.contract_salary_terms)
 OR EXISTS(SELECT salary_term_id,contract_id,effective_from,effective_to,base_salary,pay_basis,document_reference,row_version FROM dbo.contract_salary_terms EXCEPT SELECT * FROM #BeforeSalary012)
 BEGIN ROLLBACK; THROW 51913,N'Original contracts or salary terms changed unexpectedly.',1; END;
COMMIT;
DROP TABLE #BeforeContracts012;
DROP TABLE #BeforeSalary012;
SELECT DB_NAME() AS applied_database,COUNT(*) AS contracts_preserved FROM dbo.contracts;
SELECT contract_type_id,type_code,term_kind,max_months FROM dbo.contract_types ORDER BY contract_type_id;
