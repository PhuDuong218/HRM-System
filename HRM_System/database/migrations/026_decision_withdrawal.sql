-- Preserve decisions and their audit trail when withdrawing a pending decision.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.employee_decisions') AND name='CK_decisions_approval')
 ALTER TABLE dbo.employee_decisions DROP CONSTRAINT CK_decisions_approval;
ALTER TABLE dbo.employee_decisions WITH CHECK ADD CONSTRAINT CK_decisions_approval CHECK(
 (approval_status IN ('CHO_DUYET','DA_THU_HOI') AND approved_by IS NULL AND approved_at IS NULL)
 OR (approval_status IN ('DA_DUYET','TU_CHOI') AND approved_by IS NOT NULL AND approved_at IS NOT NULL));
COMMIT;
