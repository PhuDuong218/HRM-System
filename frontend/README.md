# HRM Center — Bộ khung Angular để học và phát triển

## 1. Bản này có gì?

Dựa trên PROJECT_SPEC(2).md được cung cấp: Web Angular + TypeScript + Bootstrap. Đây là khung giao diện để học và phát triển. Có layout quản lý, menu 15 module, trang tổng quan, bảng nhân viên rỗng, trang kiểm tra API, các trang chờ phát triển và trang 404.

Đăng nhập đã kết nối với API của backend. Trang quản lý yêu cầu phiên đăng nhập và token được gửi đến các API qua interceptor. ADMIN, HR và EMPLOYEE dùng cùng một menu; quyền đối với dữ liệu và chức năng vẫn do backend kiểm soát.

## 2. Chạy trên Windows

Cài Node.js phù hợp Angular 20.3: Node 20.19+, 22.12+ hoặc 24.x. Gợi ý Node 24 LTS. Kiểm tra `node -v` và `npm -v` trong CMD.

Giải nén để có thư mục frontend cạnh backend của bạn (kiểm tra thư mục cũ trước khi sao chép). Ví dụ:

```
HRM-System/
  backend/
  frontend/
    package.json
    angular.json
    src/
```

Mở CMD:

```bat
cd /d D:\Duong\CongNghePhanMem\HRM-System\frontend
npm ci
npm start
```

Mở http://localhost:4200. Giữ CMD đang chạy; Ctrl+C để dừng. `npm ci` tải thư viện đúng package-lock.json nên cần Internet. Không cần cài Angular CLI toàn cục. Lệnh `npm run build` tạo bản build trong dist/hrm-frontend/browser.

## 3. Cây thư mục để đọc

```
frontend/
  src/
    main.ts                  # Bắt đầu khởi động Angular
    index.html               # Trang chứa <app-root>
    styles.css               # CSS chung toàn ứng dụng
    app/
      app.ts                 # Component gốc
      app.html               # Chứa router-outlet
      app.config.ts          # Cấp Router, HttpClient và cơ chế cập nhật giao diện
      app.routes.ts          # Đường dẫn → component
      navigation.ts          # Tên và đường dẫn các mục menu
      layout/
        admin-layout.ts      # Dữ liệu menu, đóng/mở menu trên điện thoại
        admin-layout.html    # Sidebar + header + vùng trang con
        admin-layout.css     # Định dạng bố cục
      pages/
        overview/            # Trang tổng quan
        employees/           # Khung bảng nhân viên
        connection/          # Trang gọi API health
        placeholder/         # Trang dùng chung cho phần chưa triển khai
      services/
        api.config.ts        # Tiền tố API: /api
        health.service.ts    # Gọi GET /api/health
  proxy.conf.json            # Chuyển tiếp /api tới Spring Boot khi chạy local
  package.json               # Thư viện và lệnh chạy
  package-lock.json          # Phiên bản thư viện đã khóa
  angular.json               # Cấu hình chạy và build, nạp Bootstrap CSS
  tsconfig*.json             # Cấu hình TypeScript
```

Không tạo core/shared/features vì bản đầu chưa cần nhiều tầng thư mục. Bootstrap được cài bằng npm và nạp CSS ở angular.json; menu di động dùng Angular, không cần Bootstrap JavaScript.

## 4. Đọc code theo thứ tự nào?

1. **pages/overview/overview.html**: thử sửa dòng tiêu đề rồi lưu. Trình duyệt cập nhật.
2. **styles.css**: đổi màu hoặc khoảng cách. Đây là CSS chung; layout/admin-layout.css chỉ áp dụng bố cục đó.
3. **pages/connection/connection.ts**: xem biến loading, response, error; hàm checkConnection xử lý nút bấm.
4. **pages/connection/connection.html**: tìm `(click)` để thấy sự kiện gọi hàm và `{{ ... }}` để thấy hiển thị dữ liệu.
5. **app.routes.ts**: URL /employees trỏ tới EmployeesPage.
6. **health.service.ts**: xem component gọi API bằng cách nào.

Các khái niệm:
- Component: một phần giao diện gồm class TypeScript và template HTML, có thể kèm CSS riêng.
- `@Component`: khai báo template, CSS và các thành phần Angular mà component sử dụng.
- `standalone: true`: component tự khai báo các thành phần phụ thuộc trong imports.
- `{{ response }}`: đưa giá trị biến TypeScript ra HTML.
- `(click)="checkConnection()"`: bấm nút thì chạy hàm trong class.
- `[disabled]="loading"`: khóa nút khi biến loading là true.
- `@if`: hiển thị có điều kiện; `@for`: lặp menu.
- `routerLink`: chuyển trang bằng Angular; `router-outlet`: vị trí Angular đặt trang được chọn.
- Service: nơi chứa lời gọi API dùng lại được; `inject` lấy đối tượng do Angular cung cấp.
- `subscribe`: nhận kết quả bất đồng bộ của HTTP; next xử lý thành công, error xử lý thất bại.

## 5. Luồng chạy của ứng dụng

Trình duyệt mở index.html → main.ts gọi bootstrapApplication → App được tạo theo app.config.ts → router đọc app.routes.ts → AdminLayout hiển thị → trang overview được đặt trong router-outlet bên trong layout.

Có hai router-outlet: một ở App đặt layout, một ở layout đặt trang con. Nhờ vậy chuyển trang không phải viết lại sidebar và header.

Trang placeholder dùng ActivatedRoute.data và AsyncPipe để hiển thị tiêu đề theo route. Khi chuyển giữa hai route cùng dùng PlaceholderPage, tiêu đề vẫn cập nhật. Các placeholder không phải nghiệp vụ đã hoàn thành.

## 6. Thử kết nối backend của bạn

1. Chạy Spring Boot của bạn.
2. Mở proxy.conf.json, đổi các target từ http://localhost:2005 sang địa chỉ backend thực tế nếu khác.
3. Dừng và chạy lại npm start sau khi đổi proxy.
4. Mở trang Kiểm tra kết nối rồi bấm nút.

Luồng: nút trong connection.html → checkConnection() trong connection.ts → HealthService.check() → HttpClient GET /api/health → Angular dev proxy → Spring Boot → kết quả trả về và hiển thị.

Endpoint health là endpoint được tài liệu xác nhận. Service nhận text nên hiển thị được chuỗi hoặc JSON nguyên văn mà không giả định DTO. Có thời gian chờ tối đa 10 giây và thông báo lỗi; không tự gửi yêu cầu khi chỉ mở trang. HTTP thành công không chứng minh SQL Server còn kết nối.

Lỗi 401/403 phải đối chiếu Security thật. Lỗi kết nối: kiểm tra backend/cổng/proxy và Network của trình duyệt (F12). Không mở toàn bộ Security để chữa lỗi frontend. Không đưa tài khoản SQL Server hay khóa JWT vào frontend.

Proxy chỉ phục vụ phát triển local; bản production cần reverse proxy /api hoặc cấu hình URL API và CORS phù hợp. Chưa có hướng dẫn deploy production trong bản này.

## 7. Phát triển nhân viên từ đâu?

Bước tiếp theo sau khi hiểu giao diện là lấy Controller và DTO nhân viên từ backend để đối chiếu hợp đồng API. Cần xác minh path, quyền, cấu trúc phân trang, tên trường, định dạng ngày và lỗi.

Sau đó:
1. Tạo services/employee.service.ts để gọi endpoint thật.
2. Tạo model theo DTO thật, có thể đặt cạnh service hoặc trong thư mục employees.
3. Trong employees.ts, gọi service và lưu danh sách, loading và lỗi.
4. Trong employees.html, dùng @for để hiển thị từng dòng; thêm trạng thái rỗng/lỗi theo kết quả API.
5. Khi danh sách chạy được mới thêm tìm kiếm, phân trang, form và validation.

Không tự dùng tên trường hoặc đường dẫn mẫu trong tài liệu như hợp đồng API đã triển khai. Nhân viên nghỉ việc được giữ hồ sơ, không thêm nút xóa cứng trái đặc tả.

## 8. Muốn thêm một trang thì làm gì?

Ví dụ phòng ban: tạo pages/departments/departments.ts và departments.html, khai báo component tương tự EmployeesPage. Import component mới trong app.routes.ts và thay route departments đang dùng PlaceholderPage thành component mới. Menu đã có nên không cần thêm lại.

Muốn đổi tên menu: navigation.ts. Muốn đổi sidebar: layout/admin-layout.html. Muốn đổi trang mặc định: redirectTo trong app.routes.ts.

Trang `/login` gửi tên đăng nhập và mật khẩu tới `POST /api/auth/login`. Token được lưu trong `sessionStorage` của tab, đính kèm vào các yêu cầu `/api/` và bị xóa khi hết hạn, bị backend từ chối hoặc đăng xuất thành công. Nút đăng xuất gọi `POST /api/auth/logout`, làm backend vô hiệu các token hiện tại của tài khoản. Cần dùng tài khoản ứng dụng HRM, không phải tài khoản SQL Server. Guard frontend không thay thế phân quyền backend; menu chưa được lọc theo vai trò.

## 9. Giới hạn bộ khung

Không có dữ liệu nhân sự giả; các chức năng CRUD, tính lương, chấm công, duyệt đơn và quản lý tài khoản chưa hoàn chỉnh trên frontend. Bảng nhân viên rỗng là khung giao diện, không phải kết quả truy vấn trả về rỗng. Trang tổng quan không có số liệu thống kê giả. Các mục menu thể hiện phạm vi module và chưa phản ánh quyền của người dùng.

Tài liệu tham khảo chính thức: https://angular.dev/reference/versions và https://angular.dev/cli/build.
