# Kiểm tra chức năng nghỉ việc nhân viên

## Thay đổi trong lượt triển khai này

- Thêm `EmployeeTerminationController`: `POST /employees/{id}/terminate`.
- API mới kiểm tra tài khoản ADMIN/HR có quyền EMPLOYEE_MANAGE qua Principal của server.
- Giữ `DELETE /employees/{id}` cũ, bổ sung cùng kiểm tra quyền và sửa thông báo thành xử lý nghỉ việc.
- Hai API dùng lại `EmployeeService.deleteEmployee()`, không xóa bản ghi nhân viên.
- Thêm test service, HTTP và một test database trong `EmployeeAssignmentValidityIntegrationTest`.
- Không sửa DTO, migration, frontend hoặc các chức năng khác trong lượt này.

## Quy tắc hiện có được giữ nguyên

- Ngày nghỉ là ngày nghiệp vụ hiện tại theo giờ Việt Nam; chưa nhận ngày do client chọn.
- Đây là thao tác trực tiếp của người quản lý, chưa phải quy trình duyệt đơn nghỉ việc.
- Lưu trạng thái DA_NGHI và termination_date, giữ hồ sơ/lịch sử.
- Phân công kết thúc tại ngày nghỉ cộng một ngày theo khoảng [effective_from, effective_to).
- Phân công còn hiệu lực về ngày trong ngày nghỉ cuối cùng; trạng thái nhân viên chuyển DA_NGHI ngay khi xử lý.
- Chặn nghỉ việc lặp lại, thiếu phân công hiện tại và trưởng phòng chưa kết thúc/chuyển giao nhiệm vụ.

## Gọi API mới

1. Khởi động backend ở thư mục HRM_System: `.\mvnw.cmd spring-boot:run`.
2. Dùng database thử nghiệm và ID nhân viên thử nghiệm đang làm việc, có phân công thường.
3. Gửi `POST http://localhost:2005/employees/{id}/terminate`, không cần body.
4. Khi phần JWT của nhóm đã tích hợp, gửi token thật của tài khoản đủ quyền qua Authorization Bearer.
5. Kết quả thành công là HTTP 204, không có body.

Không có Principal: 401. Không đủ quyền: 403. Không tồn tại: 404.
Đã nghỉ việc hoặc đang làm trưởng phòng: 409.
Nếu JWT chưa tích hợp, không thể thử thành công qua Postman chỉ bằng cách tự thêm header role/account.

DELETE cũ vẫn tồn tại nhưng cũng cần quyền. Thành công trả HTTP 200 với thông báo nghỉ việc.
Giao diện cũ gọi DELETE mà chưa có đăng nhập sẽ nhận 401; cần nối với JWT thật khi tích hợp.

## Chạy test

Trong thư mục HRM_System, với SQL Server và hrm_test sẵn sàng:

```powershell
.\mvnw.cmd "-Dtest=EmployeeDetailServiceTest,EmployeeAssignmentValidityIntegrationTest,EmployeeTerminationServiceTest,EmployeeTerminationControllerTest,DepartmentHeadIntegrationTest,AssignmentLockConcurrencyTest" test
```

Các test controller dùng Principal giả lập và mock kiểm tra quyền, không kiểm tra phát hành/xác minh JWT.
Các test database dùng giao dịch rollback. Xem kết quả từng lớp trong target/surefire-reports.

Kết quả xác minh ngày 23/10/2026: tổng 41 test trong sáu lớp đạt qua lượt hồi quy
và lượt chạy lại controller sau khi sửa UTF-8 (7 hồ sơ, 5 hiệu lực/database,
5 service nghỉ việc, 5 controller nghỉ việc, 18 trưởng phòng, 1 khóa đồng thời).

## Các việc còn lại của phần nhân viên

- Phân quyền đầy đủ các API danh sách, tìm kiếm, tạo và sửa cũ với phần JWT của nhóm.
- Nhật ký thao tác tạo/sửa/nghỉ việc và người thực hiện.
- Nối quy trình đơn nghỉ việc được duyệt với nghiệp vụ nghỉ việc, chốt quy tắc ngày hiệu lực.
- Rà soát validation, thời điểm tạo/sửa và cập nhật đồng thời.
- Nối giao diện với PATCH và POST terminate; kiểm thử HTTP đầy đủ sau tích hợp JWT.

Các việc trên chưa được đánh dấu hoàn thành chỉ vì bộ test nghỉ việc đạt.
