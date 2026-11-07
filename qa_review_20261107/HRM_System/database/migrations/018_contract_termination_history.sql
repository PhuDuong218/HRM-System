-- Run in the application's database. Does not change any contract rows.
-- Keep signed content immutable, except a one-time shortening on termination.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE OR ALTER TRIGGER dbo.tr_contracts_keep_history ON dbo.contracts AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.contract_id = i.contract_id
        WHERE i.employee_id <> d.employee_id OR i.contract_type_id <> d.contract_type_id
           OR i.contract_number <> d.contract_number OR i.signed_date <> d.signed_date
           OR i.start_date <> d.start_date
           OR (
               (i.end_date <> d.end_date OR (i.end_date IS NULL AND d.end_date IS NOT NULL) OR (i.end_date IS NOT NULL AND d.end_date IS NULL))
               AND NOT (
                   d.status = 'DA_KY' AND d.termination_reason_type IS NULL
                   AND i.status IN ('DA_KY', 'DA_KET_THUC')
                   AND i.termination_reason_type IS NOT NULL
                   AND i.termination_reason_type IN
                       ('HET_HAN', 'THOA_THUAN', 'NLD_DON_PHUONG', 'NSDLD_DON_PHUONG', 'SA_THAI')
                   AND i.end_date IS NOT NULL AND i.end_date >= d.start_date
                   AND (d.end_date IS NULL OR i.end_date <= d.end_date)
               )
           )
    )
        THROW 51011, N'Không ghi đè nội dung hợp đồng đã ký. Chỉ được rút ngắn ngày cuối khi ghi nhận chấm dứt lần đầu.', 1;
END;
GO
