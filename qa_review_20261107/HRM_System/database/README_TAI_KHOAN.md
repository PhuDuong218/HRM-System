# Tài khoản thử nghiệm

Mở **SQL Server Management Studio (SSMS)**, kết nối SQL Server trên máy bạn và chọn **New Query**. Chạy đoạn sau để xem tất cả tài khoản của ba vai trò:

```sql
USE hrm_db;
GO
SELECT a.username, r.role_code, a.is_active, e.employment_status
FROM dbo.accounts AS a
JOIN dbo.roles AS r ON r.role_id = a.role_id
JOIN dbo.employees AS e ON e.employee_id = a.employee_id
WHERE r.role_code IN ('ADMIN', 'HR', 'EMPLOYEE')
ORDER BY r.role_code, a.username;
```

Sau đó, chạy đoạn dưới để **mở khóa và đặt lại mật khẩu cho tất cả tài khoản thuộc ba vai trò có nhân viên đang làm việc**. Mật khẩu mới của các tài khoản này sẽ là `Admin@123`:

```sql
USE hrm_db;
GO

UPDATE a
SET a.is_active = 1,
    a.password_hash = '$2a$10$JfTUZwfQEtoJjVAR9ZgMhuYhB9HbGkVJMHkRkbbHvcIoVhcLW.V/C',
    a.token_version = a.token_version + 1,
    a.updated_at = SYSUTCDATETIME()
FROM dbo.accounts AS a
JOIN dbo.employees AS e ON e.employee_id = a.employee_id
JOIN dbo.roles AS r ON r.role_id = a.role_id
WHERE r.role_code IN ('ADMIN', 'HR', 'EMPLOYEE')
  AND e.employment_status = 'DANG_LAM';

SELECT @@ROWCOUNT AS so_tai_khoan_da_cap_nhat;

SELECT a.username, r.role_code, a.is_active, e.employment_status
FROM dbo.accounts AS a
JOIN dbo.roles AS r ON r.role_id = a.role_id
JOIN dbo.employees AS e ON e.employee_id = a.employee_id
WHERE r.role_code IN ('ADMIN', 'HR', 'EMPLOYEE')
ORDER BY r.role_code, a.username;
```

Với tài khoản có `is_active = 1` và `employment_status = DANG_LAM`, dùng `username` trong kết quả và mật khẩu `Admin@123`. Nhân viên đã nghỉ không được cập nhật và không thể đăng nhập theo điều kiện hiện tại của backend.

Trong VS Code, chạy backend tại thư mục `HRM_System`:

```powershell
.\mvnw.cmd spring-boot:run
```

Mở terminal khác tại thư mục `frontend`, chạy:

```powershell
npm.cmd start
```

Mở `http://localhost:4200/login` để đăng nhập bằng tài khoản ADMIN hoặc HR. Tài khoản EMPLOYEE dùng API đăng nhập chung để kiểm thử và dành cho ứng dụng nhân viên; không vào cổng web quản lý. Các tài khoản phải có sẵn trong database; nếu thiếu, kiểm tra lại dữ liệu mẫu.

Chỉ dùng đoạn SQL và mật khẩu này cho **database thử nghiệm cục bộ**. Lệnh thay mật khẩu cũ và thu hồi các phiên đăng nhập cũ của tất cả tài khoản được cập nhật. Mật khẩu gốc không thể đọc ngược từ mã băm BCrypt trong database.
