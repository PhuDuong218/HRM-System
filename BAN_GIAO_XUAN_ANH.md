# Bàn giao backend của Xuân Anh

Phạm vi theo bảng phân công và yêu cầu ngày 28/10/2026: hoàn thiện backend của Xuân Anh, kiểm thử và chuẩn bị bàn giao. Phần của thành viên khác sẽ đối chiếu khi Xuân Anh pull về. Chưa làm giao diện.

## 1. Kiểm kê công việc

| Phần việc | Tình hình | Chức năng |
|---|---|---|
| XA-01–XA-07: nhân viên, phòng ban, chức vụ, phân công | Giữ và kiểm thử hồi quy | CRUD/ngừng sử dụng; hồ sơ/liên hệ; tìm kiếm/phân trang; điều chuyển và lịch sử; trưởng phòng theo thời gian; nghỉ việc trực tiếp; quyền và audit |
| XA-08: cấp trình độ | Đã triển khai backend | Tạo/xem/sửa/ngừng/kích hoạt; mã không đổi sau tạo; quyền và audit |
| XA-09: bằng cấp/chứng chỉ | Đã triển khai backend | Nhiều bằng trên một nhân viên; tối đa một bằng chính; chuyển bằng chính có khóa giao dịch; sửa/xóa mềm; giữ lịch sử và người thao tác |
| XA-10: loại nghỉ | Đã triển khai backend | Danh mục và hưởng lương; ADMIN cấu hình riêng việc có sử dụng hạn mức |
| XA-11: đơn phép | Đã triển khai backend | Nộp/xem cá nhân/chi tiết; hàng đợi quản lý; rút đơn chờ có lý do; kiểm tra lịch, trùng thời gian, số dư và quyền |
| XA-12: đơn nghỉ việc | Đã triển khai backend | Nộp/xem/rút đơn chờ; ngày cuối dự kiến; chặn đơn trùng; giữ lịch sử |
| XA-13: xử lý đơn và hiệu lực | Đã triển khai backend trong phạm vi nhân sự | Duyệt/từ chối một lần; cấm tự duyệt; lý do từ chối; phân bổ phép; tới ngày mới nghỉ việc, đóng phân công và khóa tài khoản; báo phụ thuộc cần xử lý |
| XA-14: audit dùng chung | Đã triển khai service và API tra cứu | Actor, thời gian UTC, action, đối tượng, trước/sau, lý do, correlation; che trường mật khẩu/token; cùng transaction với nghiệp vụ |
| XA-15: phối hợp tích hợp nhóm | Chuẩn bị bàn giao; chờ source nhóm | Rà soát phụ thuộc, thứ tự migration và hợp đồng tích hợp ở mục 5; chưa xác nhận build của năm module sau khi ghép |
| XA-16: E2E liên module | Chưa thể nghiệm thu toàn chuỗi | Các luồng nhân sự được kiểm thử; chuỗi công/hợp đồng → bảng lương → phiếu sẽ chạy khi nhận source và chính sách chính thức của nhóm |

Không đánh dấu XA-15/XA-16 hoàn thành toàn hệ thống khi chưa có code và công thức chính thức. Không triển khai thay phần chấm công, tăng ca, hợp đồng, phụ cấp, quyết định, tính lương, báo cáo hoặc đăng nhập của các bạn.

## 2. Đã thu gọn những gì

- Gỡ 220 file vừa nhập/thêm ngoài phạm vi khỏi source và migration đang bàn giao. Bản tham chiếu nằm trong `HRM_System/target/outside-scope-review/`, được Git bỏ qua; không được thêm vào classpath hoặc commit.
- Khôi phục những file hợp đồng và test khởi động đã có trước lúc tích hợp thử. Source cũ, frontend và archive do người dùng có sẵn được giữ lại.
- Gỡ dependency/cấu hình JWT, tính lương và chính sách demo vừa thêm ngoài phạm vi. Không sửa README đang được người dùng chỉnh.
- Giữ dịch vụ audit dùng chung, các adapter nhân sự, đơn từ, withdrawal ledger và bộ kiểm thử liên quan.
- File/class mới dùng tên nghiệp vụ: `EmployeeRequestService`, `PersonnelAccessService`, `ResignationExecutionService`, `AuditLogService`… không tạo class tên G2. Bảng `g2_*`, khóa SQL và alias `/g2/audit` đã dùng vẫn giữ để tương thích dữ liệu/API cũ.

Không xóa chức năng cũ. Có thay đổi kiểm soát truy cập ở API trình độ, bằng cấp, loại nghỉ và hạn mức: phải có Principal được xác thực, tương tự các API nhân sự trước đó. Code đăng nhập thật do thành viên phụ trách cung cấp.

## 3. Database và chạy kiểm tra

Máy có schema HRM: chọn đúng database trong SSMS, chạy theo thứ tự:

1. `HRM_System/database/migrations/001_department_heads.sql`
2. `HRM_System/database/migrations/002_personnel_requests.sql`
3. `HRM_System/database/migrations/003_request_withdrawals.sql`

Các migration không tự đổi database. Máy thử nghiệm mới, trống hoàn toàn mới dùng `000_create_hrm_test.sql` trước ba migration. Không chạy baseline trên database đang có dữ liệu.

Lượt làm việc này chỉ áp dụng thay đổi database vào `hrm_test`; không chạy migration trên `hrm_db`. Một số cấu trúc tích hợp thử đã được thêm vào `hrm_test` ở lượt trước. Không DROP/rollback các cấu trúc đó để tránh mất dữ liệu; chúng không được phát hành trong migration của Xuân Anh. Source hiện tại không phụ thuộc JWT/payroll/scopes đã thử nhập.

Trong PowerShell:

```powershell
cd C:\NguyenXuanAnh\Git-Nhom-HRM\HRM_System
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-personnel.ps1 -Mode test
```

Nếu máy đã có dependency trong cache, có thể thêm `-Offline`. Script bắt buộc datasource trỏ `hrm_test`, tạo lại `classes`, `test-classes`, `maven-status` và giữ các thư mục backup/report khác. Không chạy Maven clean trong khi còn cần bản sao phục hồi trong target. Kết quả chi tiết mới được ghi tại `target/personnel-test-summary.csv`; không tính test bị bỏ qua hoặc báo cáo cũ là đạt. Hai file test cũ dạng mẫu/trống được giữ nguyên và loại khỏi lượt xác minh nghiệp vụ.

Kiểm thử Postman nhân sự/đơn từ:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-personnel.ps1 -Mode postman
```

Chờ `PERSONNEL_POSTMAN_READY`, giữ terminal đó mở. Import:

- Collection: `HRM_System/test-tools/personnel.postman_collection.json`
- Environment vừa sinh: `HRM_System/target/personnel-postman-runtime.postman_environment.json`

Chọn environment **HRM Personnel — local test session**, đặt `runMutations=true`, chạy cả collection đúng thứ tự, một iteration. Server nghe `127.0.0.1:2007`, chỉ dùng trong test, nhận token phiên ngẫu nhiên để gắn Principal; đây chưa phải kiểm thử JWT của nhóm. Không gửi token phiên này đến server nghiệp vụ cổng 2005.

Mỗi phiên server chỉ chạy chuỗi ghi dữ liệu một lần. Chạy lại thì dừng, bật server mới và import environment mới. Fixture riêng và lịch sử Postman được giữ trong hrm_test; các test Java dùng rollback. Dừng bình thường khóa các tài khoản fixture:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-personnel.ps1 -Mode stop
```

Hồi quy Postman phần nhân viên/phòng ban/chức vụ/điều chuyển:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-g1.ps1 -Mode postman
```

Import collection `test-tools/g1.postman_collection.json` và environment runtime `target/g1-postman-runtime.postman_environment.json`, cổng 2006. Dừng bằng `run-g1.ps1 -Mode stop`. Không dùng nhầm hai environment.

## 4. Luồng nghiệp vụ cần hiểu khi demo

**Bằng cấp:** quản lý thêm bằng → có thể chọn làm chính → hệ thống bỏ cờ chính ở bằng trước trong cùng transaction → xóa là xóa mềm. Không tạo hai bằng chính khi hai thao tác cạnh tranh.

**Phép:** tạo loại nghỉ → ADMIN chốt có dùng hạn mức → cấp giờ phép nếu cần → nhân viên nộp → quản lý duyệt/từ chối. `is_paid` và `consumesEntitlement` độc lập. Số giờ được cấp không bị trừ trực tiếp; số đã dùng lấy từ phân bổ của đơn đã duyệt. Pending không giữ chỗ; duyệt luôn kiểm tra lại số dư.

**Thời gian:** nghiệp vụ dùng giờ Việt Nam; audit UTC. Khoảng phép, phân công, kỳ hạn mức là `[bắt đầu, kết thúc)`. Nghỉ ca qua đêm dùng `work_date` của ca. Trọn ca trừ thời gian nghỉ giữa ca; nghỉ một phần ca có break nhưng chưa biết vị trí break thì bị từ chối thay vì đoán giờ nghỉ. Không tự mặc định số ngày phép/năm hoặc 8 giờ/ngày.

**Rút đơn:** chỉ người nộp rút khi còn chờ; bắt buộc lý do. Đơn vẫn giữ trạng thái gốc `CHO_DUYET`, có `withdrawnAt`/`withdrawalReason`; biến mất khỏi hàng đợi xử lý nhưng còn trong lịch sử cá nhân. Không duyệt/từ chối/rút lại; được nộp đơn mới. Không tạo trạng thái thứ tư ngoài đặc tả.

**Nghỉ việc:** duyệt không làm nhân viên nghỉ ngay. Sau ngày làm cuối mới đóng phân công tại ngày kế tiếp, đặt ngày nghỉ đúng ngày cuối và khóa tài khoản. Kiểm tra trưởng phòng, lịch, hợp đồng, phân công tương lai và phép đã duyệt. Có phụ thuộc thì báo BLOCKED, không tự sửa phần của bạn khác. Chạy lại không thực thi hai lần. Scheduler mặc định tắt; khi nhóm sẵn sàng mới bật:

```properties
personnel.resignations.scheduler-enabled=true
personnel.resignations.interval-ms=60000
```

ADMIN có thể gọi `POST /employee-requests/resignations/execute-due`. API nghỉ việc trực tiếp cũ vẫn giữ, không dùng thay quy trình duyệt đơn nếu nhân viên đang chờ ngày nghỉ đã được duyệt.

**Audit:** nghiệp vụ và audit cùng transaction, lỗi thì rollback. API `/personnel/audit` cho quản lý tra cứu đối tượng nhân sự trong whitelist; `/audit-logs` ADMIN tra cứu dùng chung. Correlation ID được trả trong header `X-Correlation-Id` để đối chiếu thao tác.

## 5. Hợp đồng bàn giao để ghép source nhóm sau

| Module của nhóm | Điểm cần thống nhất với phần Xuân Anh |
|---|---|
| Đăng nhập/quyền | Gắn Principal có `getName()` bằng `accounts.username`; tài khoản hoạt động, nhân viên DANG_LAM; EMPLOYEE_MANAGE cho quản lý; REQUEST_APPROVE cho duyệt; không tin header role/accountId từ client |
| Lịch/chấm công | work_schedules dùng giờ Việt Nam và `work_date`; không thay đổi ca đã tính phép duyệt nếu chưa có quy trình điều chỉnh; dùng phân bổ thực tế, không tự trừ thêm lần nữa |
| Hợp đồng | `contracts.end_date` là ngày cuối có hiệu lực; đóng/xử lý hợp đồng vượt ngày làm cuối ở module hợp đồng trước khi thực thi nghỉ việc |
| Lương/phiếu | Đọc phép đã duyệt và chế độ của loại nghỉ; số dư phép chỉ để quản lý quota, không tự suy ra công thức khấu trừ lương; chờ công thức công chuẩn/OT/thuế/bảo hiểm của nhóm |
| Audit các module | Inject `AuditLogService`, gọi `record(AuditEvent)` trong transaction nghiệp vụ, truyền actor lấy từ server; actor null chỉ cho tác vụ hệ thống; không truyền request đăng nhập/mật khẩu hoặc dùng audit làm nơi lưu secret |

Ví dụ dùng chung (để thành viên ghép vào service của chính họ sau này):

```java
@Transactional
public void updateBusinessRecord(Long authenticatedActorId) {
    // Đọc snapshot trước, kiểm tra quyền và cập nhật nghiệp vụ.
    auditLogService.record(new AuditEvent(
        authenticatedActorId, "DOMAIN_UPDATE", "domain_table", "123",
        "Lý do nghiệp vụ", beforeSnapshot, afterSnapshot));
}
```

Chưa tạo payroll policy demo hoặc áp công thức lương. Khi có source của nhóm: đối chiếu schema → ghép và build → chạy hồi quy nhân sự/đơn từ → kiểm thử JWT thật → chạy E2E với công thức đã được xác nhận → mới nghiệm thu XA-15/XA-16.

## 6. Git

Chưa commit, push hoặc merge. Khi bàn giao chỉ chọn source Java, các migration liên quan, runner và collection/generator cần dùng. Không add toàn bộ bằng `git add .` khi còn tài liệu cá nhân và archive.

Không đưa `target/`, log, runtime environment/token, datasource local, archive source cá nhân, tài liệu review/hướng dẫn cá nhân lên Git. Bản hướng dẫn này phục vụ Xuân Anh kiểm kê, không bắt buộc đưa lên repository nhóm.

## 7. Kết quả xác minh ngày 28/10/2026

| Kiểm tra | Kết quả |
|---|---|
| Java nghiệp vụ, HTTP controller, SQL Server integration, khóa cạnh tranh và hồi quy | 123 PASS, 0 lỗi, 0 bỏ qua |
| Postman trình độ/phép/đơn từ/audit | 81 request, 91 assertion, 0 lỗi |
| Postman G1 hiện có | 149 request, 176 assertion, 0 lỗi |
| Maven package | BUILD SUCCESS |
| Nội dung jar | Có service đơn từ/audit; không có Postman server test, datasource local thật hoặc JWT/payroll vừa nhập ngoài phạm vi |
| Phạm vi source | Không còn 220 file vừa thêm ngoài phạm vi trong source; Git không có file tracked bị xóa |

Đã kiểm tra: thiếu đăng nhập/quyền, xem đơn/số dư người khác, ADMIN tự duyệt/từ chối, ngày đảo ngược/quá khứ, thiếu lịch/chính sách, đơn trùng, số dư không đủ, duyệt lặp, thay đổi số dư trong lúc chờ, ca qua đêm, lịch đã có phép, đổi bằng chính, rút đơn lặp/rút thay/rút sau duyệt, lịch sử và audit, ngày hiệu lực nghỉ việc, rollback khi audit lỗi và chờ khóa giao dịch.

Hai server Postman đã dừng sau xác minh; tài khoản fixture được khóa khi dừng. Khởi động phiên mới theo mục 3 để tự kiểm tra. Các kết quả này xác minh backend thuộc phạm vi Xuân Anh, chưa xác minh JWT thật hoặc E2E năm module khi chưa pull code nhóm.
