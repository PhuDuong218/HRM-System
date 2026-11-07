SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('dbo.employee_request_withdrawals') IS NULL THROW 52000,N'Chạy migration 003 trước.',1;
EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_withdrawal_pending_owner ON dbo.employee_request_withdrawals AFTER INSERT AS
 BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id JOIN dbo.accounts a ON a.account_id=i.withdrawn_by
  WHERE NOT ((r.status=''CHO_DUYET'' AND a.employee_id=r.employee_id)
   OR (r.status=''DA_DUYET'' AND r.request_type=''NGHI_PHEP'' AND a.is_active=1
    AND EXISTS(SELECT 1 FROM dbo.roles role JOIN dbo.role_permissions rp ON rp.role_id=role.role_id JOIN dbo.permissions p ON p.permission_id=rp.permission_id WHERE role.role_id=a.role_id AND role.role_code IN (''ADMIN'',''HR'') AND p.permission_code=''REQUEST_APPROVE'')
    AND EXISTS(SELECT 1 FROM dbo.leave_request_details l WHERE l.request_id=r.request_id AND l.leave_start>CONVERT(datetime2,SYSDATETIMEOFFSET() AT TIME ZONE ''SE Asia Standard Time'')))))
  THROW 51304,N''Chỉ chủ đơn rút đơn chờ hoặc người có quyền hủy phép đã duyệt chưa bắt đầu.'',1;
 END;');
EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_request_withdrawal_guard ON dbo.employee_requests AFTER UPDATE AS
 BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.request_id=i.request_id JOIN dbo.employee_request_withdrawals w ON w.request_id=i.request_id WHERE i.status<>d.status)
  THROW 51301,N''Đơn đã rút/hủy không được đổi trạng thái.'',1;
 END;');
/* Preserve immutable allocations; cancelled requests no longer freeze the planned schedule. */
EXEC(N'CREATE OR ALTER TRIGGER dbo.tr_g2_schedule_leave ON dbo.work_schedules AFTER INSERT,UPDATE,DELETE AS
 BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.schedule_id=d.schedule_id
  JOIN dbo.g2_leave_allocations a ON a.schedule_id=d.schedule_id
  JOIN dbo.employee_requests r ON r.request_id=a.request_id AND r.status=''DA_DUYET''
  WHERE NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)
   AND (i.schedule_id IS NULL OR i.employee_id<>d.employee_id OR i.planned_start<>d.planned_start
   OR i.planned_end<>d.planned_end OR i.planned_break_minutes<>d.planned_break_minutes OR i.work_date<>d.work_date))
  THROW 51203,N''Lịch đã có phép duyệt còn hiệu lực; cần quy trình điều chỉnh.'',1;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.schedule_id=i.schedule_id
  JOIN dbo.employee_requests r ON r.employee_id=i.employee_id AND r.status=''DA_DUYET''
  JOIN dbo.leave_request_details l ON l.request_id=r.request_id
  WHERE NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)
   AND (d.schedule_id IS NULL OR i.employee_id<>d.employee_id OR i.planned_start<>d.planned_start OR i.planned_end<>d.planned_end)
   AND i.planned_start<l.leave_end AND i.planned_end>l.leave_start)
  THROW 51204,N''Lịch mới/thay đổi trùng phép đã duyệt còn hiệu lực.'',1;
 END;');
COMMIT;
