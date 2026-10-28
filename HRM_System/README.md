# HRM System — Hệ thống quản lý nhân sự và tính lương cho trung tâm giáo dục

> **Đồ án môn học | Backend đang phát triển**  
> Mục tiêu: xây dựng hệ thống quản lý nhân sự cho trung tâm giáo dục, tập trung vào nghiệp vụ **tính lương**. Thư mục này chứa backend Java Spring Boot; Web Angular nằm ở `../frontend`.

**Cập nhật 23/10/2026:** Đã bổ sung quản lý trưởng phòng theo lịch sử phân công, migration, audit và trang Phòng ban. Xem [hướng dẫn triển khai và thử API](C:/NguyenXuanAnh/Git-Nhom-HRM/HUONG_DAN_TRUONG_PHONG.md). Các API phòng ban yêu cầu phiên ADMIN/HR có quyền EMPLOYEE_MANAGE; repo chưa có login/JWT nên gọi chưa xác thực nhận 401. Các hướng dẫn cũ bên dưới cần đối chiếu với cập nhật này.

## 1. Tổng quan

Hệ thống hướng tới quản lý hồ sơ nhân viên, phòng ban, chức vụ, lịch sử phân công và điều chuyển nhân viên; tiếp theo là hợp đồng, lịch làm việc, chấm công, nghỉ phép, tăng ca, phụ cấp, phân quyền và tính lương theo kỳ.

**Lưu ý về tiến độ:** Danh sách trên mô tả **phạm vi dự kiến của đồ án**, không có nghĩa tất cả chức năng đã được lập trình xong. Các API nhân viên, phòng ban, chức vụ và phân công đã được phát triển/kiểm thử ở mức cơ bản.

### Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 (theo `pom.xml`; thống nhất JDK 21 trong nhóm) |
| Backend | Spring Boot 4.1.1, Spring Web MVC |
| Truy cập CSDL | Spring Data JPA / Hibernate |
| Cơ sở dữ liệu | Microsoft SQL Server (`hrm_db`) |
| Build | Maven Wrapper (`mvnw.cmd`) |
| API | REST, JSON |
| Cổng backend | `2005` |
| Công cụ đề xuất | VS Code hoặc IntelliJ IDEA, SSMS, Thunder Client/Postman |

## 2. Cấu trúc thư mục

```text
HRM_System/
├── src/
│   └── main/
│       ├── java/com/hrm/
│       │   ├── controller/       # Nhận HTTP request, trả response
│       │   ├── service/          # Xử lý nghiệp vụ
│       │   ├── repository/       # Truy cập dữ liệu bằng JPA
│       │   ├── model/            # Entity ánh xạ bảng SQL Server
│       │   ├── dto/              # Dữ liệu nhận/trả qua API
│       │   ├── exception/        # Xử lý lỗi
│       │   └── HrmApplication.java
│       └── resources/
│           ├── application.properties          # Cấu hình chung (được commit)
│           └── application-local.properties    # Cấu hình riêng (KHÔNG commit)
├── database/
│   └── hrm_db.sql               # Script schema (cần đưa vào repo)
├── .mvn/wrapper/
├── .gitignore
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

> `database/` và `application-local.properties` là các phần **cần tạo/bổ sung** khi chuẩn hóa repository. Chúng có thể chưa tồn tại trong bản clone hiện tại.

## 3. Chuẩn bị trên máy thành viên

1. **Git** — tải và cập nhật source code.
2. **JDK 21** — kiểm tra bằng `java -version`. Nếu cài nhiều JDK, đặt `JAVA_HOME` trỏ đến JDK 21.
3. **VS Code + Extension Pack for Java** hoặc IntelliJ IDEA.
4. **SQL Server Database Engine** — nếu dùng database cục bộ; bảo đảm SQL Server đang chạy và cho phép kết nối TCP/IP.
5. **SQL Server Management Studio (SSMS)** — để tạo/import và kiểm tra database.
6. **Thunder Client hoặc Postman** — tùy chọn để thử API.

**Không cần cài Maven riêng** vì dự án đã có Maven Wrapper. Máy cần kết nối Internet ở lần đầu để tải dependencies.

## 4. Clone source code

```powershell
git clone https://github.com/PhuDuong218/HRM-System.git
cd HRM-System
```

> Nếu nhóm đang sử dụng repository khác hoặc thư mục gốc có tên `HRM_System`, hãy thay URL/tên thư mục tương ứng. Tất cả lệnh Maven phải chạy **tại thư mục chứa `pom.xml`**.

Kiểm tra môi trường:

```powershell
java -version
.\mvnw.cmd -version
```

## 5. Tạo database trên SQL Server

### 5.1. Tạo database

Mở **SSMS** → kết nối SQL Server trên máy → **New Query** → chạy:

```sql
IF DB_ID(N'hrm_db') IS NULL
BEGIN
    CREATE DATABASE hrm_db;
END;
GO
```

### 5.2. Import schema

Trong SSMS, mở `database/hrm_db.sql` bằng **File → Open → File**, kiểm tra đang kết nối đúng SQL Server rồi nhấn **Execute**.

- Script `hrm_db.sql` đã cung cấp sử dụng `USE [hrm_db]`, tạo khoảng **33 bảng** và các ràng buộc, nhưng **không có dữ liệu mẫu**.
- File SQL export từ SSMS có thể ở dạng **UTF-16**; nên mở/chạy trực tiếp bằng SSMS, tránh sửa encoding tùy tiện.
- Chỉ chạy script tạo schema trên **database mới/rỗng**. Không chạy lại toàn bộ script trên database đã có bảng, vì có thể báo `already exists`.
- Không xóa database đang chứa dữ liệu của nhóm để thử lại.

Kiểm tra:

```sql
USE hrm_db;
GO
SELECT COUNT(*) AS so_bang
FROM sys.tables
WHERE is_ms_shipped = 0;
GO
SELECT name FROM sys.tables ORDER BY name;
GO
```

**Cần kiểm tra thêm:** Bản schema gửi để soạn README chưa thấy index `UX_assignments_open` (index từng gây lỗi khi điều chuyển nhân viên). Trước khi dùng chung, người phụ trách DB cần đối chiếu database nguồn và xuất **đầy đủ schema/index** hoặc bổ sung migration đã kiểm thử. Việc đếm đủ bảng chưa đảm bảo hai database giống nhau.

### 5.3. Tài khoản SQL Server

Có thể dùng SQL Server Authentication với tài khoản được cấp quyền truy cập `hrm_db`. Với môi trường học tập cục bộ, nếu dùng `sa` thì phải bật Mixed Mode, bật tài khoản và đặt mật khẩu. Về lâu dài nên dùng tài khoản ứng dụng riêng có quyền phù hợp, không dùng `sa` cho môi trường triển khai chung.

## 6. Cấu hình riêng cho mỗi máy — CHỈ LÀM MỘT LẦN

**Không cần nhập `$env:DB_URL`, `$env:DB_USERNAME`, `$env:DB_PASSWORD` mỗi lần chạy.** Sử dụng Spring Profile `local`.

### 6.1. File cấu hình chung (được commit)

Mở `src/main/resources/application.properties`, đặt nội dung:

```properties
spring.application.name=hrm
server.port=2005

# Nạp thêm cấu hình riêng của từng máy
spring.profiles.active=local

spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
```

> Không ghi username/password thật vào file này.

### 6.2. File cấu hình riêng (không commit)

Tạo file **`src/main/resources/application-local.properties`**:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=hrm_db;encrypt=false;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=THAY_BANG_MAT_KHAU_MAY_BAN
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
```

Thay mật khẩu, tên tài khoản, host/cổng theo SQL Server trên **máy của bạn**. `encrypt=false` chỉ là ví dụ dành cho môi trường phát triển cục bộ; nếu dùng server chung, cần cấu hình kết nối bảo mật theo môi trường thực tế.

Để thành viên mới biết phải tạo file nào, nhóm nên commit thêm **`src/main/resources/application-local.properties.example`** với nội dung tương tự nhưng mật khẩu là `YOUR_PASSWORD`. Mỗi người copy file mẫu thành `application-local.properties` và sửa thông tin riêng.

### 6.3. `.gitignore`

Đảm bảo file `.gitignore` ở **thư mục gốc repo** có các dòng:

```gitignore
# Cấu hình bí mật của từng máy
**/application-local.properties
.env
.env.*
!.env.example

# Build / IDE
**/target/
.vscode/
.idea/
*.log
```

> `.gitignore` chỉ bỏ qua file **chưa được Git theo dõi**. Nếu file `application-local.properties` đã bị commit trước đó, chạy `git rm --cached src/main/resources/application-local.properties` tại thư mục chứa `pom.xml` nếu đó cũng là gốc repo; nếu repo nằm cao hơn, cần dùng đúng đường dẫn tính từ gốc repo. Sau đó commit thay đổi. **Không commit mật khẩu thật**, kể cả trong file mẫu.

**Bảo mật:** `application.properties` cũ từng chứa mật khẩu SQL Server dạng rõ. Nếu đã đẩy lên GitHub, hãy **đổi mật khẩu đó**; sửa file trong commit mới không xóa thông tin khỏi lịch sử Git.

## 7. Chạy backend

Mở terminal **PowerShell** tại thư mục có `pom.xml`:

```powershell
.\mvnw.cmd spring-boot:run
```

Đợi xuất hiện các dòng tương tự:

```text
Tomcat started on port 2005 (http)
Started HrmApplication
```

Thử API bằng Thunder Client hoặc trình duyệt:

```http
GET http://localhost:2005/employees
GET http://localhost:2005/departments
GET http://localhost:2005/positions
GET http://localhost:2005/employee-assignments
GET http://localhost:2005/employees/3/assignments
```

> API có `{id}` chỉ trả dữ liệu khi ID đó tồn tại. Database mới import schema nhưng chưa thêm nhân viên thì GET danh sách có thể trả `[]` — đó không phải lỗi.

**Tùy chọn chạy bằng nhấp đúp:** tạo `run.bat` tại thư mục có `pom.xml`:

```bat
@echo off
cd /d "%~dp0"
call mvnw.cmd spring-boot:run
pause
```

Chỉ chạy file `.bat` do nhóm tự kiểm tra và tin cậy.

## 8. Cách làm việc nhóm với Git

### Cách push code

Mỗi người push lên nhánh riêng đã tạo trên git, phân công công việc sau

> `main` là ví dụ; kiểm tra nhánh chính thực tế bằng `git branch -a`.

```powershell
git status
git add .
git commit -m "Add attendance module"
git push -u origin ###Ten nhanh###
```

Tạo Pull Request trên GitHub để nhóm review rồi merge vào `main`. Trước khi commit, **luôn chạy `git status` để kiểm tra không có file mật khẩu, database backup hoặc file build**.

### Khi database thay đổi

- **`git pull` không tự thay đổi SQL Server trên máy thành viên.**
- Người sửa schema phải tạo script cập nhật có thứ tự, ví dụ `database/migrations/001_add_table.sql`.
- Thông báo cho nhóm script nào cần chạy; không sửa trực tiếp DB rồi chỉ push Java.
- Trước khi chạy migration trên DB đã có dữ liệu, sao lưu và thử trên DB kiểm thử.

## 9. Kiến trúc xử lý request

```text
Frontend / Thunder Client
          ↓ HTTP + JSON
Controller (nhận request)
          ↓
Service (kiểm tra và xử lý nghiệp vụ)
          ↓
Repository (truy vấn/lưu Entity)
          ↓
SQL Server (hrm_db)
```

Luồng trả về: `SQL Server → Repository (Model/Entity) → Service (chuyển sang DTO) → Controller → JSON → Client`.

- **Model/Entity:** ánh xạ bảng database.
- **Request DTO:** dữ liệu đầu vào của POST/PUT.
- **Response DTO:** dữ liệu trả về cho client.
- **Service:** xử lý nghiệp vụ, chuyển đổi Entity ↔ DTO khi cần.

## 10. Lỗi thường gặp

| Hiện tượng | Cách kiểm tra |
|---|---|
| `java` không nhận diện | Kiểm tra JDK 21, `JAVA_HOME`, `PATH`; mở lại terminal |
| Maven Wrapper lỗi | Kiểm tra `mvnw.cmd`, `.mvn/wrapper/`, Internet lần đầu |
| `Login failed for user` | Kiểm tra SQL Authentication, username/password trong file local |
| `Connection refused` / timeout | Kiểm tra SQL Server service, TCP/IP, cổng 1433 hoặc cổng thực tế |
| `Invalid object name` | Kiểm tra database `hrm_db` và đã chạy schema đúng server chưa |
| `Port 2005 already in use` | Dừng ứng dụng cũ hoặc thay cổng cấu hình |
| API trả `404` | Kiểm tra URL, HTTP method, server đã restart sau sửa code chưa |
| API trả `409` | Kiểm tra khóa duy nhất, khóa ngoại và ràng buộc dữ liệu |
| GET trả `[]` | Database có thể chưa có dữ liệu mẫu |
| Pull code xong nhưng DB khác nhau | Chạy đúng migration SQL; Git không đồng bộ DB đang chạy |

## 11. Quy tắc chung của nhóm

1. Không commit mật khẩu, token, file cấu hình local hoặc dữ liệu cá nhân thật.
2. Mỗi người dùng cấu hình SQL Server riêng, không sửa file chung chỉ để đổi mật khẩu.
3. Không sửa/xóa bảng trên DB chung khi chưa thống nhất.
4. Commit code và script migration liên quan trong cùng Pull Request khi có thể.
5. Kiểm tra ứng dụng chạy được trước khi đề nghị merge.
6. Không dùng dữ liệu nhân viên thật trong database demo.
7. Commit theo đúng task khi làm để merge cho dễ

---

**Trạng thái dự án:** đang phát triển backend và chuẩn hóa môi trường nhóm. Nghiệp vụ **tính lương** là trọng tâm cần tiếp tục triển khai, không phải chức năng đã hoàn thiện.
