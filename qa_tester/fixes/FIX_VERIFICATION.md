# Xác minh sửa 5 lỗi HRM Center

Đã sửa BUG-001,002,005,003,004 theo thứ tự S1→S3. Giữ báo cáo tester ban đầu làm baseline, không xóa các ca FAIL cũ để mất lịch sử. Kết quả sau sửa ở RETEST_RESULTS.csv. Không commit/push/stage; không dùng git add .; qa_tester là báo cáo local.

## Thay đổi và căn cứ

1. BUG-001: balances kiểm cả ownerOrManager và PersonnelScopeService.employee trước SELECT. Cùng chính sách với hồ sơ/quỹ phép: ADMIN tất cả, HR trong scope hoặc chủ hồ sơ, EMPLOYEE chỉ chính mình. Không tạo role Manager giả hoặc tin actor header client.
2. BUG-002: logout lấy Account bằng PESSIMISTIC_WRITE trong transaction, tăng tokenVersion trên entity, cập nhật updatedAt và saveAndFlush; không coi bulk affected-row count là bằng chứng duy nhất. Bảng SQL Server kiểm tra có token_version NOT NULL và không có trigger UPDATE account. Chưa chứng minh nguyên nhân nội bộ ORM/driver của count cũ khác1; không sửa schema hoặc bỏ qua lỗi để trả thành công giả. Test thật xác nhận commit version+1 và mọi JWT cũ bị401, token mới dùng được.
3. BUG-005: handler MissingServletRequestParameterException trả JSON400 có tên from/to; handler sai kiểu giữ400. Không nới SecurityFilterChain hoặc mở /error cho anonymous để né lỗi. UI lịch đọc thông điệp nghiệp vụ400/409/422 thay vì chỉ mã HTTP;401 thực vẫn đi qua cơ chế phiên cũ.
4. BUG-003: mở filter-bar cho đủ4 catalog. Bổ sung API keyword (mã/tên, không dấu, nhiều từ) và active cho qualification-levels/leave-types; giữ overload và API không lọc cũ. Lọc trước phân trang. Bộ lọc áp dụng qua query URL để F5 giữ đúng keyword/active; View all xóa query và về trang đầu. Tiêu chí đang gõ chưa tự áp dụng. Danh mục nhỏ hiện lọc danh sách phía service; không khẳng định đã benchmark danh mục hàng triệu bản ghi.
5. BUG-004: Accounts sử dụng TranslatePipe/LanguageService cho nhãn, nút, trạng thái, lỗi client, placeholder, aria-label, tooltip và dialogTitle; thêm account-translations.ts. Không dịch tên/mã/mô tả của dữ liệu server. Các form preview đang khóa trước đây vẫn giữ nguyên, không tự triển khai CRUD ngoài phạm vi.

## Kết quả

- Backend: **62/62 PASS,0 fail/error/skip**. Trong đó5 test SQL Server thực gồm3 lỗi BE + lọc danh mục + quyền payroll;3 test unit mới kiểm logout/scope; các test hồi quy hiện có được chọn giữ PASS.
- Java integration chỉ chạy khi opt-in HRM_VERIFIED_BUGS_IT=true và URL xác nhận databaseName=hrm_test trước khởi tạo context. Có SELECT DB_NAME và fixtures QA_FIX_ qua API; tắt rõ ContractExpiryJob.onReady, resignation scheduler và missing-checkout job. Không chạy lại nhóm test cũ tự chọn hrm_db.
- Frontend: **7/7 PASS**, gồm test mới duyệt các nhãn Accounts VI/EN và persistence sau khởi tạo lại service. Angular production build PASS.
- HTTP ngoài bộ Java: **168/168 PASS**, gồm hai vòng scope/logout/ngày và các GET từng PASS được chạy lại. Không replay mutation trên bản ghi cũ.
- UI: hai catalog lọc đúng trạng thái inactive, query và count giữ sau F5, reset trả đủ danh sách; Accounts VI↔EN/F5, bảng/chi tiết/roles/permissions đổi đúng; tên/mã giữ nguyên; lỗi thiếu from ở schedules không logout; nút logout thật về /login. Không thấy lỗi Console trong các bước này.
- Ba trang sửa có9 lượt mở tại1440/390/320 CSS px, không tràn ngang toàn trang. Đây không phải nghiệm thu mọi form ở mọi kích thước.

## 7 mục người dùng yêu cầu

| Case | Trước | Sau | Kết quả |
|---|---|---|---|
| SES-05 | Logout500; token cũ200 | Logout204; DB token_version+1; tất cả JWT cũ401; login mới200 | PASS trong nhánh đã nêu |
| FEG-02 | Không có filter trên hai danh mục | Tìm từ khóa/trạng thái hoạt động; chưa Search không đổi list; F5 giữ; View all reset cả URL | PASS trong nhánh đã nêu |
| FEG-12 | Accounts vẫn tiếng Việt khi EN | VI/EN và F5 đúng; nhãn bảng/dialog/roles/permissions dịch; dữ liệu giữ nguyên | PASS trong nhánh đã nêu |
| FEG-14 | Baseline toàn hàng NOT-RUN; nhánh thiếu ngày từng401 | Lỗi400 chỉ rõ from/to; UI hiển thị from và giữ phiên | PASS trong nhánh đã nêu |
| BEG-03 | HR-B đọc được16giờ của NV-A | HR-B403; nhân viên khác403; owner/HR-A/ADMIN200 | PASS trong nhánh đã nêu |
| BEG-06 | Thiếu from/to trả401 | Thiếu một/hai ngày hoặc sai định dạng đều400 có tên tham số | PASS trong nhánh đã nêu |
| ENT-03 | Quỹ phép ngoài scope bị lộ qua balances | Chặn403 trước truy vấn quỹ, bên trong phạm vi vẫn200 | PASS trong nhánh đã nêu |

FEG-14 trong CSV baseline không phải FAIL toàn hàng mà là NOT-RUN đầy đủ; lần này kiểm nhánh400 liên quan BUG-005 và giữ401 cho JWT không hợp lệ. Không đánh dấu offline/throttle/500 là đã kiểm. Tương tự, PASS của7 dòng trên không thay thế toàn bộ165 hàng của master tester prompt.

## Các lần thử đầu và xử lý fixture

- Lượt Java đầu:61/62 PASS; một fixture catalog thiếu isPaid bắt buộc cho leave-types. Bổ sung đúng DTO rồi62/62 PASS; không nới validation hoặc assertion.
- Test i18n đầu phát hiện thiếu nhãn Xác nhận mật khẩu, đã bổ sung. Ký tự × không phải nhãn ngôn ngữ, giữ literal. Lượt cuối7/7.
- Lượt HTTP đầu dùng lại JWT EMPLOYEE đã bị chính logout trước đó thu hồi nên một assertion200 nhận401; sửa fixture đăng nhập mới mỗi vòng,168/168. Không thay đổi chức năng thu hồi để né test.

## Tệp kiểm tra

- backend-regression.log / backend-results.json; frontend-regression.log; angular-build.log.
- http-results.json; ui-retest.json; RETEST_RESULTS.csv; changed-files.json.
- Test lâu dài: HRM_System/src/test/java/com/hrm/security/VerifiedBugsSqlServerIntegrationTest.java; service/AuthLogoutRegressionTest.java; service/LeaveBalanceScopeRegressionTest.java; frontend/test-tools/accounts-i18n.test.cjs.
- Script local run-backend-tests.ps1 yêu cầu kết nối hrm_test và tài khoản test đã chuẩn bị; không xuất mật khẩu/JWT. Không chạy script reset mật khẩu cho nhóm.

## Giới hạn và bàn giao

Không sửa120 assertion/fixture đỏ ngoài phạm vi5 bugs từ lần tester trước. Không tuyên bố full suite526 đã xanh hoặc toàn bộ sản phẩm đã nghiệm thu. Không sửa cấu hình datasource đang dùng của người dùng, không đổi mật khẩu hoặc schema trong lượt sửa. Tài khoản QA_FIX_ và dữ liệu test còn trong hrm_test để tái hiện; không tự restore DB hoặc chạy job toàn cục.

Người dùng cần restart Backend đang dùng (cổng2005) để nạp class mới; reload Frontend. Các bước xác minh dùng server QA riêng2009/4203, không triển khai lên Git/main.
