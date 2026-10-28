# Bộ kiểm thử trưởng phòng dành cho Xuân Anh

Chỉ bổ sung script, bộ request Postman và tài liệu kiểm thử; không sửa code nghiệp vụ hoặc thay nội dung test cũ trong lượt chuẩn bị bộ này.

Đã chạy lại bằng script mới ngày 23/10/2026 trên hrm_test: 19 test, 0 thất bại, 0 lỗi, 0 bỏ qua. [CSV kết quả lần xác minh](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/target/head-test-summary.csv) nằm trong target nên có thể bị xóa khi chạy Maven clean. Postman gồm 19 request; đã kiểm tra JSON, cú pháp JavaScript và điều kiện mặc định bỏ qua nhóm ghi, chưa chạy chuỗi HTTP có JWT.

## Cách chạy ngay khi chưa có JWT

Mở PowerShell tại thư mục backend:

```powershell
cd C:\NguyenXuanAnh\Git-Nhom-HRM\HRM_System
.\run-head-tests.ps1
```

Script chạy 19 test của hai lớp đã có: DepartmentHeadIntegrationTest (18) và AssignmentLockConcurrencyTest (1). Không cần bật server bằng spring-boot:run trước. Spring tự khởi tạo trong test; SQL Server vẫn phải đang chạy.

Nếu PowerShell chặn script tải về, mở một PowerShell mới với chính sách chỉ áp dụng cho tiến trình đó:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-head-tests.ps1
```

Script không đổi ExecutionPolicy toàn máy, không sửa application-local.properties và khôi phục JAVA_HOME/PATH sau khi chạy.

Chạy toàn bộ test hiện có, gồm hai test cũ:

```powershell
.\run-head-tests.ps1 -All
```

Hoặc dùng Maven trực tiếp:

```powershell
.\mvnw.cmd "-Dtest=DepartmentHeadIntegrationTest,AssignmentLockConcurrencyTest" test
```

Chạy riêng một tình huống:

```powershell
.\mvnw.cmd "-Dtest=DepartmentHeadIntegrationTest#replacementDemotesPreviousHeadAndPreservesBoundary" test
```

## Điều kiện database và Java

- Dùng database SQL Server thử nghiệm riêng, có sẵn schema HRM, ba vai trò ADMIN/HR/EMPLOYEE và quyền EMPLOYEE_MANAGE với role ADMIN như dữ liệu seed đã cung cấp. Tên database phải chứa test. Máy này hiện dùng hrm_test.
- Cấu hình kết nối lấy từ application-local.properties như backend hiện tại, hoặc SPRING_DATASOURCE_URL. Không đổi sang database dữ liệu thật để chạy test.
- Các test tạo fixture tạm, chạy migration trong giao dịch và rollback. Không cần tự nhập nhân viên A/B. Rollback không hoàn lại số IDENTITY hoặc bộ đếm rowversion; có khoảng nhảy ID sau test là bình thường.
- Tài khoản test cần quyền chạy migration/trigger và ghi fixture trong database test. Không nâng quyền tài khoản ứng dụng thật chỉ để chạy kiểm thử.
- Dự án đặt Java release 21. Máy đã xác minh dùng JDK 23 biên dịch release 21; nên chạy thêm bằng JDK 21 của nhóm trước bàn giao.

Nếu cần chỉ rõ đường dẫn JDK/Maven/cache:

```powershell
.\run-head-tests.ps1 -JavaHome 'C:\Program Files\Java\jdk-21' -MavenCommand 'C:\duong-dan-maven\bin\mvn.cmd'
```

Thay các đường dẫn bằng đường dẫn thật trên máy. Dùng -Offline chỉ khi dependency Maven đã có trong cache; -MavenRepository cho phép chọn cache riêng.

## Danh sách 19 test tự động

| Mã | Tên hàm test | Kết quả cần đạt |
| --- | --- | --- |
| TP01 | appointmentKeepsContiguousHistoryAndAudit | Bổ nhiệm đúng, đóng chức vụ cũ liền mạch, audit có tài khoản thực hiện |
| TP02 | replacementDemotesPreviousHeadAndPreservesBoundary | Thay A bằng B; ngày trước mốc là A, đúng mốc là B; chức vụ tiếp theo của A đúng lựa chọn |
| TP03 | replacementRequiresExplicitNextPositionAndLeavesOldHeadUnchanged | Thiếu chức vụ người cũ bị 400; trưởng phòng cũ giữ nguyên |
| TP04 | endLeavesVacancyAndKeepsHistoricalHead | Kết thúc nhiệm kỳ; hiện tại trống, lịch sử còn nguyên |
| TP05 | refusesFutureAndZeroLengthAndDuplicateAppointments | Chặn ngày tương lai, khoảng rỗng và bổ nhiệm lại người đang giữ chức |
| TP06 | refusesInactiveDepartmentAndRetiredEmployee | Chặn phòng ngừng hoạt động và nhân viên nghỉ việc |
| TP07 | refusesEmployeeFromAnotherDepartment | Chặn người thuộc phòng khác |
| TP08 | accessUsesAuthenticatedPrincipalAndAccountState | Chưa xác thực là 401; tài khoản không hợp lệ/khóa là 403 |
| TP09 | employeeRoleCannotAppointHead | EMPLOYEE không được bổ nhiệm, trả 403 |
| TP10 | genericTransferCannotBypassHeadWorkflow | API điều chuyển thường không bỏ qua quy trình trưởng phòng |
| TP11 | candidatesAndHistoryArePagedAndExcludeCurrentHead | Danh sách có phân trang; người đang là trưởng phòng không trong ứng viên; page âm bị chặn |
| TP12 | databaseRejectsHeadOverlapEvenForDirectSql | Trigger chặn SQL gán hai trưởng phòng chồng ngày |
| TP13 | migrationCanRunTwice | Chạy migration lại không tạo trùng chức vụ |
| TP14 | ordinaryTransferKeepsHalfOpenBoundary | Điều chuyển thường không làm hở một ngày lịch sử |
| TP15 | retirementOnAssignmentStartKeepsTheLastWorkingDay | Nghỉ ngay ngày bắt đầu phân công vẫn giữ ngày làm việc cuối, không lỗi CHECK |
| TP16 | departmentAndRetirementCannotSilentlyRemoveAnOpenHead | Phải kết thúc/thay trưởng phòng trước khi ngừng phòng hoặc cho người đó nghỉ |
| TP17 | httpRejectsAnonymousAndClientClaimedRoles | HTTP không chấp nhận tự khai vai trò/ID bằng header |
| TP18 | httpExposesVacancyAndValidatedAppointment | HTTP trả 204 khi trống, 400 khi body thiếu, 200 khi bổ nhiệm hợp lệ và đọc được lịch sử |
| TP19 | competingAssignmentTransactionsAreSerialized | Giao dịch thứ hai phải chờ giao dịch thứ nhất nhả khóa |

Các file Java gốc:

- [18 test nghiệp vụ/API](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/test/java/com/hrm/hrm/service/DepartmentHeadIntegrationTest.java).
- [Test hai giao dịch](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/test/java/com/hrm/hrm/service/AssignmentLockConcurrencyTest.java).

Các test HTTP dùng MockMvc với Principal do môi trường kiểm thử cung cấp. Chúng kiểm tra controller/service/DB nhưng không chứng minh JWT filter, đăng nhập trên trình duyệt hay toàn bộ hệ thống đã đúng. Các test nghiệp vụ dùng transaction ngoài của test; đây chưa phải kiểm thử đầy đủ mọi lỗi rollback khi chạy request thật.

## Đọc kết quả

Khi chạy bộ mới cần thấy:

```text
Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Nếu chạy -All với bộ hiện tại sẽ có 21 test. Script in tên từng test PASS/FAIL/SKIP và tạo:

- HRM_System/target/head-test-summary.csv: bảng kết quả lần chạy hiện tại.
- HRM_System/target/surefire-reports: báo cáo chi tiết Maven; file .txt có stack trace khi thất bại.

Mở file báo cáo tên lớp bị lỗi; tìm tên test, dòng expected/actual và nguyên nhân ở phần Caused by. Không sửa số mong đợi chỉ để test xanh; đối chiếu quy tắc trong bảng trên.

## Thử API bằng Postman

Import [bộ request Postman](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/test-tools/department-heads.postman_collection.json).

1. Chạy backend cổng 2005.
2. Các request health và chưa đăng nhập có thể chạy ngay. Health cần 200; truy cập phòng ban/trưởng phòng chưa xác thực cần 401.
3. Request yêu cầu token sẽ được bỏ qua khi token trống. Hiện repo chưa có JWT nên chưa thể kiểm tra thao tác thành công bằng Postman chỉ bằng cách tự nhập username/role.
4. Khi Việt đã nối JWT, điền token ADMIN/HR thật vào biến token. tokenEmployee dành riêng cho token EMPLOYEE để thử 403; không điền token ADMIN vào biến đó.
5. Nếu muốn chạy nhóm thay đổi dữ liệu, dùng phòng ban và nhân viên thử nghiệm riêng rồi đặt runMutations=true. Nhóm này mặc định bị bỏ qua. Nó ghi dữ liệu qua HTTP thật nên không tự rollback như JUnit.

Các biến cho nhóm thay đổi dữ liệu:

| Biến | Ý nghĩa |
| --- | --- |
| baseUrl | Mặc định http://localhost:2005 |
| departmentId | Phòng thử nghiệm đang hoạt động và chưa có trưởng phòng |
| employeeA / employeeB | Hai người đang làm, có phân công mở trong phòng này, chức vụ thường |
| nextPositionId | Chức vụ thường còn hoạt động cho trưởng phòng cũ |
| firstDate / replaceDate / endDate | Ba ngày: firstDate < replaceDate < endDate <= hôm nay; phân công ban đầu của A/B phải bắt đầu trước firstDate |
| token | Token ADMIN/HR có EMPLOYEE_MANAGE |
| tokenEmployee | Token EMPLOYEE để kiểm tra bị từ chối |
| runMutations | false: không chạy nhóm ghi; true: chạy trên dữ liệu thử nghiệm đã chuẩn bị |

Chạy nhóm ghi theo đúng thứ tự: bổ nhiệm A → xem A → từ chối bổ nhiệm A lần nữa → từ chối thay thiếu chức vụ người cũ → thay bằng B → xem B → đọc lịch sử → kết thúc B → kiểm tra phòng trống. Không chạy lại chuỗi trên chính các bản ghi đã dùng mà không chuẩn bị một bộ dữ liệu thử mới.

## Tự kiểm tra giao diện

Tại thư mục frontend:

```powershell
npm run build
npm start
```

Mở /departments và kiểm tra:

| Tình huống | Mong đợi |
| --- | --- |
| Chưa có phiên đăng nhập | Thông báo cần đăng nhập, không báo lưu thành công |
| Backend tắt hoặc lỗi mạng | Hiện lỗi; không giả vờ đã tải/lưu dữ liệu |
| Có phiên ADMIN/HR hợp lệ | Tải danh sách phòng và trưởng phòng đúng dữ liệu |
| Chọn phòng | Tải ứng viên và lịch sử của đúng phòng |
| Thay người nhưng bỏ chức vụ người cũ | Không gửi form hợp lệ/không chấp nhận lưu |
| Ngày không hợp lệ | Hiện lỗi; không thay đổi lịch sử |
| Lưu thành công | Tải lại trưởng phòng, ứng viên và lịch sử |
| Đang lưu | Không cho bấm lưu liên tục hoặc đổi phòng giữa chừng |

Build kiểm tra biên dịch/template; checklist trình duyệt này là phần tự kiểm tra, không được mặc định coi đã qua chỉ vì build thành công.
