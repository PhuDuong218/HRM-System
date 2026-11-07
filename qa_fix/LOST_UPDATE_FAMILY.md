# Rà soát cùng họ: bản nháp cũ và khóa lạc quan

Chỉ hồ sơ nhân viên được sửa. Các dòng khác là **nguy cơ từ source**, chưa tái hiện HTTP trong lượt này, không coi là bug đã xác minh mới.

| Đường ghi / file service | Hiện trạng | Mức độ / đề xuất |
|---|---|---|
| PUT employees/{id} — EmployeeCreationAuditService, EmployeeService | Đã sửa: version16hex bắt buộc; so sánh sau PESSIMISTIC_WRITE; Hibernate BaseEntity @Version còn bảo vệ UPDATE WHERE row_version; flush trước trả version | P1 đã sửa source; race HTTP CHƯA VERIFY |
| PATCH employees/{id}/contact — EmployeeContactService | Đã áp cùng hợp đồng version/lý do; kiểm trước no-op và trước thay đổi; trả version mới từ detail | Cùng gốc với QA-01/04 |
| EmployeeService.updateEmployee(id, EmployeeRequestDTO) | Overload internal cũ không được HTTP mapping trực tiếp gọi. Controller legacy không mapped chuyển sang creationAudit nhưng chưa truyền version/reason từ DTO cũ | Client/programmatic cũ cần cập nhật; không thêm đường bỏ qua kiểm tra |
| Phòng ban — DepartmentService/CatalogAuditService | BaseEntity có @Version; chưa có phiên bản bản nháp từ client | P2: hai form cùng đọc, A đổi tên, B đổi mô tả gửi lại tên cũ. Đề xuất expectedVersion +409. CHƯA ÁP |
| Chức vụ — PositionService/CatalogAuditService | Tương tự; khóa ghi danh mục không tự phát hiện bản nháp cũ | P2; thử cùng kịch bản tên/mô tả; CHƯA ÁP |
| Bằng cấp — EmployeeQualificationService/PersonnelCatalogService | Đã thêm dedup theo tổ hợp, có khóa employee. Chưa thêm client version cho sửa nội dung | P2; A đổi nơi cấp, B sửa năm trên form cũ có thể ghi lại nơi cấp cũ; CHƯA VERIFY/CHƯA ÁP version |
| Quỹ phép — LeaveEntitlementService/PersonnelCatalogService | Có kiểm hạn mức đã dùng, giao kỳ; chưa nhận expectedVersion của bản nháp | P2; hai phiên thay tổng quỹ với giá trị cũ. CHƯA ÁP |
| Trình độ/loại phép — QualificationLevelService/LeaveTypeService | Có audit và trạng thái; DTO chưa chứa client version | P2; đề xuất snapshot version cho cập nhật, không sửa lượt này |
| Hợp đồng — ContractService/ContractLifecycleService | Các command gia hạn/chấm dứt có guard trạng thái/ngày; chưa có đồng nhất version client | Phải phân biệt state-machine conflict với lost-update. Không áp khóa lạc quan đại trà |
| Phân công/bổ nhiệm — EmployeeTransferAuditService, luồng trưởng phòng | Có timeline/employee lock; request command chứa ngày/lý do, không phải form replace đơn giản | Kiểm expected current assignment/appointment riêng nếu cần. Không đổi luồng trong lượt này |
| Lịch/công/OT | Một số command đã có expectedVersion, còn các API cũ | Không đánh đồng mọi command với profile PUT; cần mapping từng route. Ngoài phạm vi |

## Vì sao cách sửa hồ sơ có tính nguyên tử

1. Controller được phân quyền như cũ; RequestBodyAdvice giữ quy tắc dữ liệu riêng tư.
2. Transaction wrapper lấy employee qua `EmployeeRepository.findByIdForUpdate` (PESSIMISTIC_WRITE).
3. Kiểm version bản đọc của client so với employee vừa khóa, trước mọi mutate/audit.
4. ORM vẫn dùng mapping `BaseEntity.@Version` sẵn có cho row_version sinh bởi SQL Server; không đổi BaseEntity hoặc schema.
5. save/flush; supplemental gender được ghi trước snapshot cuối, refresh entity; trả version cuối.
6. Bản nháp lỗi thời409 không mutate và không gọi audit. Unit test xác nhận đường này; cần race HTTP thật sau backup/restart để nghiệm thu DB.
