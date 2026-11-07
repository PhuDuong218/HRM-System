# Cấu hình chức năng quên mật khẩu

Backend tự tạo bảng `password_recovery` khi khởi động trên SQL Server nếu bảng chưa tồn tại; không cần chạy file SQL thủ công. Tài khoản kết nối SQL Server cần quyền tạo bảng trong schema `dbo`, và database phải có sẵn bảng `dbo.accounts`. Backend sẽ báo lỗi khởi động nếu không thể tạo bảng. Có thể tắt việc này bằng `app.recovery.initialize-schema=false` khi quản trị database tự áp dụng migration.

Để gửi bằng Gmail, bật xác minh hai bước và tạo mật khẩu ứng dụng cho tài khoản Gmail. Đặt các biến môi trường trước khi khởi động backend (không đưa mật khẩu vào source code):

```powershell
$env:SMTP_USERNAME = 'your-email@gmail.com'
$env:SMTP_PASSWORD = 'mat-khau-ung-dung'
```

Các biến chỉ áp dụng cho cửa sổ PowerShell hiện tại và các tiến trình khởi động từ cửa sổ đó. Gmail SMTP (`smtp.gmail.com`, cổng 587, STARTTLS) và địa chỉ gửi mặc định đã được cấu hình; có thể đặt `SMTP_FROM` nếu cần địa chỉ khác được nhà cung cấp cho phép. Với nhà cung cấp SMTP khác, có thể ghi đè các biến hoặc cấu hình trong `src/main/resources/application-local.properties`:

```properties
spring.mail.host=${SMTP_HOST}
spring.mail.port=${SMTP_PORT:587}
spring.mail.username=${SMTP_USERNAME}
spring.mail.password=${SMTP_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000
app.recovery.mail-from=${SMTP_FROM}
```

Không đưa thông tin đăng nhập SMTP vào Git. Khởi động lại backend từ cửa sổ có các biến môi trường; kiểm tra email của nhân viên và gửi thử với hộp thư được phép nhận. Nếu SMTP chưa cấu hình, API trả lỗi 503; nếu máy chủ email từ chối gửi, lỗi được ghi vào log backend.

Tài khoản phải hoạt động, nhân viên còn làm việc và hồ sơ có email. Hiện hệ thống chưa có trạng thái xác minh email: quản trị viên cần bảo đảm địa chỉ hồ sơ đúng và chỉ người được ủy quyền mới được sửa. API yêu cầu mã trả cùng phản hồi cho tên đăng nhập không đủ điều kiện. Mã gồm 8 chữ số, hết hạn sau 10 phút, tối đa 5 lần nhập sai, có thể yêu cầu mã mới sau 60 giây; đổi mật khẩu sẽ thu hồi các phiên JWT cũ. Khi triển khai công khai cần bổ sung giới hạn lưu lượng theo IP tại proxy để giảm lạm dụng và gửi email hàng loạt.
