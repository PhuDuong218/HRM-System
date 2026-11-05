:ON ERROR EXIT
:setvar DatabaseName "hrm_center_full"
USE [$(DatabaseName)];
GO
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER TRIGGER dbo.[tr_employee_qualifications_no_delete] ON dbo.[employee_qualifications] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_payslips_no_delete] ON dbo.[payslips] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_audit_logs_append_only ON dbo.audit_logs AFTER UPDATE AS
        BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM inserted)
            THROW 51002, N'Nhật ký chỉ được thêm mới.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_leave_details_type ON dbo.leave_request_details AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id
            WHERE r.request_type <> 'NGHI_PHEP')
            THROW 51003, N'Chi tiết phép sai loại đơn.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_resignation_details_type ON dbo.resignation_request_details AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id
            WHERE r.request_type <> 'NGHI_VIEC')
            THROW 51004, N'Chi tiết nghỉ việc sai loại đơn.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_requests_processing ON dbo.employee_requests AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.request_id=i.request_id
            WHERE d.status <> 'CHO_DUYET' OR i.request_type <> d.request_type OR i.employee_id <> d.employee_id)
            THROW 51005, N'Không đổi chủ/loại đơn hoặc sửa đơn đã xử lý.', 1;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.accounts a ON a.account_id=i.processed_by
            WHERE a.employee_id=i.employee_id)
            THROW 51006, N'Không tự duyệt đơn.', 1;
        IF EXISTS(SELECT 1 FROM inserted i WHERE i.status <> 'CHO_DUYET' AND
            ((i.request_type='NGHI_PHEP' AND NOT EXISTS(SELECT 1 FROM dbo.leave_request_details x WHERE x.request_id=i.request_id))
            OR (i.request_type='NGHI_VIEC' AND NOT EXISTS(SELECT 1 FROM dbo.resignation_request_details x
                WHERE x.request_id=i.request_id AND (i.status<>'DA_DUYET' OR x.approved_last_working_date IS NOT NULL)))))
            THROW 51007, N'Đơn xử lý thiếu chi tiết hoặc ngày nghỉ được chấp thuận.', 1;
        END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_leave_request_details_processed] ON dbo.[leave_request_details] AFTER INSERT, UPDATE AS
            BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id WHERE r.status<>'CHO_DUYET')
                THROW 51008, N'Không sửa chi tiết đơn đã xử lý.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_resignation_request_details_processed] ON dbo.[resignation_request_details] AFTER INSERT, UPDATE AS
            BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id WHERE r.status<>'CHO_DUYET')
                THROW 51008, N'Không sửa chi tiết đơn đã xử lý.', 1; END;
GO

CREATE   TRIGGER dbo.tr_overtime_no_self_approval
ON dbo.overtime_requests
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN dbo.accounts a ON a.account_id = i.processed_by
        WHERE a.employee_id = i.employee_id
    )
        THROW 51009, N'Không tự duyệt tăng ca.', 1;

    IF EXISTS (
        SELECT 1
        FROM inserted i JOIN deleted d ON d.overtime_id = i.overtime_id
        WHERE
            i.employee_id <> d.employee_id
            OR (
                d.status <> 'CHO_DUYET'
                AND (
                    -- Chỉ cho phép DA_DUYET -> DA_HUY khi mọi trường nghiệp vụ khác giữ nguyên
                    NOT (
                        d.status = 'DA_DUYET' AND i.status = 'DA_HUY'
                        AND i.requested_start = d.requested_start
                        AND i.requested_end = d.requested_end
                        AND i.reason = d.reason
                        AND i.processed_by = d.processed_by
                        AND i.processed_at = d.processed_at
                        AND ISNULL(i.rejection_reason, N'') = ISNULL(d.rejection_reason, N'')
                        AND d.actual_start IS NULL AND d.actual_end IS NULL
                        AND d.actual_break_minutes = 0
                        AND d.confirmed_by IS NULL AND d.confirmed_at IS NULL
                        AND i.actual_start IS NULL AND i.actual_end IS NULL
                        AND i.actual_break_minutes = 0
                        AND i.confirmed_by IS NULL AND i.confirmed_at IS NULL
                    )
                    AND (
                        i.status <> d.status OR i.requested_start <> d.requested_start
                        OR i.requested_end <> d.requested_end OR i.reason <> d.reason
                        OR ISNULL(i.processed_by, -1) <> ISNULL(d.processed_by, -1)
                        OR ISNULL(i.processed_at, '19000101') <> ISNULL(d.processed_at, '19000101')
                        OR ISNULL(i.rejection_reason, N'') <> ISNULL(d.rejection_reason, N'')
                    )
                )
            )
    ) THROW 51010, N'Không đổi chủ hoặc kết quả duyệt tăng ca đã xử lý.', 1;
END

GO
CREATE OR ALTER TRIGGER dbo.tr_contracts_keep_history ON dbo.contracts AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.contract_id=i.contract_id
            WHERE i.employee_id<>d.employee_id OR i.contract_type_id<>d.contract_type_id
            OR i.contract_number<>d.contract_number OR i.signed_date<>d.signed_date OR i.start_date<>d.start_date
            OR ISNULL(i.end_date,'99991231')<>ISNULL(d.end_date,'99991231'))
            THROW 51011, N'Giữ nội dung hợp đồng đã ký; tạo hợp đồng/phụ lục mới.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_salary_terms_keep_history ON dbo.contract_salary_terms AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.salary_term_id=i.salary_term_id
            WHERE i.contract_id<>d.contract_id OR i.effective_from<>d.effective_from OR i.base_salary<>d.base_salary
            OR i.pay_basis<>d.pay_basis OR ISNULL(i.document_reference,N'')<>ISNULL(d.document_reference,N'')
            OR (d.effective_to IS NOT NULL AND ISNULL(i.effective_to,'99991231')<>d.effective_to))
            THROW 51012, N'Không ghi đè điều kiện lương cũ; tạo điều kiện mới.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_payroll_details_locked ON dbo.payroll_details AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM (SELECT batch_id FROM inserted UNION SELECT batch_id FROM deleted) x
            JOIN dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK) ON b.batch_id=x.batch_id WHERE b.status='DA_CHOT')
            THROW 51013, N'Không sửa kết quả nhân viên của bảng lương đã chốt.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_payroll_lines_locked] ON dbo.[payroll_lines] AFTER INSERT, UPDATE AS
            BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM (SELECT payroll_detail_id FROM inserted UNION SELECT payroll_detail_id FROM deleted) x
                JOIN dbo.payroll_details d ON d.payroll_detail_id=x.payroll_detail_id
                JOIN dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK) ON b.batch_id=d.batch_id WHERE b.status='DA_CHOT')
                THROW 51014, N'Không sửa dòng tiền/căn cứ của bảng lương đã chốt.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_payroll_snapshots_locked] ON dbo.[payroll_snapshots] AFTER INSERT, UPDATE AS
            BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM (SELECT payroll_detail_id FROM inserted UNION SELECT payroll_detail_id FROM deleted) x
                JOIN dbo.payroll_details d ON d.payroll_detail_id=x.payroll_detail_id
                JOIN dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK) ON b.batch_id=d.batch_id WHERE b.status='DA_CHOT')
                THROW 51014, N'Không sửa dòng tiền/căn cứ của bảng lương đã chốt.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_batches_lock_validation ON dbo.payroll_batches AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.batch_id=i.batch_id
            WHERE d.batch_id IS NULL AND i.status<>'NHAP')
            THROW 51015, N'Tạo bảng lương nháp trước khi chốt.', 1;
        -- Bản chốt chỉ được công bố một lần; không đổi tiền, kỳ, version, căn cứ.
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.batch_id=i.batch_id WHERE d.status='DA_CHOT'
            AND (i.status<>d.status OR i.period_id<>d.period_id OR i.version_no<>d.version_no
            OR ISNULL(i.previous_batch_id,0)<>ISNULL(d.previous_batch_id,0)
            OR ISNULL(i.adjustment_reason,N'')<>ISNULL(d.adjustment_reason,N'')
            OR i.locked_by<>d.locked_by OR i.locked_at<>d.locked_at
            OR d.published_at IS NOT NULL))
            THROW 51016, N'Bảng lương đã chốt bất biến; tạo phiên bản điều chỉnh.', 1;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.payroll_batches p ON p.batch_id=i.previous_batch_id
            WHERE p.status<>'DA_CHOT' OR p.version_no>=i.version_no)
            THROW 51017, N'Phiên bản trước phải đã chốt và có số phiên bản nhỏ hơn.', 1;
        IF EXISTS(SELECT 1 FROM inserted i WHERE i.status='DA_CHOT' AND
            (NOT EXISTS(SELECT 1 FROM dbo.payroll_details d WHERE d.batch_id=i.batch_id)
            OR EXISTS(SELECT 1 FROM dbo.payroll_details d WHERE d.batch_id=i.batch_id AND
                (NOT EXISTS(SELECT 1 FROM dbo.payroll_lines l WHERE l.payroll_detail_id=d.payroll_detail_id)
                OR NOT EXISTS(SELECT 1 FROM dbo.payroll_snapshots s WHERE s.payroll_detail_id=d.payroll_detail_id)))))
            THROW 51018, N'Không chốt bảng lương rỗng hoặc thiếu dòng tiền/snapshot.', 1;
        END;
GO
CREATE OR ALTER TRIGGER dbo.tr_payslips_locked ON dbo.payslips AFTER INSERT, UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.payroll_details d ON d.payroll_detail_id=i.payroll_detail_id
            JOIN dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK) ON b.batch_id=d.batch_id WHERE b.status<>'DA_CHOT')
            THROW 51019, N'Chỉ phát hành phiếu từ bảng lương đã chốt.', 1;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.payslip_id=i.payslip_id
            WHERE i.payroll_detail_id<>d.payroll_detail_id OR i.payslip_number<>d.payslip_number OR i.issued_at<>d.issued_at)
            THROW 51020, N'Không đổi kết quả gắn với phiếu lương đã phát hành.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_payroll_periods_locked ON dbo.payroll_periods AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
        IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.period_id=i.period_id
            JOIN dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK) ON b.period_id=i.period_id
            WHERE b.status='DA_CHOT' AND (i.period_start<>d.period_start OR i.period_end<>d.period_end OR i.period_code<>d.period_code))
            THROW 51021, N'Không đổi kỳ đã có bảng lương chốt.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_assignments_timeline_guard
        ON dbo.employee_assignments AFTER INSERT, UPDATE AS
        BEGIN
            SET NOCOUNT ON;
            DECLARE @lock int;
            EXEC @lock=sys.sp_getapplock @Resource=N'HRM_ASSIGNMENT_TIMELINE',
                @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
            IF @lock<0 THROW 51105,N'Khong lay duoc khoa phan cong.',1;
            IF EXISTS(SELECT 1 FROM inserted a JOIN dbo.employee_assignments b WITH (UPDLOCK,HOLDLOCK)
                ON a.employee_id=b.employee_id AND a.assignment_id<>b.assignment_id
                WHERE (a.effective_to IS NULL OR b.effective_from<a.effective_to)
                  AND (b.effective_to IS NULL OR a.effective_from<b.effective_to))
                THROW 51106,N'Phan cong nhan vien chong hieu luc.',1;
            IF EXISTS(SELECT 1 FROM inserted a JOIN dbo.positions p ON p.position_id=a.position_id
                JOIN dbo.employee_assignments b WITH (UPDLOCK,HOLDLOCK)
                    ON a.department_id=b.department_id AND a.assignment_id<>b.assignment_id
                JOIN dbo.positions q ON q.position_id=b.position_id
                WHERE p.position_code='TRUONG_PHONG' AND q.position_code='TRUONG_PHONG'
                  AND (a.effective_to IS NULL OR b.effective_from<a.effective_to)
                  AND (b.effective_to IS NULL OR a.effective_from<b.effective_to))
                THROW 51107,N'Moi phong chi co mot truong phong tai mot thoi diem.',1;
            IF EXISTS(SELECT 1 FROM inserted a JOIN dbo.positions p ON p.position_id=a.position_id
                JOIN dbo.employees e ON e.employee_id=a.employee_id
                JOIN dbo.departments d ON d.department_id=a.department_id
                WHERE p.position_code='TRUONG_PHONG' AND a.effective_to IS NULL
                  AND (e.employment_status<>'DANG_LAM' OR d.is_active=0 OR p.is_active=0 OR a.effective_from<e.hire_date))
                THROW 51108,N'Truong phong phai dang lam viec va thuoc phong/chuc vu dang hoat dong.',1;
        END;
GO
CREATE OR ALTER TRIGGER dbo.tr_departments_head_active
        ON dbo.departments AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM inserted d JOIN dbo.employee_assignments a ON a.department_id=d.department_id
                JOIN dbo.positions p ON p.position_id=a.position_id
                WHERE d.is_active=0 AND p.position_code='TRUONG_PHONG' AND a.effective_to IS NULL)
                THROW 51109,N'Ket thuc truong phong truoc khi ngung su dung phong.',1;
        END;
GO
CREATE OR ALTER TRIGGER dbo.tr_employees_head_termination
        ON dbo.employees AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM inserted e JOIN dbo.employee_assignments a ON a.employee_id=e.employee_id
                JOIN dbo.positions p ON p.position_id=a.position_id
                WHERE e.employment_status='DA_NGHI' AND p.position_code='TRUONG_PHONG' AND a.effective_to IS NULL)
                THROW 51110,N'Ket thuc truong phong truoc khi nhan vien nghi viec.',1;
        END;
GO
CREATE OR ALTER TRIGGER dbo.tr_positions_head_code
        ON dbo.positions AFTER UPDATE AS
        BEGIN SET NOCOUNT ON;
            IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.position_id=i.position_id
                WHERE (d.position_code='TRUONG_PHONG' OR i.position_code='TRUONG_PHONG')
                  AND (i.position_code<>d.position_code OR i.is_active=0))
                THROW 51111,N'Khong doi ma hoac ngung su dung chuc vu he thong TRUONG_PHONG.',1;
        END;
GO
CREATE OR ALTER TRIGGER dbo.tr_g2_leave_allocations_immutable ON dbo.g2_leave_allocations AFTER UPDATE,DELETE AS
  BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 51201,N'Khong sua/xoa phan bo phep da ghi.',1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_g2_execution_immutable ON dbo.g2_resignation_executions AFTER UPDATE,DELETE AS
  BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 51202,N'Khong sua/xoa lich su thuc thi nghi viec.',1; END;
GO
CREATE OR ALTER TRIGGER dbo.tr_g2_schedule_leave ON dbo.work_schedules AFTER INSERT,UPDATE,DELETE AS
 BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.schedule_id=d.schedule_id
  JOIN dbo.g2_leave_allocations a ON a.schedule_id=d.schedule_id
  JOIN dbo.employee_requests r ON r.request_id=a.request_id AND r.status='DA_DUYET'
  WHERE NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)
   AND (i.schedule_id IS NULL OR i.employee_id<>d.employee_id OR i.planned_start<>d.planned_start
   OR i.planned_end<>d.planned_end OR i.planned_break_minutes<>d.planned_break_minutes OR i.work_date<>d.work_date))
  THROW 51203,N'Lịch đã có phép duyệt còn hiệu lực; cần quy trình điều chỉnh.',1;
 IF EXISTS(SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.schedule_id=i.schedule_id
  JOIN dbo.employee_requests r ON r.employee_id=i.employee_id AND r.status='DA_DUYET'
  JOIN dbo.leave_request_details l ON l.request_id=r.request_id
  WHERE NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)
   AND (d.schedule_id IS NULL OR i.employee_id<>d.employee_id OR i.planned_start<>d.planned_start OR i.planned_end<>d.planned_end)
   AND i.planned_start<l.leave_end AND i.planned_end>l.leave_start)
  THROW 51204,N'Lịch mới/thay đổi trùng phép đã duyệt còn hiệu lực.',1;
 END;
GO
CREATE OR ALTER TRIGGER dbo.tr_withdrawal_pending_owner ON dbo.employee_request_withdrawals AFTER INSERT AS
 BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.employee_requests r ON r.request_id=i.request_id JOIN dbo.accounts a ON a.account_id=i.withdrawn_by
  WHERE NOT ((r.status='CHO_DUYET' AND a.employee_id=r.employee_id)
   OR (r.status='DA_DUYET' AND r.request_type='NGHI_PHEP' AND a.is_active=1
    AND EXISTS(SELECT 1 FROM dbo.roles role JOIN dbo.role_permissions rp ON rp.role_id=role.role_id JOIN dbo.permissions p ON p.permission_id=rp.permission_id WHERE role.role_id=a.role_id AND role.role_code IN ('ADMIN','HR') AND p.permission_code='REQUEST_APPROVE')
    AND EXISTS(SELECT 1 FROM dbo.leave_request_details l WHERE l.request_id=r.request_id AND l.leave_start>CONVERT(datetime2,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')))))
  THROW 51304,N'Chỉ chủ đơn rút đơn chờ hoặc người có quyền hủy phép đã duyệt chưa bắt đầu.',1;
 END;
GO
CREATE OR ALTER TRIGGER dbo.tr_request_withdrawal_guard ON dbo.employee_requests AFTER UPDATE AS
 BEGIN SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN deleted d ON d.request_id=i.request_id JOIN dbo.employee_request_withdrawals w ON w.request_id=i.request_id WHERE i.status<>d.status)
  THROW 51301,N'Đơn đã rút/hủy không được đổi trạng thái.',1;
 END;
GO
CREATE OR ALTER TRIGGER dbo.tr_withdrawal_immutable ON dbo.employee_request_withdrawals AFTER UPDATE,DELETE AS
 BEGIN SET NOCOUNT ON;
  IF EXISTS(SELECT 1 FROM deleted) THROW 51302,N'Khong sua/xoa lich su rut don.',1;
 END;
GO

CREATE TRIGGER dbo.tr_overtime_breaks_validate
ON dbo.overtime_breaks AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
      SELECT 1 FROM inserted b JOIN dbo.overtime_requests o ON o.overtime_id = b.overtime_id
      WHERE b.is_active = 1 AND
         (o.actual_start IS NULL OR o.actual_end IS NULL
          OR b.break_start < o.actual_start OR b.break_end > o.actual_end
          OR o.confirmed_at IS NOT NULL)
    ) THROW 53120, N'Khoảng nghỉ phải thuộc thời gian OT chưa xác nhận.', 1;

    IF EXISTS (
      SELECT 1 FROM inserted i JOIN dbo.overtime_breaks b
        ON b.overtime_id = i.overtime_id AND b.break_id <> i.break_id
        AND i.is_active = 1 AND b.is_active = 1
        AND i.break_start < b.break_end AND b.break_start < i.break_end
    ) THROW 53121, N'Các khoảng nghỉ OT không được chồng lấn.', 1;
END

GO
CREATE OR ALTER TRIGGER dbo.[tr_contracts_no_delete] ON dbo.[contracts] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_audit_logs_no_delete] ON dbo.[audit_logs] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_contract_salary_terms_no_delete] ON dbo.[contract_salary_terms] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_work_schedules_no_delete] ON dbo.[work_schedules] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_attendance_records_no_delete] ON dbo.[attendance_records] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_employee_requests_no_delete] ON dbo.[employee_requests] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_leave_request_details_no_delete] ON dbo.[leave_request_details] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_resignation_request_details_no_delete] ON dbo.[resignation_request_details] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_leave_entitlements_no_delete] ON dbo.[leave_entitlements] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_roles_no_delete] ON dbo.[roles] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_overtime_requests_no_delete] ON dbo.[overtime_requests] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_permissions_no_delete] ON dbo.[permissions] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_departments_no_delete] ON dbo.[departments] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_positions_no_delete] ON dbo.[positions] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_qualification_levels_no_delete] ON dbo.[qualification_levels] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_employee_allowances_no_delete] ON dbo.[employee_allowances] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_contract_types_no_delete] ON dbo.[contract_types] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_work_shifts_no_delete] ON dbo.[work_shifts] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_employee_decisions_no_delete] ON dbo.[employee_decisions] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_leave_types_no_delete] ON dbo.[leave_types] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_allowance_types_no_delete] ON dbo.[allowance_types] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_payroll_periods_no_delete] ON dbo.[payroll_periods] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_salary_components_no_delete] ON dbo.[salary_components] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_payroll_batches_no_delete] ON dbo.[payroll_batches] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_employees_no_delete] ON dbo.[employees] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_accounts_no_delete] ON dbo.[accounts] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_payroll_details_no_delete] ON dbo.[payroll_details] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_payroll_lines_no_delete] ON dbo.[payroll_lines] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_role_permissions_no_delete] ON dbo.[role_permissions] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_employee_assignments_no_delete] ON dbo.[employee_assignments] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO
CREATE OR ALTER TRIGGER dbo.[tr_payroll_snapshots_no_delete] ON dbo.[payroll_snapshots] INSTEAD OF DELETE AS
            BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted)
                THROW 51001, N'Không hard-delete. Giữ lịch sử; dùng trạng thái hoặc phiên bản điều chỉnh.', 1; END;
GO

-- Current source migration: 016_contract_cancelled_status.sql
-- Run on the intended HRM database after 013. Only changes the status CHECK.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF DB_NAME() IN ('master','model','msdb','tempdb') OR COL_LENGTH('dbo.contracts','termination_reason_type') IS NULL
        THROW 51960,N'Select the intended HRM database and apply migration 013 first.',1;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id=OBJECT_ID('dbo.contracts') AND name='CK_contracts_status')
        THROW 51961,N'Expected CK_contracts_status is missing. Inspect the target schema.',1;

    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
                   WHERE parent_object_id=OBJECT_ID('dbo.contracts') AND name='CK_contracts_status'
                     AND definition LIKE '%''DA_HUY''%')
    BEGIN
        ALTER TABLE dbo.contracts DROP CONSTRAINT CK_contracts_status;
        ALTER TABLE dbo.contracts WITH CHECK ADD CONSTRAINT CK_contracts_status
            CHECK (status IN ('DA_KY','DA_KET_THUC','DA_HUY'));
    END;
    ALTER TABLE dbo.contracts WITH CHECK CHECK CONSTRAINT CK_contracts_status;
    COMMIT;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK;
    THROW;
END CATCH;

GO

-- Current source migration: 017_single_day_effective_ranges.sql
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

GO
