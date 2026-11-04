-- Safe rollback: stop rather than discard populated new metadata.
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF DB_NAME() IN ('master','model','msdb','tempdb') OR OBJECT_ID('dbo.contract_types') IS NULL THROW 51800,N'Select HRM database.',1;
 IF COL_LENGTH('dbo.contract_types','term_kind') IS NOT NULL
   EXEC(N'IF EXISTS(SELECT 1 FROM dbo.contract_types WHERE term_kind IS NOT NULL OR max_months IS NOT NULL) THROW 51803,N''Reviewed classification exists. Rollback needs a separate preservation plan.'',1;');
 IF COL_LENGTH('dbo.contracts','probation_group') IS NOT NULL
   EXEC(N'IF EXISTS(SELECT 1 FROM dbo.contracts WHERE probation_group IS NOT NULL) THROW 51803,N''Probation metadata exists; rollback stopped.'',1;');
 IF COL_LENGTH('dbo.contract_salary_terms','official_reference_salary') IS NOT NULL
   EXEC(N'IF EXISTS(SELECT 1 FROM dbo.contract_salary_terms WHERE official_reference_salary IS NOT NULL OR official_reference_pay_basis IS NOT NULL) THROW 51803,N''Salary references exist; rollback stopped.'',1;');
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_salary_reference_012' AND parent_object_id=OBJECT_ID('dbo.contract_salary_terms')) ALTER TABLE dbo.contract_salary_terms DROP CONSTRAINT CK_salary_reference_012;
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_contracts_probation_012' AND parent_object_id=OBJECT_ID('dbo.contracts')) ALTER TABLE dbo.contracts DROP CONSTRAINT CK_contracts_probation_012;
 IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE name='CK_contract_types_term_012' AND parent_object_id=OBJECT_ID('dbo.contract_types')) ALTER TABLE dbo.contract_types DROP CONSTRAINT CK_contract_types_term_012;
 IF COL_LENGTH('dbo.contract_salary_terms','official_reference_salary') IS NOT NULL ALTER TABLE dbo.contract_salary_terms DROP COLUMN official_reference_salary;
 IF COL_LENGTH('dbo.contract_salary_terms','official_reference_pay_basis') IS NOT NULL ALTER TABLE dbo.contract_salary_terms DROP COLUMN official_reference_pay_basis;
 IF COL_LENGTH('dbo.contracts','probation_group') IS NOT NULL ALTER TABLE dbo.contracts DROP COLUMN probation_group;
 IF COL_LENGTH('dbo.contract_types','max_months') IS NOT NULL ALTER TABLE dbo.contract_types DROP COLUMN max_months;
 IF COL_LENGTH('dbo.contract_types','term_kind') IS NOT NULL ALTER TABLE dbo.contract_types DROP COLUMN term_kind;
 COMMIT;
END TRY
BEGIN CATCH
 IF @@TRANCOUNT>0 ROLLBACK;
 THROW;
END CATCH;
