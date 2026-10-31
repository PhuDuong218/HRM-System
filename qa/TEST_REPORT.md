**Cập nhật sau sửa được phép:** đã chặn các API payroll bị lọt và sửa trần ăn trưa; 10 test mới PASS, HTTP retest 124 PASS/3 FAIL/1 quan sát. Xem [fix-verification.md](fix-verification.md). Các kết quả bên dưới là baseline để giữ lịch sử.

# Báo cáo QA HRM-Center — 31/10/2026

**Kết luận: CHƯA SẴN SÀNG nghiệm thu toàn hệ thống.** Có lỗi quyền truy cập bảng lương và tính lương đã tái hiện. Đây là báo cáo bằng chứng hiện có, không xác nhận hoàn thành toàn bộ 218 ca.

| Lớp kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Bộ Java hiện có | 541: 477 PASS, 17 FAIL, 6 ERROR, 41 SKIP | Có lỗi harness/fixture; không coi mọi test đỏ là bug sản phẩm |
| HTTP với JWT thật | 118 PASS, 8 FAIL, 1 quan sát | Dữ liệu QA trong hrm_test; không gọi tính lương ghi từ provider giả |
| Oracle lương độc lập | 11 tình huống: 4 PASS, 4 khớp code, 3 FAIL | Engine-only; 4 ca bậc thuế cao chưa được đặc tả chính thức |
| Web responsive | 104 lượt mở: 26 route × 4 kích thước CSS | 1280/768/390/320 px; không tràn ngang toàn trang; chưa kiểm hết form CRUD |
| Angular production build | PASS | Không thay thế kiểm thử nghiệp vụ |
| npm audit | 21 cảnh báo: 4 high, 16 moderate, 1 low | Chưa cập nhật dependency tự động |

Ưu tiên: (1) chặn EMPLOYEE tạo/xem bảng lương của người khác; (2) thay provider giả bằng dữ liệu thật trước nghiệm thu lương; (3) sửa căn cứ NGÀY/GIỜ, trần miễn thuế ăn trưa và JSON snapshot; (4) đồng bộ check-in theo quyết định đã chốt.

Chi tiết: [defects.md](defects.md), [coverage-matrix.md](coverage-matrix.md), [payroll-reconciliation.md](payroll-reconciliation.md), [recommendations.md](recommendations.md).

Baseline được kiểm thử trước khi sửa source. Sau khi người dùng cho phép, đã sửa PayrollBatchController, PayrollPeriodController, PayrollCalculationEngine và thêm 2 file test; xem fix-verification.md. Giữ nhánh Duong, không ghi hrm_db, không commit/push. Các bản sao lưu COPY_ONLY/CHECKSUM đã RESTORE VERIFYONLY thành công; chưa thực hiện khôi phục.
