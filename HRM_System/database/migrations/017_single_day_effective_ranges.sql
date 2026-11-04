-- SQL Server. Coordinate with the team and select the intended HRM database first.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb')
        OR NOT EXISTS (SELECT 1 FROM sys.check_constraints
                       WHERE parent_object_id=OBJECT_ID('dbo.contract_salary_terms') AND name='CK_salary_terms_dates')
        OR NOT EXISTS (SELECT 1 FROM sys.check_constraints
                       WHERE parent_object_id=OBJECT_ID('dbo.employee_allowances') AND name='CK_allowances_dates')
        THROW 51970,N'Select the HRM database and verify both named date constraints.',1;

    ALTER TABLE dbo.contract_salary_terms DROP CONSTRAINT CK_salary_terms_dates;
    ALTER TABLE dbo.contract_salary_terms WITH CHECK ADD CONSTRAINT CK_salary_terms_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from);

    ALTER TABLE dbo.employee_allowances DROP CONSTRAINT CK_allowances_dates;
    ALTER TABLE dbo.employee_allowances WITH CHECK ADD CONSTRAINT CK_allowances_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from);
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    THROW;
END CATCH;
