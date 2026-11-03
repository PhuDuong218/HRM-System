# Ma trận theo đề cương

ĐẠT ở một tầng chỉ cho phần đã có bằng chứng, không phải nghiệm thu toàn chức năng. Phần chưa đủ dùng CHƯA KIỂM thay vì suy đoán.

| Mục | Chức năng | Giao diện | API | Ràng buộc DB | Phân quyền | Tích hợp |
|---|---|---|---|---|---|---|
| 1.1 | Đăng nhập bằng tài khoản, mật khẩu; hệ thống xác thực và cấp JWT cho tài khoản hợp lệ. | Đăng nhập ADMIN quan sát đạt | Đăng nhập thật nhiều vai trò đạt | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 1.2 | Quản trị viên quản lý tài khoản gắn với nhân viên, vai trò, quyền và trạng thái hoạt động. Tài khoản bị khóa không được truy cập. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 1.3 | Backend kiểm tra phiên, quyền và phạm vi dữ liệu ở mọi API; nhân viên chỉ truy cập dữ liệu của chính mình. Hỗ trợ đăng xuất; cơ chế gia hạn, thu hồi token và đặt lại mật khẩu cần xác nhận. | CHƯA KIỂM | CÓ LỖI BUG-002 | CHƯA KIỂM | CÓ LỖI BUG-001 | CÓ LỖI BUG-002 |
| 2.1 | Thêm, xem, cập nhật và tìm kiếm hồ sơ; hỗ trợ lọc và phân trang theo nhu cầu quản lý. | Mở / tìm bộ lọc đã quan sát | Tạo, tìm, validation nhiều nhánh đạt | SELECT nhân viên QA đạt | CHƯA KIỂM | CHƯA KIỂM |
| 2.2 | Theo dõi mã nhân viên duy nhất, thông tin cá nhân, liên hệ, ngày vào làm, phòng ban, chức vụ, trình độ và trạng thái. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 2.3 | Khi nhân viên nghỉ việc, cập nhật trạng thái và ngày nghỉ, giữ nguyên hồ sơ cùng hợp đồng, công, đơn và lương lịch sử. Nhân viên chỉ xem hồ sơ của mình. | CHƯA KIỂM | Thôi việc QA qua API đạt; tái tuyển tương lai bị chặn | Lịch sử QA còn; chưa đủ mọi module | CHƯA KIỂM | CHƯA KIỂM |
| 3.1 | Quản lý mã, tên, mô tả và trạng thái hoạt động của phòng ban. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 3.2 | Phân công nhân viên vào phòng ban và tra cứu nhân sự theo phòng ban. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 3.3 | Ngừng sử dụng phòng ban khi cần; không xóa danh mục đang được dữ liệu nghiệp vụ tham chiếu. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 4.1 | Quản lý mã, tên, mô tả và trạng thái của từng chức vụ. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 4.2 | Gán chức vụ cho nhân viên và tra cứu chức vụ hiện tại; chính sách phụ cấp liên quan chỉ áp dụng khi được phê duyệt. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 4.3 | Không xóa chức vụ đang được dữ liệu nghiệp vụ tham chiếu. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 5.1 | Quản lý danh mục trình độ và thông tin trình độ của nhân viên. | CÓ LỖI BUG-003 | Tạo/switch primary/xóa mềm QA đã kiểm | Một primary QA; dữ liệu đã SELECT | CHƯA KIỂM | CHƯA KIỂM |
| 5.2 | Thông tin dự kiến gồm tên trình độ, chuyên ngành, cơ sở đào tạo và năm hoàn thành khi cần. | Danh sách/chi tiết/tìm đã quan sát | Tạo/switch primary/xóa mềm QA đã kiểm | Một primary QA; dữ liệu đã SELECT | CHƯA KIỂM | CHƯA KIỂM |
| 5.3 | Nhân viên xem thông tin của mình; cách lưu một trình độ chính hay nhiều bằng cấp, chứng chỉ cần xác nhận. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 6.1 | Tạo, xem và quản lý hợp đồng theo từng nhân viên, gồm số, loại, ngày ký, hiệu lực, ngày kết thúc, điều kiện lương và trạng thái. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 6.2 | Lưu nhiều hợp đồng theo thời gian; khi gia hạn hoặc ký mới phải giữ hợp đồng cũ để tra cứu lịch sử. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 6.3 | Kiểm tra thứ tự ngày và ghi dấu vết thay đổi điều kiện hợp đồng; quy tắc hợp đồng đồng thời, hết hạn và phụ lục cần xác nhận. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 7.1 | Tạo ca và phân công lịch cho nhân viên theo ngày; ghi thời gian bắt đầu, kết thúc dự kiến và ghi chú. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 7.2 | Phát hiện lịch trùng hoặc chồng lấn của cùng nhân viên; nhân viên xem lịch được phân công cho mình. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 7.3 | Lịch là kế hoạch làm việc, không tự tạo công thực tế hoặc tiền công. Quy tắc ca qua đêm cần xác nhận. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 8.1 | Ghi nhận và tra cứu thời gian làm việc thực tế theo nhân viên, ngày hoặc ca; đối chiếu lịch để xác định đi làm, vắng, đi muộn và về sớm. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 8.2 | Người có quyền kiểm tra, xác nhận và sửa công; mỗi lần sửa lưu lý do, người thực hiện và thời điểm. Công đã xác nhận là đầu vào tính lương. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 8.3 | Nhân viên xem lịch sử công của mình. Nếu triển khai check-in/check-out trên Mobile, dữ liệu gửi lên cần được backend kiểm tra và xác nhận theo chính sách; nguồn chấm công và quy tắc làm tròn cần xác nhận. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 9.2 | Người có quyền duyệt hoặc từ chối; lưu người xử lý, thời điểm và lý do từ chối. Trạng thái gồm CHO_DUYET, DA_DUYET, TU_CHOI. | CHƯA KIỂM | Nghỉ việc: submit/reject/resubmit/approve/double approve đã kiểm | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 9.3 | Đơn nghỉ phép đã duyệt được đối chiếu với lịch và công, không mặc định mọi ngày nghỉ đều hưởng lương. Đơn nghỉ việc đã duyệt chỉ cập nhật trạng thái nhân viên từ ngày có hiệu lực; hồ sơ lịch sử được giữ lại. | CHƯA KIỂM | Duyệt tương lai chưa thôi việc ngay: đã kiểm | CHƯA KIỂM | CHƯA KIỂM | Job tới hạn NOT-RUN |
| 10.1 | Nhân viên đăng ký tăng ca theo ngày, khoảng thời gian và lý do; người có quyền duyệt hoặc từ chối. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 10.2 | Kiểm tra thời gian hợp lệ, tránh đăng ký trùng; dùng các trạng thái CHO_DUYET, DA_DUYET, TU_CHOI. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 10.3 | Chỉ tăng ca đã duyệt và có giờ thực tế được xác nhận mới đủ điều kiện tính lương, tránh cộng trùng giờ làm thông thường. Hệ số và giới hạn tăng ca cần xác nhận. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 11.1 | Quản lý loại phụ cấp và khoản phụ cấp áp dụng cho từng nhân viên. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 11.2 | Ghi số tiền hoặc cách tính, điều kiện áp dụng và thời gian hiệu lực; khoản hợp lệ trong kỳ là đầu vào tính lương. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 11.3 | Tránh áp dụng trùng cùng một khoản; nhân viên chỉ xem phụ cấp của mình. Danh mục và cách tính cụ thể cần xác nhận. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 12.1 | Ghi quyết định theo nhân viên, gồm loại, nội dung, ngày, người ban hành và số tiền nếu có. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 12.2 | Phân biệt quyết định chỉ lưu hồ sơ với quyết định được phép ảnh hưởng đến lương; giữ lịch sử và dấu vết điều chỉnh. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 12.3 | Nhân viên chỉ xem quyết định liên quan đến mình. Quy tắc hạch toán thưởng, khấu trừ và quyền phê duyệt cần xác nhận. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 13.1 | Tạo kỳ lương, tổng hợp điều kiện lương có hiệu lực, công xác nhận, nghỉ và tăng ca đã duyệt, phụ cấp, thưởng và khấu trừ được phép áp dụng. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 13.2 | Tính, kiểm tra và chốt kết quả theo từng nhân viên, kỳ lương; ngăn bản ghi trùng. Công thức chính thức, công chuẩn, thuế, bảo hiểm và làm tròn cần xác nhận. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 13.3 | Bảng lương đã chốt không được sửa, xóa hay tính lại trực tiếp. Nếu điều chỉnh, tạo phiên bản mới theo quyền, có lý do và lịch sử; lưu căn cứ tính tại thời điểm chốt. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 14.1 | Tạo phiếu lương từ kết quả bảng lương đã chốt, thể hiện kỳ, các khoản thu nhập, khấu trừ và thực nhận. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 14.2 | Công bố phiếu theo quyền; nhân viên chỉ xem phiếu của mình đã công bố, kể cả khi truy cập trực tiếp API hoặc tải tệp. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 14.3 | Phiếu phải khớp kết quả đã chốt và phiên bản điều chỉnh được chấp thuận. Việc tải PDF, in phiếu và thời điểm công bố cần xác nhận. | NOT-IMPLEMENTED (UI) | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 15.1 | Thống kê nhân sự theo trạng thái, phòng ban, chức vụ và trình độ; theo dõi hợp đồng còn hiệu lực hoặc sắp hết hạn. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 15.2 | Tổng hợp lịch, công, nghỉ phép, tăng ca, quỹ lương, phụ cấp, thưởng và khấu trừ theo kỳ hoặc phạm vi được chọn. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |
| 15.3 | Bộ lọc và dữ liệu xuất phải tuân thủ quyền truy cập; nhân viên không xem báo cáo quản trị hay lương người khác. Định dạng xuất và biểu đồ cần xác nhận. | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM | CHƯA KIỂM |

9.1 thiếu nội dung trong DOCX, không tự bổ sung làm căn cứ PASS/FAIL.

## Checklist HRM thiết yếu §8

| # | Nhóm | Phạm vi | Trạng thái |
|---|---|---|---|
| 1 | Đăng nhập/đăng xuất | Có trong đề cương | CÓ LỖI BUG-002 |
| 2 | Đổi/reset mật khẩu | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 3 | Quên mật khẩu tự phục vụ | Chuẩn HRM / không bắt buộc đề cương | CHƯA KIỂM ĐỦ |
| 4 | Vai trò và phạm vi | Có trong đề cương | CÓ LỖI BUG-001 |
| 5 | Hồ sơ nhân viên | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 6 | Hồ sơ mở rộng | Chuẩn HRM / không bắt buộc đề cương | CHƯA KIỂM ĐỦ |
| 7 | Tìm/lọc/phân trang/sắp xếp | Có trong đề cương | CÓ LỖI BUG-003; một số tìm kiếm đã kiểm |
| 8 | Phòng ban/chức vụ/trưởng phòng | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 9 | Phân công/điều chuyển | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 10 | Trình độ/bằng cấp | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 11 | Thôi việc/tái tuyển | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 12 | Nghỉ phép/quỹ phép | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 13 | Thông báo nghiệp vụ | Chuẩn HRM / không bắt buộc đề cương | CHƯA KIỂM ĐỦ |
| 14 | Audit | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 15 | Dữ liệu riêng tư | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 16 | Hợp đồng | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 17 | Lịch/công/OT/lương liên module | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 18 | Báo cáo/xuất file | Có trong đề cương | CHƯA KIỂM ĐỦ |
| 19 | Nhập hàng loạt/in | Chuẩn HRM / không bắt buộc đề cương | CHƯA KIỂM ĐỦ |
| 20 | VI/EN/responsive/bàn phím | Chuẩn HRM / không bắt buộc đề cương | CÓ LỖI BUG-004; responsive route smoke đạt |
| 21 | Backup/khôi phục/tài liệu | Chuẩn HRM / không bắt buộc đề cương | Backup hrm_test + VERIFYONLY đạt; chưa RESTORE thật/máy sạch |
