# Giao diện thống nhất cho nhiệm vụ Xuân Anh

## Phạm vi trên menu

Có **10 mục nghiệp vụ chính**, tương ứng **11 đường dẫn** vì Phòng ban có trang danh mục phụ.

| Nhóm menu | Mục | Route | Task |
|---|---|---|---|
| Quản lý Nhân sự | Nhân viên | /employees | FE-002 |
| Quản lý Nhân sự | Phân công / Điều chuyển | /assignments | FE-004 |
| Quản lý Nhân sự | Bằng cấp / Chứng chỉ | /certificates | FE-005 |
| Thời gian & Chấm công | Quản lý đơn từ — nghỉ phép/nghỉ việc | /requests | FE-006, FE-007 |
| Thời gian & Chấm công | Loại nghỉ phép | /leave-types | FE-006 |
| Thời gian & Chấm công | Quỹ phép | /leave-entitlements | FE-006 |
| Cơ cấu tổ chức | Phòng ban và trưởng phòng | /departments | FE-003 |
| Cơ cấu tổ chức | Thêm / sửa phòng ban (trang phụ) | /department-catalog | FE-003 |
| Cơ cấu tổ chức | Chức vụ | /positions | FE-003 |
| Cơ cấu tổ chức | Trình độ | /qualifications | FE-005 |
| Hệ thống | Lịch sử thay đổi | /audit | FE-008 |

Tổng quan và Kiểm tra kết nối thuộc nền tảng dùng chung FE-001. FE-009 là kiểm thử/tích hợp, không phải một mục nghiệp vụ riêng. Hợp đồng trong ảnh được dùng làm mẫu bố cục; các module của thành viên khác vẫn được giữ trong menu và source.

## Khuôn mẫu chung

- Tiêu đề và thao tác chính ở đầu trang.
- Cột trái chứa bộ lọc, nút tìm/xóa lọc, thanh Danh sách/Tải lại, bản ghi và phân trang.
- Cột phải chứa chi tiết hoặc form thêm/sửa; chỉ có một nút đóng trong thanh đầu panel.
- Khi cột danh sách còn tối đa 740 px, bảng thành thẻ có nhãn từng trường như mẫu Hợp đồng. Ngưỡng dựa vào chiều rộng cột, không chỉ chiều rộng thiết bị.
- Desktop đủ rộng dùng hai cột; màn hình tối đa 1250 px dùng drawer bên phải. Drawer tự cuộn; trang phía sau không cuộn. Đóng drawer khôi phục cuộn trang.
- Nút lọc căn phải; nút Tải lại ở thanh danh sách. Form kết thúc bằng Hủy rồi Lưu thay đổi; nút lưu khóa khi form không hợp lệ hoặc đang lưu. Tác vụ nghiệp vụ có tên riêng như Duyệt, Từ chối, Lưu bổ nhiệm vẫn giữ tên và điều kiện riêng.
- Có trạng thái chọn bản ghi, loading, lỗi, danh sách rỗng và phân trang dùng chung. Màu xanh/trắng/xám và dark mode theo theme hiện có.
- Mở panel giữ vị trí trang, không tự cuộn tiêu đề xuống dưới topbar.

ManagementPageBase tiếp tục quản lý trạng thái/lifecycle; RecordWorkspace quản lý list/panel/drawer; Pagination dùng chung. management-page.css chứa quy tắc trình bày riêng cho các trang nhân sự, không áp kiểu thẻ lên module khác.

## Khác biệt đã xử lý

| Trước | Sau |
|---|---|
| Bộ lọc phân công/bằng cấp/quỹ phép/đơn/audit nằm ngoài workspace | Nằm trong cùng khung danh sách bên trái |
| Một số trang cuộn bảng ngang khi mở chi tiết, mẫu Hợp đồng dùng thẻ | Các trang nhiệm vụ dùng cùng ngưỡng chuyển thẻ và nhãn trường |
| Bản ghi được chọn chỉ được đánh dấu ở một số trang | Danh mục và phòng ban cũng có trạng thái chọn |
| Nhãn Lưu và Lưu thay đổi khác nhau giữa các form chung | Thống nhất Hủy / Lưu thay đổi |
| Nút tải lại lẫn trong bộ lọc | Nằm ở thanh danh sách |
| Panel có khung surface lồng khung, heading/label/khoảng cách khác nhau | Dùng quy tắc chung cho panel, form, nút và thông tin chi tiết |
| Focus panel có thể kéo trang và làm khuất tiêu đề | Focus với preventScroll |
| Mobile cuộn được trang sau drawer | Chỉ drawer cuộn khi mở |

## Kiểm tra thực tế

- Build Angular production thành công; log: qa/evidence/personnel-ui-build.log.
- 11 màn hình danh sách desktop + 11 màn hình chi tiết ở mỗi độ rộng CSS 1440, 390, 320: **44 quan sát**, không ghi nhận tràn ngang toàn trang, ô dữ liệu thiếu nhãn hoặc alert lỗi tải trong các ca này.
- **16 ca form**: mở/Hủy tám form tại 1440 và 320 px; nút lưu khóa khi form trống, panel sticky ở desktop và fixed ở mobile.
- Kiểm tra thêm tìm kiếm không có kết quả, Xóa lọc, đóng drawer khôi phục cuộn, mở form không đổi scrollY.
- Dữ liệu từ backend QA thật trỏ hrm_test; không dùng mock dữ liệu giao diện, không ghi hrm_db. Chỉ mở/đóng/đọc form; **không coi đây là kiểm thử lưu CRUD hoặc nghiệm thu toàn bộ nghiệp vụ**.
- Bằng chứng: personnel-unified-layout-results.json, personnel-unified-form-results.json, personnel-unified-empty-results.json trong qa/evidence/.

Trong lượt chỉnh giao diện này không đổi DTO, endpoint, quyền hoặc logic xử lý nghiệp vụ. Không commit/push.
