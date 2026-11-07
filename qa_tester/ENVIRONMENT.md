# Môi trường và lệnh

- Ngày: 01/11/2026, Asia/Saigon. Windows; Node v24.22.1; Java23.0.1, compile release21; Maven wrapper cache3.9.16.
- Branch `XuanAnh`, HEAD `fb9376567f1f9a6c1b61c18ed075961b893c2b2e`. Không checkout/merge/commit/push/reset/stash/clean.
- Working tree chưa commit từ trước: giữ nguyên. Hash so sánh source/test đầu-cuối: 0 file thay đổi. Chi tiết baseline.json và end-state.json.
- Runtime QA cổng2009, web4203 (proxy riêng qa_tester/proxy.json). Các kết nối JDBC/JWT không in ra báo cáo.
- SELECT DB_NAME xác nhận hrm_test; 50 bảng. Không chạy script tạo DB/migrations vì DB test đã có sẵn; chưa chứng minh migration provenance đầy đủ.
- Backup COPY_ONLY + CHECKSUM và RESTORE VERIFYONLY đạt, xem backup.json. Không RESTORE tự động; khôi phục hrm_test sau khi dừng các client và xác nhận không có thay đổi cần giữ từ người khác, dùng file backup đã ghi. Không dùng hrm_db làm đích restore.
- Ngoại lệ SQL chuẩn bị: prepare-account.ps1 theo README_TAI_KHOAN.md, chỉ admin.sample trên hrm_test: mở khóa, thay hash bằng hash của README, tăng token_version, cập nhật thời điểm. Không xuất giá trị mật khẩu/hash. Không reset hàng loạt.
- CRUD QA qua API: tiền tố QA_20261101_205827; không dùng UPDATE/DELETE SQL dựng tình huống. Không thực thi job nghỉ việc toàn cục. QA data còn để tái hiện, QA admin đã khóa qua API; tài khoản mẫu test vẫn hoạt động sau chuẩn bị.
- Rủi ro: launcher QA tắt scheduler processor, nhưng ApplicationReadyEvent là cơ chế riêng; bản test mặc định có thể khởi tạo ContractExpiryJob.onReady. Chưa có bằng chứng đảm bảo job không ảnh hưởng dữ liệu có sẵn. Đọc OPEN_QUESTIONS.md mục6; không được diễn đạt là hrm_db chắc chắn nguyên vẹn.

## Lệnh đã chạy (tham số nhạy cảm không ghi)

- git status --short; git branch --show-current; git rev-parse HEAD; git diff --stat.
- probe-db.ps1 / backup.ps1 / select.ps1; tất cả connection helper kiểm DB_NAME trước thao tác.
- npm --prefix frontend run build: PASS (angular-build.log).
- javac QA-only QaBackendLauncher; start-backend.ps1 trên port2009. Không đổi source ứng dụng.
- ng serve --host127.0.0.1 --port4203 với proxy QA riêng.
- api_test.py, api_workflows.py, api_edge_cases.py (bản không DELETE), security_scan.py, recheck.py, session_checks.py.
- mvn -o -Dtest=<82 lớp trong java-selector.json> -DargLine=<cached Mockito agent> test. Không chạy full suite không chọn lọc vì các lớp SQL ghi fixture trực tiếp.
- Một lượt trong sandbox và một lượt ngoài sandbox để phân biệt lỗi loopback; kết quả cuối từ lượt ngoài sandbox, không cộng hai lượt thành số ca độc lập.

Kết quả Java: 526 test, 399 PASS, 120 FAIL, 6 ERROR, 1 SKIP. Các lớp bị loại là NOT-RUN, không cộng vào JUnit skip.

## Phân loại test đỏ

- com.hrm.hrm.service.AssignmentLockConcurrencyTest: 1 fail / 0 error — Cần đối chiếu fixture và nghiệp vụ; chưa chứng minh lỗi sản phẩm.
- com.hrm.hrm.service.AuditLogIntegrationTest: 2 fail / 0 error — Môi trường: DB guard chặn cấu hình hrm_db.
- com.hrm.hrm.service.DepartmentHeadIntegrationTest: 18 fail / 0 error — Cần đối chiếu fixture và nghiệp vụ; chưa chứng minh lỗi sản phẩm.
- com.hrm.hrm.service.EmployeeAssignmentValidityIntegrationTest: 5 fail / 0 error — Môi trường: DB guard chặn cấu hình hrm_db.
- com.hrm.hrm.service.EmployeeContactAuditIntegrationTest: 4 fail / 0 error — Môi trường: DB guard chặn cấu hình hrm_db.
- com.hrm.hrm.service.EmployeeRequestWorkflowIntegrationTest: 32 fail / 0 error — Môi trường: DB guard chặn cấu hình hrm_db.
- com.hrm.hrm.service.EmployeeServiceMergeTest: 0 fail / 1 error — Fixture thiếu bean/strict stub cũ.
- com.hrm.hrm.service.EmployeeTerminationAuditIntegrationTest: 3 fail / 0 error — Môi trường: DB guard chặn cấu hình hrm_db.
- com.hrm.hrm.service.G1EndpointAccessTest: 2 fail / 0 error — Cần đối chiếu fixture và nghiệp vụ; chưa chứng minh lỗi sản phẩm.
- com.hrm.hrm.service.G1WorkflowIntegrationTest: 18 fail / 0 error — Môi trường: DB guard chặn cấu hình hrm_db.
- com.hrm.hrm.service.PersonnelWorkflowLockConcurrencyTest: 1 fail / 0 error — Môi trường: DB guard chặn cấu hình hrm_db.
- com.hrm.security.PermissionAuthorizationTests: 0 fail / 1 error — Fixture thiếu bean/strict stub cũ.
- com.hrm.security.PersonnelScopePrivacyIntegrationTest: 20 fail / 0 error — Môi trường: DB guard chặn cấu hình hrm_db.
- com.hrm.service.EmployeeRehireSqlServerIntegrationTest: 1 fail / 0 error — Môi trường: DB guard chặn cấu hình hrm_db.
- com.hrm.service.WorkShiftDurationTest: 2 fail / 0 error — Cần đối chiếu fixture và nghiệp vụ; chưa chứng minh lỗi sản phẩm.
- com.hrm.service.WorkShiftPermissionTest: 1 fail / 0 error — Cần đối chiếu fixture và nghiệp vụ; chưa chứng minh lỗi sản phẩm.
- com.hrm.service.WorkShiftValidationTest: 10 fail / 0 error — Cần đối chiếu fixture và nghiệp vụ; chưa chứng minh lỗi sản phẩm.
- com.hrm.service.WorkShiftValidCasesTest: 0 fail / 4 error — Cần đối chiếu fixture và nghiệp vụ; chưa chứng minh lỗi sản phẩm.

Không sửa test để xanh. G1EndpointAccessTest hai assertion403 nhận200 cần đối chiếu quyền mới; chưa tự kết luận lộ dữ liệu từ test fixture đó.

## Git status đầu phiên

```text
M HRM_System/src/main/java/com/hrm/repository/EmployeeQualificationRepository.java
 M HRM_System/src/main/java/com/hrm/service/EmployeeQualificationService.java
 M HRM_System/src/main/java/com/hrm/service/EmployeeService.java
 M HRM_System/src/main/resources/application-hrm-db.properties
 M HRM_System/src/test/java/com/hrm/hrm/service/EmployeeDetailServiceTest.java
 M frontend/src/app/pages/catalog/catalog.html
 M frontend/src/app/pages/catalog/catalog.ts
 M frontend/src/app/pages/contracts/contracts.html
 M frontend/src/app/pages/departments/departments.html
 M frontend/src/app/pages/employees/employees.html
 M frontend/src/app/pages/employees/employees.ts
 M frontend/src/app/pages/personnel-workspace/personnel-workspace.html
 M frontend/src/app/pages/personnel-workspace/personnel-workspace.ts
 M frontend/src/app/services/employee.service.ts
 M frontend/src/app/services/language.service.ts
 M frontend/src/app/shared/page-header.ts
 M frontend/src/styles.css
?? HRM_DB_GIO_NGHI_TANG_CA_PHAN_QUYEN_HR.sql
?? HRM_System/src/main/java/com/hrm/controller/PersonnelSearchController.java
?? HRM_System/src/main/java/com/hrm/util/PersonnelSearch.java
?? HRM_System/src/test/java/com/hrm/service/PersonnelSearchControllerTest.java
?? HRM_System/src/test/java/com/hrm/service/PersonnelSearchTest.java
```

## Git status cuối lúc kiểm hash

```text
 M HRM_System/src/main/java/com/hrm/repository/EmployeeQualificationRepository.java
 M HRM_System/src/main/java/com/hrm/service/EmployeeQualificationService.java
 M HRM_System/src/main/java/com/hrm/service/EmployeeService.java
 M HRM_System/src/main/resources/application-hrm-db.properties
 M HRM_System/src/test/java/com/hrm/hrm/service/EmployeeDetailServiceTest.java
 M frontend/src/app/pages/catalog/catalog.html
 M frontend/src/app/pages/catalog/catalog.ts
 M frontend/src/app/pages/contracts/contracts.html
 M frontend/src/app/pages/departments/departments.html
 M frontend/src/app/pages/employees/employees.html
 M frontend/src/app/pages/employees/employees.ts
 M frontend/src/app/pages/personnel-workspace/personnel-workspace.html
 M frontend/src/app/pages/personnel-workspace/personnel-workspace.ts
 M frontend/src/app/services/employee.service.ts
 M frontend/src/app/services/language.service.ts
 M frontend/src/app/shared/page-header.ts
 M frontend/src/styles.css
?? HRM_DB_GIO_NGHI_TANG_CA_PHAN_QUYEN_HR.sql
?? HRM_System/src/main/java/com/hrm/controller/PersonnelSearchController.java
?? HRM_System/src/main/java/com/hrm/util/PersonnelSearch.java
?? HRM_System/src/test/java/com/hrm/service/PersonnelSearchControllerTest.java
?? HRM_System/src/test/java/com/hrm/service/PersonnelSearchTest.java
?? qa_tester/

```

Mọi thay đổi source/test nêu trong hai status đã tồn tại trước QA; chỉ qa_tester mới được tạo trong lượt này. Các build output ignored không tính là source. Các server QA tạm đã được dừng; chưa commit/push báo cáo.
