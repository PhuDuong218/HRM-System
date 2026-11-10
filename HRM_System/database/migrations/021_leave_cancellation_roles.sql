-- Align withdrawal protection with owner/HR/head cancellation rules.
-- Preserves immutable withdrawal history; no table/column changes.
SET XACT_ABORT ON;
GO
CREATE OR ALTER TRIGGER dbo.tr_withdrawal_pending_owner ON dbo.employee_request_withdrawals AFTER INSERT AS
BEGIN
 SET NOCOUNT ON;
 DECLARE @now datetime2=CONVERT(datetime2,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time');
 IF EXISTS(
  SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id
  JOIN dbo.accounts actor ON actor.account_id=i.withdrawn_by
  JOIN dbo.employees actor_employee ON actor_employee.employee_id=actor.employee_id
  LEFT JOIN dbo.leave_request_details l ON l.request_id=r.request_id
  WHERE NOT(actor.is_active=1 AND actor_employee.employment_status='DANG_LAM' AND (
    -- Retain the existing owner-only withdrawal flow for resignation requests.
    (r.request_type='NGHI_VIEC' AND r.status='CHO_DUYET' AND actor.employee_id=r.employee_id)
    OR (r.request_type='NGHI_PHEP' AND r.status IN ('CHO_DUYET','DA_DUYET') AND l.leave_start IS NOT NULL AND l.leave_start>@now AND (
      actor.employee_id=r.employee_id
      OR EXISTS(
        SELECT 1 FROM dbo.roles role JOIN dbo.role_permissions rp ON rp.role_id=role.role_id
        JOIN dbo.permissions perm ON perm.permission_id=rp.permission_id
        WHERE role.role_id=actor.role_id AND perm.permission_code='REQUEST_APPROVE'
        AND (role.role_code='ADMIN' OR (role.role_code='HR' AND EXISTS(
          SELECT 1 FROM dbo.employee_assignments member JOIN dbo.hr_department_scopes scope ON scope.department_id=member.department_id
          WHERE member.employee_id=r.employee_id AND member.effective_from<=CONVERT(date,@now)
          AND (member.effective_to IS NULL OR member.effective_to>CONVERT(date,@now))
          AND scope.hr_account_id=actor.account_id AND scope.revoked_at IS NULL
          AND scope.effective_from<=SYSUTCDATETIME() AND (scope.effective_to IS NULL OR scope.effective_to>SYSUTCDATETIME())
        )))
      )
      OR (r.status='CHO_DUYET' AND EXISTS(
        SELECT 1 FROM dbo.employee_assignments leader JOIN dbo.positions position ON position.position_id=leader.position_id
        JOIN dbo.employee_assignments member ON member.department_id=leader.department_id
        WHERE leader.employee_id=actor.employee_id AND position.position_code='TRUONG_PHONG'
        AND leader.effective_from<=CONVERT(date,@now) AND (leader.effective_to IS NULL OR leader.effective_to>CONVERT(date,@now))
        AND member.employee_id=r.employee_id AND member.effective_from<=CONVERT(date,@now)
        AND (member.effective_to IS NULL OR member.effective_to>CONVERT(date,@now))
      ))
    )))
  )
 ) THROW 51304,N'Không có quyền hủy, đơn không còn hợp lệ hoặc thời gian nghỉ đã bắt đầu.',1;
END;
GO
