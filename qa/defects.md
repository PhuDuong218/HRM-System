**Cập nhật sau sửa được phép:** đã chặn các API payroll bị lọt và sửa trần ăn trưa; 10 test mới PASS, HTTP retest 124 PASS/3 FAIL/1 quan sát. Xem [fix-verification.md](fix-verification.md). Các kết quả bên dưới là baseline để giữ lịch sử.

# Các vấn đề đã xác minh

Mức S1: chặn nghiệm thu bảo mật/tài chính. S2: cần sửa trước bàn giao. Các phát hiện không mặc định quy về nhiệm vụ Xuân Anh; cần người sở hữu module xử lý.

## D01 — S1 — EMPLOYEE truy cập và tạo bảng lương quản trị

- Đăng nhập bằng QA EMPLOYEE; GET `/payroll-batches`, `/payroll-periods` trả 200.
- ADMIN tạo kỳ QA mới; cùng EMPLOYEE POST `/payroll-batches/draft` trả **201**, phải 403.
- Gắn một payroll_detail của QA nhân viên B vào chính batch QA vừa tạo; EMPLOYEE A GET `/payroll-batches/{id}/details` trả **200** chứa employeeId của B, phải 403.
- Đây là truy cập chéo có bằng chứng, không chỉ suy luận từ thiếu annotation. Không dùng hồ sơ lương thật để tái hiện.
- Nguồn: PayrollBatchController, PayrollPeriodController; evidence/http-results.json (PAY-employee-create-draft, PAY-other-details).
- Cần role + permission backend, actor lấy từ principal, API phiếu lương cá nhân riêng có kiểm tra chủ sở hữu. Patch đề xuất mới chặn cấp controller, chưa thay thế rà soát service/actor đầy đủ.

## D02 — S1 — Runtime lương đang dùng nguồn giả lập

- QA chạy HrmApplication thật, profile local, SQL hrm_test: bean salaryProvider=MockSalaryContractProvider; attendanceProvider=MockAttendanceDataProvider.
- Provider trả lương/công cố định; kết quả engine đúng không chứng minh truy vấn hợp đồng/công/đơn thật đúng.
- Nguồn: evidence/runtime-beans.json, service/provider/impl. Chưa chứng minh các profile khác có cùng wiring.
- Cần provider thật và chỉ cho phép mock trong profile demo/test rõ ràng; thêm integration test thay dữ liệu nguồn làm tiền lương thay đổi đúng.

## D03 — S1 — Engine bỏ qua căn cứ trả NGÀY/GIỜ

- NGÀY 1.000.000 × 22 công: gross kỳ vọng 22.000.000; thực tế 1.000.000. Bảo hiểm phải quy đổi tháng ×22 theo quyết định nhóm.
- GIỜ 100.000 × 176 giờ: gross kỳ vọng 17.600.000; thực tế 100.000. Bảo hiểm phải quy đổi ×8×22.
- Engine luôn chia baseSalary cho standardDays và bảo hiểm luôn ×10,5% baseSalary. Đây là lỗi engine; chưa đối soát SQL end-to-end.
- evidence/payroll-oracle-results.json DAILY-01/HOURLY-01. Net NGÀY dùng bậc thuế 2 chỉ là tham chiếu code; lỗi gross và căn cứ bảo hiểm không phụ thuộc bậc thuế đó.

## D04 — S2 — Miễn thuế toàn bộ tiền ăn, chưa áp trần 730.000 theo spec đồ án

- Lương tháng 15 triệu, 22 công, ăn trưa 800.000: thuế kỳ vọng 124.750, thực tế 121.250; net cao hơn 3.500.
- Ca nằm trong bậc thuế 1 đã chốt. Không kết luận đây là cách áp thuế pháp luật hiện hành.
- evidence/payroll-oracle-results.json MEAL-CAP-01. Cần cap tổng khoản AN_TRUA, tránh cộng nhiều dòng vượt trần.

## D05 — S2 — Snapshot JSON không escape chuỗi

- Tên QA chứa dấu nháy kép và xuống dòng; gọi buildSnapshotJson thực tế bằng probe: JSON parser từ chối.
- evidence/payroll-policy-probe.json snapshotWithQuotesValid=false.
- Cần JSON serializer có schema/version; kiểm tra lưu/đọc và hoàn tác giao dịch khi snapshot lỗi. Chưa xác nhận bản ghi SQL bị lưu hỏng vì không gọi batch calculate với nguồn giả.

## D06 — S2 — Thiếu salary component vẫn sinh dòng tiền fallback

- Repository danh mục trống: engine vẫn tạo 4 dòng, fallback component_id=1 và direction ADD.
- Có nguy cơ gắn sai thành phần hoặc cộng khoản khấu trừ. Chưa khẳng định SQL đã lưu dòng sai.
- evidence/payroll-policy-probe.json missingComponentsProducesLines=4. Cần fail rõ ràng trước ghi, không tự dựng ID.

## D07 — S2 — Check-in lệch quy tắc đã chốt

- Clock probe: -30 và -16 phút bị từ chối; +16 phút bị từ chối.
- Quyết định người dùng: sớm tối đa 30 phút; sau +15 vẫn ghi giờ thực tế, đánh dấu muộn.
- AttendanceAnomalyPolicy hiện đánh dấu LATE_ARRIVAL ngay sau giờ bắt đầu, chưa đợi +15.
- evidence/payroll-policy-probe.json; AttendanceClockPolicy.java và AttendanceAnomalyPolicy.java. Giới hạn cuối cùng được check-in sau hết ca chưa chốt; không tự đặt trong patch.

## D08 — S2 — Kết quả 401/403 không nhất quán với JWT hợp lệ

- EMPLOYEE GET `/api/management/schedules`, `/api/management/attendance`: 401 thay vì 403.
- Điều này không chứng minh vượt quyền nhưng có thể làm frontend đăng xuất sai khi thiếu quyền.
- GET `/api/management/shifts` trả 200 cho EMPLOYEE không có lịch; cần kiểm tra và giới hạn dữ liệu lịch cá nhân theo ma trận.
- evidence/http-results.json, nhóm RBAC-employee-api-management-*.

## D09 — S2 — Endpoint lock thiếu chặn quyền trước khi chạm nghiệp vụ

- EMPLOYEE POST lock batch QA, truyền actorAccountId của HR khác: trả 500 thay vì 403.
- DB trigger từ chối batch thiếu dòng tiền/snapshot; DB vẫn NHAP, locked_by=NULL. **Không có bằng chứng giả actor/chốt lương thành công.**
- Cần kiểm tra quyền trước service, lấy actor từ JWT; map xung đột nghiệp vụ thành 409 khi người có quyền thao tác.
- evidence/http-results.json PAY-employee-lock-spoof/PAY-lock-actor-evidence và qa-server.log.

## D10 — S2 — Bộ regression chưa xanh

- 17 FAIL/6 ERROR được tách theo từng class trong regression-failures.md. Có harness cũ không chạy filter/AOP, fixture thiếu và contract API thay đổi.
- Không sửa test bằng cách bỏ assertions hoặc nới quyền production. Phải đối chiếu rule rồi cập nhật fixture/harness.

## D11 — S2 — Lịch sử Git từng chứa mật khẩu datasource

- Commit 5b8f70e6582278f0c52c53475ca5f923b73b2ed3 có literal password; khác cấu hình hiện tại. application-local.properties hiện không tracked.
- Chưa xác minh mật khẩu cũ đã vô hiệu hóa. Không đưa mật khẩu vào báo cáo. evidence/secret-history-summary.json.

## D12 — Cảnh báo dependency và bàn giao schema

- npm audit: 4 high/16 moderate/1 low; xem dependency-findings.md, không tự chạy audit fix --force.
- DB hiện có các bảng/trigger không thấy trong phép đối chiếu CREATE TABLE của migration repo (evidence/schema-not-in-migrations.json). Đây là khoảng trống tái tạo môi trường cần kiểm chứng bootstrap riêng, chưa kết luận cài mới thất bại.
- Không thiếu bảng dbo mà source tham chiếu trực tiếp trong phép rà soát đã chạy.
