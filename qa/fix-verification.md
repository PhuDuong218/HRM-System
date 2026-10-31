# Xác minh bản sửa đã được cho phép

Người dùng cho phép sửa các lỗi phân quyền lương và trần ăn trưa sau khi nhận bằng chứng; các file production dưới đây đã thay đổi, không chỉ có patch đề xuất.

- PayrollBatchController: quản lý lương yêu cầu ADMIN/HR + PAYROLL_MANAGE; đọc chấp nhận PAYROLL_VIEW hoặc PAYROLL_MANAGE. EMPLOYEE bị chặn kể cả có authority payroll. Lock/publish lấy accountId từ principal, bỏ qua query actorAccountId để giữ tương thích URL.
- PayrollPeriodController: áp cùng gate đọc/ghi, tránh truy cập quản trị chỉ cần đăng nhập.
- PayrollCalculationEngine: trần miễn thuế AN_TRUA tính trên tổng nhiều dòng, tối đa 730.000 theo spec đồ án.
- Test mới: PayrollManagementAuthorizationTest và PayrollMealExemptionTest: **10/10 PASS**, 0 fail/error/skip. Không dùng SQL trong hai class này. Có test ADMIN/HR hợp lệ, thiếu permission, EMPLOYEE có permission giả, chỉ quyền xem và giả actor.

## HTTP thật sau sửa

128 bản ghi: 124 PASS, 3 FAIL, 1 quan sát; evidence/http-retest-results.json. Runtime mới dùng source đã build, JWT thật, hrm_test; tắt server/tài khoản QA sau chạy.

- PAY-employee-create-draft: HTTP 403, PASS.
- PAY-other-details: HTTP 403, PASS.
- PAY-employee-lock-spoof: HTTP 403, PASS.
- PAY-admin-create-draft: HTTP 201, PASS.

Batch QA vẫn NHAP/locked_by=NULL sau request EMPLOYEE; admin tạo draft vẫn 201. Test method-security xác nhận lock/publish chuyển actor JWT 71 sang service thay vì ID giả 999. Chưa thực hiện admin chốt/công bố batch lương đầy đủ trên SQL.

## Oracle độc lập sau sửa

MEAL-CAP-01: thuế 124.750, net 14.100.250, chênh 0. Trước sửa net cao hơn 3.500. Các ca TC-01…08 không thay đổi kết quả. DAILY-01/HOURLY-01 vẫn FAIL; không gọi là đã sửa NGÀY/GIỜ. evidence/payroll-oracle-retest.json.

## Còn mở

D02/D03/D05/D06/D07 chưa sửa. D08 vẫn 3 probe FAIL: shifts trả 200, schedules/attendance trả 401 thay 403. D09 đã chặn EMPLOYEE trước nghiệp vụ; mapping lỗi 500 khi ADMIN hợp lệ gặp trigger vẫn chưa sửa. Chưa chạy lại toàn bộ 541 test sau sửa; kết quả 477/17/6/41 là baseline, không phải suite cuối cùng.

Patch 01/02 trong patches/ là bản đề xuất ban đầu đã được áp dụng và bổ sung actor/read permission như mô tả; không áp lại máy móc. Patch 03 context test vẫn chưa áp dụng.

## Dọn phiên QA và giữ workspace

Backend QA 2009 và frontend QA 4202 đã dừng; không dừng server người dùng 2005/4200. SQL kiểm tra 21 tài khoản QA ngày 31/10: activeQaAccounts=0. File mật khẩu QA tạm đã xóa. Fixture QA được giữ để đối chiếu, không xóa cứng lịch sử. Không còn unmerged paths; staged diff giữ đúng hash baseline, các source untracked cũ không đổi. Xem cleanup-database.json và final-worktree-verification.json trong evidence/.

Backend đang chạy của người dùng chưa được restart để nạp bản sửa; cần restart bằng cấu hình hiện có trước khi kiểm tra trên cổng làm việc bình thường. Không commit/push trong lượt này.
