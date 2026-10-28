# Danh sách thay đổi của lượt triển khai G1

Phạm vi: backend nhân viên, phòng ban, chức vụ, điều chuyển. Giữ các API cũ, không xóa file hoặc reset thay đổi có sẵn của Xuân Anh. Các file được nêu là thay đổi của lượt này; git status còn chứa nhiều file đã thay đổi từ trước.

## File mới của lượt này

- `HRM_System/src/main/java/com/hrm/config/BusinessValidationConfiguration.java`: validation dùng đồng hồ nghiệp vụ.
- `HRM_System/src/main/java/com/hrm/controller/G1AuditController.java`: API đọc audit có quyền và phân trang.
- `HRM_System/src/main/java/com/hrm/service/CatalogAuditService.java`: điều phối audit phòng ban/chức vụ.
- `HRM_System/src/main/java/com/hrm/service/EmployeeTransferAuditService.java`: điều phối audit điều chuyển.
- `HRM_System/src/test/java/com/hrm/hrm/service/G1WorkflowIntegrationTest.java`: 18 test SQL Server cho các luồng G1 và rollback.
- `HRM_System/src/test/java/com/hrm/hrm/service/G1EndpointAccessTest.java`: kiểm tra 401/403 trên nhóm API danh mục/phân công/audit.
- `HRM_System/src/test/java/com/hrm/hrm/service/G1PostmanServer.java`: server test loopback, không nằm trong jar ứng dụng.
- `HRM_System/run-g1.ps1`: chạy test, server Postman hoặc gửi tín hiệu dừng.
- `HRM_System/test-tools/build-g1-postman.mjs`: tái tạo collection/environment template.
- `HRM_System/test-tools/g1.postman_collection.json`: 149 request G1.
- `HRM_System/test-tools/g1.postman_environment.json`: template không chứa token.
- `HRM_System/database/000_create_hrm_test.sql`: bản baseline SQL người dùng cung cấp, đổi database đích phục vụ bàn giao máy mới, không tự thực thi.
- `HRM_System/database/README_G1.md`, `G1_BAN_GIAO.md`, file danh sách này: hướng dẫn và phạm vi bàn giao.

## File được bổ sung/chỉnh trong lượt này

| Nhóm | File | Nội dung |
| --- | --- | --- |
| Controller | EmployeeController | PUT đi qua luồng có audit; giữ các URL cũ |
| Controller | DepartmentController, PositionController | Quyền, audit, tìm kiếm/lọc; POST trả 201 |
| Controller | EmployeeAssignmentController | Quyền cho đọc/điều chuyển; điều chuyển đi qua audit |
| Service | EmployeeCreationAuditService | Thêm điều phối PUT hồ sơ có audit |
| Service | EmployeeAuditService | Thêm audit PUT, điều chuyển và danh mục |
| Service | EmployeeService, EmployeeAssignmentService | UTC cho thời điểm ghi; chuẩn hóa tên/liên hệ khi tạo/sửa |
| Service | DepartmentService, PositionService | Mã trùng, tìm kiếm/lọc; chặn ngừng dùng khi còn phân công |
| Service | AssignmentGuardService | Kiểm tra tham chiếu hiện tại/tương lai cho danh mục |
| Service | DepartmentHeadAccessService | Thông báo đăng nhập phù hợp quản lý nhân sự |
| Repository | DepartmentRepository, PositionRepository | Truy vấn tìm kiếm/lọc; kiểm tra mã phòng trùng |
| DTO | EmployeeRequestDTO, EmployeeTransferRequestDTO | ID phòng/chức vụ phải dương |
| Test | EmployeeCreationAuditServiceTest, EmployeeTerminationControllerTest | Sửa lỗi cú pháp đã có trong code đang dán |
| Test | EmployeeAccessControllerTest | Kiểm tra PUT gọi đúng luồng audit |
| Test | DepartmentHeadIntegrationTest | Cập nhật constructor controller theo dependency mới |
| Build | HRM_System/pom.xml | Không đóng gói datasource local vào jar; local run/test vẫn dùng được cấu hình riêng |

## Các phần không được thay đổi trong lượt này

- Không sửa nội dung HRM_System.zip, không đóng gói bí mật local.
- Không sửa module hợp đồng của thành viên khác, bằng cấp, nghỉ phép hoặc đơn từ.
- Không sửa các trang Angular; không xem placeholder là giao diện hoàn chỉnh.
- Không commit/push/merge hay reset thay đổi cũ.
- Không thực thi baseline SQL trên database hiện có và không hard-delete hồ sơ để dọn test.

## Dữ liệu test

JUnit dùng rollback cho fixture nghiệp vụ. Postman chạy HTTP thật, tạo G1PM-* và giữ lịch sử. Hai actor G1-POSTMAN-* chỉ phục vụ server test và bị khóa khi dừng bình thường. Mật khẩu fixture không dùng đăng nhập thật; token runtime ngẫu nhiên chỉ nằm trong target, không đưa vào Git.
