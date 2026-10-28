# Database cho G1

G1 dùng schema SQL Server mà nhóm đã thiết kế. Không tạo database nghiệp vụ thứ hai cho Web/Mobile.

## Máy đã có hrm_test

Không chạy lại script khởi tạo. Migration `migrations/001_department_heads.sql` phải được áp dụng trong đúng database HRM. Script có thể chạy lại, kiểm tra dữ liệu chồng hiệu lực trước khi tạo ràng buộc, không xóa lịch sử. Nếu báo dữ liệu không hợp lệ, phải đối chiếu dữ liệu; không tắt trigger để vượt qua.

## Máy mới, database thử nghiệm trống

1. Mở SSMS, kết nối SQL Server của mình.
2. Chạy `000_create_hrm_test.sql` chỉ để khởi tạo database thử nghiệm trống. Đây là bản SQL gốc người dùng cung cấp, chỉ đổi database đích từ HRMCenter sang hrm_test. Có đầy đủ seed/schema tham chiếu của hệ thống; không chạy trên database đã có bảng.
3. Chọn database hrm_test, chạy `migrations/001_department_heads.sql`.
4. Tạo `src/main/resources/application-local.properties` theo cấu hình riêng của máy, gồm datasource URL/username/password. Không commit file này.
5. Chạy `run-g1.ps1 -Mode test` theo hướng dẫn ở G1_BAN_GIAO.md.

Không có bước DROP/TRUNCATE hoặc cập nhật hàng loạt dữ liệu cũ. Script baseline chủ động dừng nếu database đã có bảng.

Các tài khoản seed của SQL gốc có thể đang khóa và mật khẩu mẫu không dùng để đăng nhập thật. Server Postman G1 tạo hai actor test riêng, bật khi server chạy và khóa khi dừng. Nó không mở khóa các tài khoản mẫu/cá nhân của nhóm.

Schema bổ sung của các module ngoài G1 (ví dụ cột xóa mềm bằng cấp) cần migration riêng khi bàn giao module đó; không dùng kết quả G1 để khẳng định các module ngoài phạm vi đã tái tạo đầy đủ.
