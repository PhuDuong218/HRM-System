/* OPTIONAL: Dữ liệu chốt phiếu lương giả lập của Trung tâm Giáo dục An Phúc (không phải bảng lương thật) for payslip UI tests.
   Run only on isolated local TEST DB AFTER seed_hrm_realistic_test_data.sql.
   NOT suitable for genuine salary or production accounting. No DELETE/DROP.
*/
USE [hrm_db];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
GO
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @batch bigint = (
    SELECT b.batch_id FROM dbo.payroll_batches b JOIN dbo.payroll_periods p ON p.period_id=b.period_id
    WHERE p.period_code='2026-08' AND b.version_no=1
);
DECLARE @hr bigint = (SELECT account_id FROM dbo.accounts WHERE username=N'hr.sample');
IF @batch IS NULL OR @hr IS NULL THROW 53001, N'Thiếu fixture kỳ lương hoặc HR, chạy seed chính trước.',1;
IF NOT EXISTS (SELECT 1 FROM dbo.payroll_batches WHERE batch_id=@batch AND status IN ('NHAP','DA_CHOT'))
    THROW 53003, N'Batch test phải ở trạng thái NHAP hoặc DA_CHOT.', 1;
IF EXISTS (SELECT 1 FROM dbo.payroll_batches WHERE batch_id=@batch AND status='DA_CHOT' AND published_at IS NULL AND locked_at IS NULL)
    THROW 53004, N'Batch chốt thiếu locked_at; dừng để kiểm tra thủ công.', 1;
IF EXISTS (SELECT 1 FROM dbo.payroll_details d WHERE d.batch_id=@batch
           AND EXISTS (SELECT 1 FROM dbo.payroll_lines l WHERE l.payroll_detail_id=d.payroll_detail_id
                       AND l.explanation NOT LIKE N'%GIẢ LẬP%'))
    THROW 53005, N'Batch chứa dòng lương không được đánh dấu GIẢ LẬP; không tự chốt.', 1;

IF EXISTS(SELECT 1 FROM dbo.payroll_details d WHERE d.batch_id=@batch AND
    (NOT EXISTS(SELECT 1 FROM dbo.payroll_lines l WHERE l.payroll_detail_id=d.payroll_detail_id)
      OR NOT EXISTS(SELECT 1 FROM dbo.payroll_snapshots s WHERE s.payroll_detail_id=d.payroll_detail_id)))
    THROW 53002, N'Thiếu dòng tiền/snapshot, không thể khóa bản thử nghiệm.',1;
-- Ensure the three demonstration employees really have complete details before publishing.
IF (SELECT COUNT(DISTINCT e.employee_code)
    FROM dbo.payroll_details d JOIN dbo.employees e ON e.employee_id=d.employee_id
    WHERE d.batch_id=@batch AND e.employee_code IN ('EMP001','EMP004','EMP005')) <> 3
    THROW 53006, N'Thiếu ít nhất một trong ba nhân viên cần phát hành phiếu lương TEST.', 1;
-- DO NOT EDIT OR RECALCULATE THE TEST LINES AFTER LOCK.
UPDATE dbo.payroll_batches SET status='DA_CHOT',locked_by=@hr,locked_at='2026-10-20T02:00:00'
 WHERE batch_id=@batch AND status='NHAP';
UPDATE dbo.payroll_batches SET published_by=@hr,published_at='2026-10-20T02:01:00'
 WHERE batch_id=@batch AND status='DA_CHOT' AND published_at IS NULL;
INSERT dbo.payslips(payroll_detail_id,payslip_number,file_reference)
SELECT d.payroll_detail_id, 'TEST-PS-202608-'+e.employee_code,NULL
FROM dbo.payroll_details d JOIN dbo.employees e ON e.employee_id=d.employee_id
WHERE d.batch_id=@batch AND e.employee_code IN ('EMP001','EMP004','EMP005')
AND NOT EXISTS(SELECT 1 FROM dbo.payslips ps WHERE ps.payroll_detail_id=d.payroll_detail_id);
COMMIT TRANSACTION;
PRINT N'Đã tạo phiếu lương TEST cho batch 2026-08 đã chốt. KHÔNG dùng làm lương thật.';
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
SELECT p.period_code,b.status,b.published_at,COUNT(ps.payslip_id) AS test_payslips
FROM dbo.payroll_batches b JOIN dbo.payroll_periods p ON p.period_id=b.period_id
LEFT JOIN dbo.payroll_details d ON d.batch_id=b.batch_id
LEFT JOIN dbo.payslips ps ON ps.payroll_detail_id=d.payroll_detail_id
WHERE p.period_code='2026-08'
GROUP BY p.period_code,b.status,b.published_at;
