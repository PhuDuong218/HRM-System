# TIẾN ĐỘ HOÀN THIỆN BACKEND HRM

Ngày cập nhật: 2026-10-16.

## 1. Phạm vi và quyền thực hiện

- Người dùng đã chuyển từ học tự gõ code sang cho phép AI trực tiếp phát triển backend, build, test, sửa lỗi và cập nhật tài liệu.
- Giữ nguyên Java/Spring Boot/Maven/JPA/SQL Server. Web Angular và Android dùng chung backend; không sửa Web/Mobile trong nhiệm vụ này.
- Không xóa dữ liệu, không chạy lại script khởi tạo trên database đang có. Thay đổi schema cần migration an toàn và người dùng xác nhận trước khi chạy.
- Không tự xác lập công thức lương, bảo hiểm hoặc chính sách nghiệp vụ chưa được xác nhận.

## 2. Hiện trạng workspace thực tế

Workspace hiện tại khác bản trong hội thoại trước:

| Nội dung | Hiện tại |
| --- | --- |
| Project backend | HRM_System/ |
| Package gốc | com.hrm |
| Công nghệ | Java 21, Spring Boot 4.1.1, Maven Wrapper 3.9.16 |
| SQL tham chiếu có sẵn | hrm_db.sql ở gốc workspace; UTF-16, 33 bảng |
| Cổng cấu hình | 2005 |
| Cấu hình môi trường | Profile local và application-local.properties bị ignore |
| Hibernate | ddl-auto=none, show-sql=true; chưa chuyển sang validate |
| Tài liệu đang thiếu | docs/PROJECT_SPEC.md, docs/IMPLEMENTATION_PLAN.md |
| Đường dẫn cũ không tồn tại | backend/, database/create_database.sql, docs/LEARNING_PROGRESS.md |

Không tạo lại project hoặc đổi package để khớp hội thoại cũ. Không đọc/in mật khẩu cấu hình local ra báo cáo.

## 3. Blocker và quyết định người dùng

Đã hỏi cách xử lý hai tài liệu gốc không còn trong workspace. Người dùng chọn **tự khôi phục tài liệu gốc**.

Vì yêu cầu bắt buộc đọc đặc tả và kế hoạch trước khi triển khai, phần lập trình nghiệp vụ đang chờ hai file được khôi phục. Không dùng nội dung suy đoán làm đặc tả thay thế. Khi tài liệu xuất hiện, đọc đầy đủ và đối chiếu project hiện tại trước khi tiếp tục.

Đến thời điểm báo cáo chỉ kiểm tra baseline và tạo tài liệu này; chưa sửa mã nguồn, pom, cấu hình, database hoặc thực hiện migration. Maven có tải dependencies và ghi kết quả build/test vào target/cache.

## 4. Đánh giá từng nhóm chức năng

Các trạng thái dưới đây dựa trên rà soát code, không đồng nghĩa đã kiểm thử API.

| Nhóm | Code hiện có | Phần còn thiếu / cần xác minh |
| --- | --- | --- |
| Authentication/authorization | Chưa có dependency Security/JWT hoặc implementation | Login/logout, đổi mật khẩu, token revocation, tài khoản/vai trò/quyền, owner scope, audit |
| Nhân viên | Entity, DTO, repository, service/controller; danh sách/chi tiết/thêm/sửa/nghỉ việc | Phân trang/filter, validation nghiệp vụ, self API, audit, khóa tài khoản khi nghỉ; regression |
| Phòng ban | DTO CRUD và ngừng sử dụng thay hard delete | Phân quyền, pagination/filter, audit, lỗi 404/409, regression |
| Chức vụ | CRUD/ngừng sử dụng | Controller hiện nhận/trả Entity; cần DTO, validation, phân quyền, audit |
| Phân công/điều chuyển | Lịch sử, JOIN FETCH, transaction và khóa nhân viên khi điều chuyển | Sai quy ước khoảng hiệu lực; cần test đồng thời và đối chiếu index/schema |
| Trình độ | Chưa có module Java | Catalog và hồ sơ bằng cấp theo đặc tả |
| Hợp đồng | Chưa có module Java | Lịch sử, điều kiện lương, kết thúc/gia hạn, sắp hết hạn |
| Lịch/chấm công | Chưa có module Java | Ca/lịch/overlap, check-in/out, xác nhận và khóa công |
| Đơn/tăng ca | Chưa có module Java | Self scope, luồng duyệt, không tự duyệt, điều kiện giờ hợp lệ |
| Phụ cấp/quyết định | Chưa có module Java | Hiệu lực, phê duyệt, lịch sử và tác động lương |
| Lương/phiếu lương | Chưa có module Java | Chờ quy tắc chính thức; kỳ/phiên bản/chốt/công bố/scope; chưa xác nhận schema thanh toán |
| Báo cáo | Chưa có module Java | Tổng hợp phân quyền theo nguồn dữ liệu và kỳ |
| Hạ tầng/audit | Có GlobalExceptionHandler và GET /hello | Health, OpenAPI, lỗi thống nhất an toàn, audit giao dịch, kiểm thử sâu |

## 5. Vấn đề phát hiện khi rà soát

1. Chưa có Spring Security/JWT: API hiện chưa có kiểm soát vai trò và quyền sở hữu.
2. Employee.employeeCode dùng length=255 trong Java trong khi SQL là varchar(50); email Java 255 so với SQL nvarchar(254); employmentStatus cần đối chiếu SQL varchar(20). Các chuỗi nvarchar cần mapping Unicode phù hợp trước khi bật validate.
3. EmployeeAssignmentService kết thúc phân công bằng transferDate.minusDays(1); SQL CHECK yêu cầu effective_to > effective_from. Với khoảng hiệu lực [start, end), cách này tạo khoảng trống và có thể vi phạm CHECK khi chuyển sau một ngày. Chưa sửa hoặc ghi dữ liệu thử.
4. PositionController nhận/trả Entity, có nguy cơ cho client can thiệp thuộc tính hệ thống; cần DTO giới hạn trường.
5. GlobalExceptionHandler trả RuntimeException thành HTTP 500 kèm ex.getMessage(); cần phân biệt lỗi nghiệp vụ 400/404/409 và tránh lộ thông tin nội bộ.
6. Query danh sách nhân viên lọc assignment.effectiveTo IS NULL có thể bỏ nhân viên chỉ còn phân công lịch sử; cần regression test.
7. Bản SQL export có 33 bảng nhưng không có CREATE TRIGGER hoặc UX_assignments_open. Đây là kết luận về **file export**, chưa phải kết luận database thực tế thiếu các đối tượng đó. Cần đọc metadata trực tiếp, không tự tạo index/trigger.
8. Nhiều thao tác còn thiếu audit actor và audit log cùng transaction. BaseEntity.rowVersion cần kiểm thử optimistic concurrency và giá trị do SQL Server sinh trước khi khẳng định cơ chế ghi an toàn.
9. README vẫn có các đoạn cấu hình cũ và nhắc database/hrm_db.sql chưa tồn tại; cần chuẩn hóa theo project thật.

## 6. Kết quả kiểm thử baseline

Ngày 2026-10-16, chạy tại HRM_System/:

- Lần đầu chạy wrapper trong sandbox lỗi Cannot index into a null array; chạy lại ngoài sandbox theo approval để Maven tải dependencies đã thành công.
- Lệnh `mvnw.cmd test`: **BUILD SUCCESS**.
- HrmApplicationTests.contextLoads: **1 test, 0 failure, 0 error, 0 skipped**.
- Test này chỉ xác nhận Spring application context khởi tạo với cấu hình hiện tại; không chứng minh CRUD/validation/phân quyền hoặc công thức nghiệp vụ đúng.
- Chưa chạy kiểm thử HTTP, ghi database, owner scope, migration hoặc regression nghiệp vụ trong phiên rà soát này.
- Lệnh `mvnw.cmd -B -ntp package`: **BUILD SUCCESS**, chạy lại 1 test với 0 failure/error; tạo HRM_System/target/hrm-0.0.1-SNAPSHOT.jar.

## 7. Kế hoạch dự kiến, cần đối chiếu tài liệu gốc

Mỗi phase hoàn thành phải build/test, kiểm tra quyền/input không hợp lệ và regression trước khi chuyển phase. Không phát triển nhiều module nghiệp vụ đồng thời.

| Phase | Mục tiêu | Cách nghiệm thu |
| --- | --- | --- |
| 0 | Khôi phục/đọc đặc tả, chốt trạng thái code/schema và baseline | Tài liệu hiện diện; bảng đối chiếu mapping/schema; build và baseline tests |
| 1 | Hạ tầng lỗi, cấu hình, health và kiểm thử | Health đúng; lỗi không lộ bí mật; validate schema; test an toàn |
| 2 | Authentication/JWT và tài khoản | Login đúng/sai, khóa/nghỉ việc, logout/đổi mật khẩu vô hiệu token cũ; không lộ hash |
| 3 | Authorization, quyền và audit | ADMIN/HR/EMPLOYEE A/B, đổi ID/body/filter không vượt scope; audit cùng transaction |
| 4 | Phòng ban hoàn chỉnh | DTO/validation, pagination/filter, unique và ngừng dùng; regression |
| 5 | Chức vụ hoàn chỉnh | Cùng tiêu chí catalog, không nhận/trả Entity |
| 6 | Trình độ | Catalog/bằng cấp đúng quan hệ và quyền |
| 7 | Nhân viên và phân công | Self scope, tìm kiếm/phân trang, lịch sử/điều chuyển, soft termination và concurrency |
| 8 | Hợp đồng | Lịch sử bất biến, hiệu lực/gia hạn/kết thúc, thông tin lương theo thời gian |
| 9 | Lịch làm việc | Không overlap, ca qua đêm, self scope, lịch không tự sinh công |
| 10 | Chấm công | Check-in/out, giờ thực tế, chỉnh có audit, chốt công và concurrency |
| 11 | Đơn, làm lần lượt nghỉ phép rồi nghỉ việc | Trạng thái/ngày hiệu lực, subtype, không tự duyệt, quyền sở hữu |
| 12 | Tăng ca | Duyệt và xác nhận giờ thực tế; chưa duyệt không đủ điều kiện trả lương |
| 13 | Phụ cấp | Hiệu lực, không trùng khoản, self scope |
| 14 | Khen thưởng/kỷ luật | Lịch sử/phê duyệt, phân biệt chỉ ghi hồ sơ với tác động tiền |
| 15 | Lương/phiếu lương | Chốt quy tắc trước tính; đối chiếu tay; snapshot/phiên bản; chốt bất biến; phiếu cá nhân |
| 16 | Báo cáo và nghiệm thu | Tổng hợp khớp nguồn, bảo mật file, OpenAPI, hướng dẫn chạy và bộ regression |

## 8. Bước tiếp theo

Chờ người dùng khôi phục docs/PROJECT_SPEC.md và docs/IMPLEMENTATION_PLAN.md. Khi có, kiểm tra lại toàn bộ yêu cầu với HRM_System/ và hrm_db.sql; hoàn thiện kế hoạch trước khi sửa backend. Không tự tái tạo hai tài liệu gốc theo phỏng đoán.

## 9. Kiểm tra lại theo yêu cầu tiếp tục ngày 2026-10-16

- Đã đọc tiến độ và kiểm tra Git: `git diff --stat` không có thay đổi tracked; `git status --short` chỉ có `?? docs/`.
- Đã kiểm tra lại pom.xml và DepartmentController: project thực tế vẫn là HRM_System/, package com.hrm, Spring Boot 4.1.1; controller hiện dùng /departments. Không có backend/src/main/java/com/hrmcenter/ trong workspace đang truy cập.
- Hai lệnh Test-Path với docs/PROJECT_SPEC.md và docs/IMPLEMENTATION_PLAN.md đều trả False. Thư mục docs trên đĩa chỉ có BACKEND_PROGRESS.md. Hai tài liệu có thể đang mở nhưng chưa lưu hoặc nằm trong workspace khác; chưa thể xác định từ trạng thái IDE.
- Không có tên hai tài liệu trong danh sách file Git được theo dõi, nên không thể khôi phục bằng checkout từ bản tracked hiện tại.
- Chưa có module nghiệp vụ nào được xác nhận hoàn thành đầy đủ bằng bộ kiểm thử. Baseline gần nhất vẫn là Maven package thành công và contextLoads 1 test, 0 failure/error; không chạy lặp lại test này vì code tracked chưa thay đổi và blocker còn nguyên.
- Module cần triển khai tiếp sau hạ tầng là Authentication/Authorization, nhưng phải đọc tài liệu gốc theo lựa chọn khôi phục của người dùng trước.
- Chưa sửa backend, Web/Android hoặc database. Cần người dùng lưu hai tài liệu vào đúng thư mục docs của workspace D:/Duong/CongNghePhanMem/HRM-System để gỡ blocker.
