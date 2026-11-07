# Đối chiếu 145 mục kiểm thử

Ngày 06/11/2026. PARTIAL = mới kiểm tra một phần các tình huống trong mục; NOT_RUN = chưa có bằng chứng thực thi, không tính PASS. Không đồng nhất số mục này với 219 kiểm tra API.

| Mã | Tình huống gốc | Trạng thái / bằng chứng |
|---|---|---|
| A1 | Hai tài khoản trong cùng trình duyệt: tài khoản sau ghi đè phiên tài khoản trước, thao tác nhầm quyền. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| A2 | Đăng xuất một tài khoản làm các tab khác của tài khoản đó bị văng, mất bản nháp. | **PARTIAL** — Logout 204; JWT cũ 401; chưa kiểm tra mất bản nháp ở tab khác. |
| A3 | Token hết hạn khi đang mở form dài: mất dữ liệu không báo, hoặc treo. | **PARTIAL** — Có regression tự động session-expiry; chưa diễn tập hết hạn trên form thật. |
| A4 | Chạy demo lần hai: mã DEMO-FLOW-01, email, SĐT, CCCD, số hợp đồng đã tồn tại nên báo trùng. Bản ghi cũ không xóa được nên cần đổi hậu tố. | **PARTIAL** — Trùng mã nhân viên/chức vụ/kỳ đã thử; chưa lặp toàn bộ demo. |
| A5 | Số bắt đầu bằng 0 (SĐT 0901234588, CCCD 0999…) bị mất số 0 khi lưu, hiển thị hoặc xuất Excel. | **PARTIAL** — CCCD QA giữ số 0 đầu; chưa kiểm tra số 0 trong Excel. |
| A6 | Job nền chạy giữa buổi demo (thực thi nghỉ việc, hết hạn hợp đồng, quét thiếu check-out, hết hạn đơn đổi lịch) làm dữ liệu đổi ngoài ý muốn. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| A7 | Chốt và công bố lương trên DB dùng chung không hoàn tác được; dữ liệu demo lẫn với dữ liệu nhóm. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| A8 | Giờ máy chủ khác giờ máy khách khiến “hôm nay” và “tương lai” lệch 1 ngày, nhất là sau 17:00 UTC. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| A9 | Tài khoản demo có mật khẩu dễ đoán còn tồn tại sau buổi demo. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| A10 | Backend hoặc DB chậm, tắt giữa chừng: giao diện hiện số cũ hoặc số 0 thay vì báo lỗi. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| B1 | Số trên Tổng quan khác SELECT thật: đếm cả nhân viên đã nghỉ, hợp đồng hết hạn, danh mục ngừng. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| B2 | Chia cho 0, phần trăm NaN, biểu đồ rỗng khi chưa có dữ liệu. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| B3 | Tổng quan của HR đếm cả nhân viên ngoài phạm vi. | **PARTIAL** — HR dùng overview riêng; kiểm tra API, chưa đối soát toàn bộ chỉ số với SQL. |
| B4 | Trang Kiểm tra kết nối lộ URL DB, tên đăng nhập DB hoặc mật khẩu; HR hay EMPLOYEE vào được. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| B5 | Lỗi backend nhưng vẫn hiển thị số liệu mẫu. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| C1 | Mã trùng khác hoa thường (demo-troly), có khoảng trắng đầu hoặc cuối, ký tự vô hình, dấu tiếng Việt tổ hợp và dựng sẵn. | **PARTIAL** — Mã chức vụ trùng khác hoa thường 409; chưa phủ ký tự vô hình. |
| C2 | Hai người thêm cùng mã đồng thời: phải 409, không được 500 hay hai bản ghi. | **PARTIAL** — Hai POST chức vụ đồng thời: một 201, một 409. |
| C3 | Ngừng danh mục đang được dùng: phòng còn người, chức vụ còn người giữ, trình độ còn bằng, loại nghỉ còn đơn, loại hợp đồng còn hợp đồng, ca còn lịch tương lai. | **PARTIAL** — Ngừng chức vụ đang có nhân viên:409; chưa phủ mọi danh mục. |
| C4 | Mở form chọn chức vụ ở tab này, ngừng chức vụ ở tab kia, rồi bấm Lưu: backend phải kiểm tra lại. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| C5 | Đổi mã hoặc đổi tên danh mục sau khi đã dùng: lịch sử đổi theo hay mất. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| C6 | Loại nghỉ: đổi “có lương” hoặc “trừ quỹ” sau khi đã có đơn; tổ hợp có lương nhưng không trừ quỹ; chưa cấu hình chính sách mà vẫn nộp được đơn. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| C7 | Loại hợp đồng: thời hạn 0, âm, rất lớn, vô thời hạn. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| C8 | Ca: giờ bắt đầu bằng giờ kết thúc; ca qua đêm; giờ nghỉ nằm ngoài ca, dài hơn ca, hai khoảng nghỉ chồng nhau; thực làm trên 12 giờ. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| C9 | Tìm kiếm có %, _, ', ": kết quả hoặc số đếm sai. | **PARTIAL** — 6 truy vấn đặc biệt/Unicode trả200; tìm Hân/han/dấu tổ hợp có cùng tập ID. |
| C10 | HR sửa danh mục khi chỉ ADMIN được phép; EMPLOYEE gọi API ghi danh mục. | **PARTIAL** — EMPLOYEE ghi chức vụ403; chưa phủ toàn bộ API danh mục. |
| D1 | Mã nhân viên trống, trùng, quá dài, ký tự lạ; đổi mã sau khi tạo. | **PARTIAL** — Trùng mã409; họ tên trống400; quá dài400. |
| D2 | Họ tên 1 ký tự, 300 ký tự, chỉ số, emoji, dấu nháy; tìm không dấu có khớp khi tên là dấu tổ hợp. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| D3 | Ngày sinh ở tương lai, năm 1900, dưới tuổi lao động, 29/02 năm không nhuận, sau ngày vào làm. | **PARTIAL** — Ngày sinh tương lai400; chưa phủ tuổi tối thiểu và mọi biên lịch. |
| D4 | Ngày vào làm ở tương lai, trước ngày sinh, trước ngày thành lập phòng. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| D5 | Email hoa thường khác nhau nhưng trùng, có khoảng trắng, hai dấu @; SĐT dạng +84, thiếu hoặc thừa số, có chữ; CCCD 9 hoặc 12 số, có chữ, trùng. | **FAIL_OR_PARTIAL** — FAIL: API chấp nhận thiếu email/CCCD/địa chỉ và CCCD chứa chữ; email/điện thoại sai bị400. |
| D6 | Phòng hoặc chức vụ đã ngừng, không tồn tại (gọi thẳng API); gán thẳng chức vụ trưởng phòng. | **PARTIAL** — Phòng không tồn tại404; chưa phủ mọi trạng thái ngừng/chức vụ quản lý. |
| D7 | Hồ sơ tạo xong nhưng phân công đầu tiên lỗi: hồ sơ mồ côi, không rollback. | **PARTIAL** — Tạo đúng một phân công; cuối phiên không có nhân viên mồ côi. |
| D8 | Bấm Lưu liên tục, F5 sau khi lưu, Back sau khi lưu: không được tạo hai hồ sơ. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| D9 | Mở chi tiết nhiều dòng nhanh liên tục: dữ liệu dòng trước đè lên dòng sau. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| D10 | Bấm nút trong dòng nhưng lại mở chi tiết (bấm nhầm vùng). | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| D11 | Sửa hồ sơ: hai người sửa cùng lúc, bản sau ghi đè mù; lý do trống; sửa liên hệ của chính mình. | **FAIL_OR_PARTIAL** — FAIL: không gửi lý do vẫn200; phiên cũ ghi đè thay đổi mới; giữ contact sau PUT hợp lệ PASS. |
| D12 | Sửa hồ sơ bằng tài khoản không thấy CCCD hoặc địa chỉ riêng tư: giá trị ẩn có bị xóa hoặc ghi đè không. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| D13 | Bản nháp: sửa dở rồi bấm sang danh mục khác, bấm Back trình duyệt, đóng tab, F5: có hỏi giữ hoặc bỏ không; “Tiếp tục nhập” có còn dữ liệu. | **PARTIAL** — Web thật: rời menu bị hỏi; Tiếp tục nhập giữ bản nháp; chưa kiểm tra đóng tab/F5 lúc HTTP đang gửi. |
| D14 | CCCD và địa chỉ hiển thị cho ADMIN hoặc HR (chính sách chỉ chủ hồ sơ); lộ qua audit, xuất file, ô tìm kiếm. | **PARTIAL** — Dùng chính sách mới: ADMIN, HR đúng scope, trưởng phòng đúng phòng xem CCCD/địa chỉ hồ sơ và audit:PASS; người ngoài scope403. |
| D15 | Bằng cấp: năm tốt nghiệp ở tương lai, trước năm sinh, 0; chuyên ngành trống; bằng trùng hoàn toàn; trình độ đã ngừng. | **PARTIAL** — Năm tốt nghiệp tương lai400; hết hạn trước ngày cấp400; FAIL:bằng trùng201. |
| D16 | Hai bằng đều là “chính” khi đặt đồng thời; gỡ bằng chính xong nhân viên không còn bằng chính; bằng đã gỡ vẫn hiện ở chi tiết hoặc vẫn được đếm. | **PARTIAL** — Hai cập nhật bằng chính đồng thời: chỉ một chính; gỡ bằng chính còn bằng khác409. |
| D17 | Trình độ ở hồ sơ nhân viên lệch với bằng chính ở mục Bằng cấp. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| E1 | Ngày hiệu lực trùng ngày bắt đầu phân công hiện tại (bản ghi dài 0 ngày), trước ngày vào làm, trước phân công hiện tại, ở tương lai. | **PARTIAL** — Lập điều chuyển tương lai thành công; chưa phủ mọi biên hiệu lực. |
| E2 | Chọn đúng chức vụ hoặc phòng hiện tại (không đổi gì) vẫn tạo bản ghi. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| E3 | Đích là phòng hoặc chức vụ đã ngừng, chức vụ trưởng phòng, phòng ngoài phạm vi HR. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| E4 | Hai điều chuyển đồng thời: còn hai phân công mở, hoặc không còn phân công mở nào. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| E5 | Lịch sử bị ghi đè; mốc kết thúc bao gồm hoặc loại trừ làm hở hoặc chồng 1 ngày; lịch sử hiển thị sai thứ tự. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| E6 | Điều chuyển nhân viên đang là trưởng phòng; đang có đơn chờ cấp 1 (người duyệt cấp 1 đổi, đơn ai xử lý); còn lịch hoặc hợp đồng ở phòng cũ. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| E7 | Sau điều chuyển: HR phòng cũ vẫn thấy nhân viên, HR phòng mới thấy ngay chưa. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| E8 | Điều chuyển nhân viên đã nghỉ. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| F1 | Bổ nhiệm với ngày hiệu lực trước ngày bắt đầu phân công, ở tương lai; ứng viên thuộc phòng khác, đã nghỉ, đang là trưởng phòng nơi khác; lý do trống. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| F2 | Hai bổ nhiệm chồng nhau; bổ nhiệm khi phòng đã có trưởng phòng. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| F3 | Thay trưởng phòng: người cũ không có chức vụ tiếp theo, hoặc vẫn giữ chức vụ “Trưởng phòng”, hoặc nhiệm kỳ cũ không đóng. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| F4 | Kết thúc nhiệm kỳ: ngày trước ngày bắt đầu; sau đó phòng khuyết, đơn mới nộp chờ cấp 1 của ai. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| F5 | Trưởng phòng nghỉ phép, nghỉ việc, chuyển phòng hoặc tự nộp đơn: đơn cấp 1 kẹt hoặc tự duyệt. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| F6 | Phòng ngừng sử dụng nhưng nhiệm kỳ trưởng phòng vẫn mở; phòng chỉ có một người (chính là trưởng phòng). | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| F7 | demo.head.dt duyệt đơn nhân viên phòng khác; vào được API quản trị nhân viên, lương, quỹ ngoài mục Duyệt cấp 1. | **PARTIAL** — Trưởng phòng khác phòng đọc hồ sơ/duyệt đơn403; chưa phủ mọi API quản trị. |
| F8 | Đổi trưởng phòng khi có đơn chờ cấp 1: đơn chuyển sang người mới hay người cũ vẫn duyệt được. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| F9 | Bổ nhiệm trưởng phòng có làm thay đổi vai trò tài khoản (không được thành ADMIN hay HR). | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| G1 | Số hợp đồng trùng khác hoa thường hoặc có khoảng trắng; ngày kết thúc trước ngày bắt đầu; ngày ký ở tương lai; hợp đồng bắt đầu trước ngày vào làm. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| G2 | Hai hợp đồng chồng thời hạn; thử việc nối chính thức bị hở hoặc chồng 1 ngày; nhân viên đã nghỉ; loại hợp đồng đã ngừng; thời hạn vượt tối đa của loại. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| G3 | Gia hạn: bản mới bắt đầu không sau ngày cuối bản cũ; gia hạn hợp đồng vô thời hạn, hợp đồng đã chấm dứt, đã gia hạn. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| G4 | Chấm dứt hồi tố, chấm dứt sau ngày cuối; hợp đồng còn “đang hiệu lực” sau khi nhân viên thôi việc. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| G5 | Job hết hạn lúc nửa đêm: ngày cuối có tính còn hiệu lực; chạy lặp; cảnh báo “sắp hết hạn” tính sai ngày (UTC hay UTC+7). | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| G6 | Mức lương: nhiều mức chồng hoặc hở trong tháng; mức bằng 0, âm, rất lớn; đổi lương hồi tố làm lệch kỳ đã chốt. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| G7 | Tài khoản: tạo cho nhân viên đã nghỉ hoặc đã có tài khoản; username trùng hoa thường; mật khẩu yếu; mật khẩu tạm lộ trong response hoặc log. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| G8 | Chọn ADMIN khi tạo tài khoản (UI chặn nhưng gọi thẳng API thì sao); gửi kèm role, isActive để leo quyền. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| G9 | Phạm vi HR: phạm vi rỗng, chồng, hết hạn; thu hồi giữa phiên (quyền mất ngay hay phải chờ hết token); HR tự cấp phạm vi cho mình. | **PARTIAL** — HR tự cấp scope403; chưa thu hồi scope thật giữa form. |
| G10 | Reset mật khẩu mà phiên cũ vẫn dùng được; khóa tài khoản đang đăng nhập; ADMIN tự khóa hoặc tự hạ quyền mình (hệ thống không còn ADMIN). | **PARTIAL** — Logout204 và JWT cũ401; chưa reset mật khẩu/tự khóa ADMIN. |
| G11 | demo.hr tìm DEMO008 (HCNS): hồ sơ, quỹ phép, đơn, bằng cấp, hợp đồng, công, phiếu lương, báo cáo, audit đều phải bị chặn. | **PARTIAL** — Hồ sơ/quỹ/đơn/bằng/hợp đồng/audit403; lịch/công trả200 rỗng; tài chính403. |
| H1 | Gán lịch vào ngày quá khứ, hôm nay, ngày lễ; trùng ca; chồng một phần (08–12 và 11–15); ca qua đêm đè sang ngày có lịch khác; tổng trên 12 giờ/ngày. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H2 | Gán lịch cho nhân viên đã nghỉ, chưa có hợp đồng, hoặc bằng ca đã ngừng. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H3 | Sửa hoặc xóa lịch đã có công, đã có phép được duyệt, hoặc thuộc kỳ lương đã chốt. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H4 | Đơn đổi lịch hết hạn tự động đúng lúc đang duyệt; hai đơn đổi cùng một lịch. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H5 | Biên check-in: sớm 29, 30, 31 phút; muộn 14, 15, 16 phút; muộn nhiều giờ vẫn ghi nhận; số phút đi muộn làm tròn. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H6 | Check-in hai lần; check-out trước check-in; không check-out (job quét có tạo trùng); check-out qua nửa đêm. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H7 | Chấm công cho lịch của người khác, lịch đã hủy, lịch tương lai; server dùng giờ máy khách hay giờ máy chủ. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H8 | Ca có nghỉ giữa ca: giờ thực làm có trừ nghỉ; làm tròn phút tích lũy; thực làm vượt 12 giờ bị cắt hay lưu. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H9 | Điều chỉnh hoặc giải trình công: quá hạn nộp; tự duyệt; duyệt hai lần; sửa công đã xác nhận hoặc đã vào kỳ chốt; bỏ xác nhận lặp. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H10 | Nhập công thủ công cho ngày tương lai, giờ ra trước giờ vào. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H11 | Tổng hợp công: công chưa xác nhận lọt vào; ca qua đêm tính vào hai kỳ hoặc không kỳ nào; ranh giới đầu hoặc cuối tháng; tổng ≠ tổng chi tiết; nhân viên nghỉ giữa kỳ; kỳ trống. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| H12 | Lọc theo phòng ban làm lộ nhân viên ngoài phạm vi HR. | **PARTIAL** — API lịch/công nhân viên ngoài scope trả rỗng, không có DEMO008. |
| I1 | Khoảng ngày không có lịch, hoặc chỉ một phần có lịch; ngày bắt đầu sau ngày kết thúc; ngày quá khứ hoặc đang diễn ra. | **PARTIAL** — Ngày đảo400; không có lịch409; nộp bù quá7ngày400. |
| I2 | Đơn vắt qua hai kỳ quỹ (30/12 → 02/01), hoặc qua kỳ quỹ bị hở; kỳ quỹ đã hết hạn. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| I3 | Nghỉ nửa ngày với ca có giờ nghỉ giữa ca chưa xác định vị trí; nghỉ ca qua đêm; số giờ lẻ phút. | **PARTIAL** — Nửa ca08–12 trừ4h; chưa ca qua đêm/lẻ phút. |
| I4 | Đơn chồng khoảng với đơn đang chờ, đã duyệt; nộp lại đúng khoảng đã bị từ chối hoặc đã rút. | **PARTIAL** — Đơn chờ chồng409; nộp lại sau hủy201. |
| I5 | Loại nghỉ chưa cấu hình hoặc đã ngừng; vượt quỹ đúng 1 phút, đúng bằng quỹ. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| I6 | Nhân viên đã nghỉ, hoặc có đơn nghỉ việc đã duyệt với ngày làm cuối trước khoảng nghỉ. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| I7 | Nộp đơn thay người khác (API); nhân viên không có tài khoản. | **PARTIAL** — Gửi employeeId=8/status/approvedBy giả trong body không đổi chủ đơn, vẫn nhân viênQA. |
| I8 | Trưởng phòng phòng khác duyệt; trưởng phòng duyệt đơn của chính mình; trưởng phòng đã hết nhiệm kỳ nhưng token còn. | **PARTIAL** — Trưởng phòng khác phòng403; chưa thử tự duyệt và hết nhiệm kỳ trên DB. |
| I9 | Phòng không có trưởng phòng: đơn chờ vô hạn; nhân viên chuyển phòng giữa chừng. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| I10 | Cấp 1 duyệt xong thì nhân viên rút đơn; cấp 1 duyệt hai lần (hai tab); cấp 1 từ chối không lý do. | **PARTIAL** — Từ chối cấp1 trắng400; duyệt lặp409; rút sau cấp1 được; duyệt sau rút409. |
| I11 | HR hay ADMIN duyệt thẳng bỏ qua cấp 1: được không, có chủ ý không. | **PARTIAL** — Duyệt cuối trước cấp1 bị409 trong phòng có trưởng phòng. |
| I12 | Duyệt trước cấp 1; HR ngoài phạm vi duyệt; HR duyệt đơn của chính mình. | **PARTIAL** — HR ngoài scope403; trước cấp1 409; chưa thử HR tự duyệt. |
| I13 | Quỹ bị điều chỉnh giảm, hết hạn, hoặc bị đơn khác trừ giữa lúc chờ cấp 2; lịch bị đổi hoặc xóa giữa hai cấp. | **PARTIAL** — Hai đơn tranh quỹ8h: một200/một409; chưa hết hạn quỹ/đổi lịch giữa hai cấp. |
| I14 | Hai HR duyệt cùng lúc; duyệt đúng lúc người nộp rút; duyệt xong nhưng không ghi phân bổ giờ (trạng thái lệch). | **PARTIAL** — Hai duyệt cuối đồng thời không âm quỹ; chưa race duyệt với rút. |
| I15 | Nghỉ cả ca 08:00–17:00 có nghỉ trưa 1 giờ phải trừ đúng 8 giờ; ca nhiều đoạn; đơn nhiều ngày có ngày không có lịch. | **PARTIAL** — Ca08–17 nghỉ1h đã kiểm tra ở full-leave-results.json trước lượt này; lượt hiện tại nửa ca4h. |
| I16 | Hiển thị “chưa trừ” ở cấp 1 nhưng thực tế có giữ chỗ hay không. | **PARTIAL** — Luồng prior leave đã xác nhận cấp1 chưa trừ; không coi là giữ chỗ. |
| I17 | Hủy đơn đã duyệt khi đã bắt đầu hoặc đã qua; hủy hai lần hoàn hai lần; người không có quyền hủy; hủy sau khi quỹ bị điều chỉnh hoặc đã hết hạn kỳ. | **PARTIAL** — Hai lần hủy đồng thời:200/409 và chỉ hoàn1lần; EMPLOYEE hủy đã duyệt403; chưa biên sau giờ nghỉ. |
| I18 | Hủy mà lịch, công hoặc kỳ lương chứa ngày đó đã chốt. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| I19 | Từ chối lý do trống hoặc chỉ dấu cách; từ chối ở cấp 2 sau cấp 1: quỹ giữ chỗ có được nhả không. | **PARTIAL** — Từ chối trắng cấp1/cấp2 bị400; chưa phủ hoàn quỹ sau từ chối mọi tình huống. |
| I20 | Nhân viên có biết đơn bị từ chối hoặc hủy; hai người cùng xử lý một đơn: người sau nhận thông báo rõ chưa. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| J1 | Tăng ca: đăng ký đúng 30 và 31 ngày trước; ngày quá khứ; trùng OT khác; chồng ca chính; tổng trên 12 giờ/ngày; qua nửa đêm. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| J2 | OT thực tế ít hoặc nhiều hơn đăng ký; đã duyệt nhưng không đi làm; tự duyệt; xác nhận hai lần; mở lại xác nhận sau khi kỳ lương đã chốt. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| J3 | OT ngày nghỉ hoặc lễ (hệ số chưa đặc tả); OT của nhân viên đã nghỉ; giờ lẻ phút làm tròn. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| J4 | Phụ cấp: hai khoản trùng thời gian; hiệu lực một phần kỳ; hiệu lực trước ngày vào làm hoặc sau ngày nghỉ; sửa khoản đã có hiệu lực; số tiền 0, âm, rất lớn; loại đã ngừng. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| J5 | Phụ cấp ăn trưa vượt trần miễn thuế 730.000; phụ cấp theo ngày công hoặc giờ khi công chưa xác nhận. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| J6 | Quyết định: số quyết định trùng; ngày ban hành ở tương lai; ngày áp dụng trước ngày ban hành. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| J7 | Khen thưởng hoặc kỷ luật: loại cộng hoặc trừ thiếu số tiền; loại không ảnh hưởng lương nhưng có số tiền; tự duyệt quyết định về mình; duyệt lặp; sửa hoặc xóa sau duyệt. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| J8 | Quyết định áp dụng sau khi kỳ đã chốt rơi vào kỳ nào; quyết định cho người đã nghỉ; khấu trừ làm thực nhận âm; thiếu thành phần lương cộng hoặc trừ trong danh mục. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| K1 | Kỳ: tạo trùng hoặc chồng; tháng 28, 29, 30, 31 ngày; kỳ không có dữ liệu; nhân viên vào hoặc nghỉ giữa kỳ (DEMO-FLOW-01 vào tháng 11 xuất hiện trong lương tháng 10). | **PARTIAL** — Kỳ tháng2/2040 hợp lệ; FAIL:kỳ trùng/ngày đảo500; chưa chồng kỳ/vào nghỉ giữa kỳ. |
| K2 | Tính: thiếu hợp đồng, mức lương hoặc công chưa xác nhận; một nhân viên lỗi làm hỏng cả đợt hoặc đợt dở dang. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| K3 | Nhiều mức lương trong tháng mà căn cứ bảo hiểm chưa xác nhận; chia 22 không chẵn (lệch 1 đồng); OT thập phân. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| K4 | Thu nhập tính thuế sát biên bậc (5, 10, 18, 32 triệu ±1 đồng); thuế từ bậc 2 trở lên (chưa chốt đặc tả); không áp dụng người phụ thuộc. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| K5 | Phép có lương và không lương; quyết định khấu trừ; thực nhận âm; tính lại sau khi sửa công; hai người bấm Tính cùng lúc; tính trong lúc người khác đang sửa công. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| K6 | Chốt: nguồn đổi sau khi tính; chốt hai lần; chốt khi còn công, OT chưa xác nhận hoặc đơn phép chờ duyệt. | **FAIL_OR_PARTIAL** — FAIL:chốt trước tính500; không chốt kỳ dùng chung. |
| K7 | Sau chốt vẫn sửa được công, OT, phụ cấp, lương bằng module hoặc API khác. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| K8 | Công bố hai lần (phiếu trùng); công bố khi chưa chốt; số phiếu ≠ số nhân viên; phiếu hiện cho sai người. | **FAIL_OR_PARTIAL** — FAIL:công bố chưa chốt500; chưa tạo phiếu công bố/race công bố. |
| K9 | Điều chỉnh sau công bố: bản cũ bị mất hay còn; mã phiếu V1, V2. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| K10 | Phiếu: nhân viên xem phiếu chưa công bố; đổi ID xem phiếu người khác; trang phiếu của ADMIN có hiện phiếu của mọi người không; số lệch bảng lương 1 đồng. | **PARTIAL** — EMPLOYEE giả employeeId người khác403; DB chưa có payslip để thử ID phiếu có thật. |
| K11 | In hoặc lưu PDF: mất dấu tiếng Việt, cắt trang giữa dòng; tên chứa <script> hoặc &. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| K12 | Báo cáo: số nhân viên, hợp đồng, công, lương khác danh mục nguồn; lọc kỳ sai biên; lấy bản chưa chốt hoặc bản lương cũ. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| K13 | Xuất Excel: mất số 0 đầu, ô bắt đầu bằng =, +, -, @ thành công thức, lỗi font tiếng Việt, ngày sai định dạng, quá nhiều dòng. | **PARTIAL** — 4 export ADMIN có MIME XLSX và magic ZIP; HR/EMPLOYEE403; chưa phân tích công thức/font/số0 trong workbook. |
| K14 | Báo cáo gồm nhân viên ngoài phạm vi HR; EMPLOYEE hoặc HR không có quyền truy cập báo cáo. | **PARTIAL** — HR/EMPLOYEE báo cáo/export403 theo chính sách route hiện tại. |
| L1 | Thôi việc có ngày làm cuối hôm nay, quá khứ, tương lai: trước ngày làm cuối nhân viên vẫn đăng nhập, vẫn chấm công, vẫn nộp đơn (đúng thiết kế); sau ngày đó bị khóa đúng lúc. | **PARTIAL** — Quyết định tương lai lập204, nhân viên vẫn DANG_LAM; chưa chờ job ngày đến hạn. |
| L2 | Tác vụ thực thi đến hạn: chạy lặp thực thi hai lần, thu hồi phiên hai lần; chạy sai ngày do múi giờ. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| L3 | Nhân viên là trưởng phòng; còn đơn chờ cấp 1 hoặc cấp 2; còn phép đã duyệt hoặc lịch sau ngày làm cuối; còn hợp đồng hiệu lực; còn OT, phụ cấp, quyết định đang chờ. | **PARTIAL** — Thôi việc trước phân công tương lai409; chưa toàn bộ đơn/OT/phụ cấp/nhiệm kỳ. |
| L4 | Kỳ lương chưa chốt chứa tháng nghỉ (lương tháng cuối, phép tồn); token cũ của người đã nghỉ còn dùng được. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| L5 | Số quyết định trùng; lý do trống; HR ngoài phạm vi thôi việc; HR tự thôi việc chính mình; nhân viên đang đăng nhập đúng lúc bị khóa. | **PARTIAL** — Quyết định lặp409; chưa mọi ràng buộc số quyết định/tự thôi việc. |
| L6 | Tái tuyển: ngày trước ngày nghỉ; còn phân công mở; còn đơn nghỉ việc chưa xong; HR gọi (chỉ ADMIN được phép). | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| L7 | Tái tuyển tự mở lại tài khoản, hợp đồng hoặc quỹ phép (không được); ngày vào làm đầu tiên bị đổi; tái tuyển rồi thôi việc lần hai; dữ liệu lương cũ còn nguyên. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| L8 | Audit thiếu dòng cho: thôi việc, hủy đơn, duyệt cấp 1, duyệt cấp 2, bổ nhiệm, đổi vai trò, reset mật khẩu, công bố lương. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| L9 | Audit ghi hai lần; người thực hiện sai tài khoản; lý do trống; thiếu trường trước hoặc sau; lộ CCCD, mật khẩu, token. | **FAIL_OR_PARTIAL** — FAIL:sửa hồ sơ bỏ reason vẫn200; audit privacy đọc đúng scope. |
| L10 | HR thấy audit ngoài phạm vi; lọc theo ngày lệch múi giờ hoặc sai mốc kết thúc; có đường sửa hay xóa audit; giao dịch lỗi vẫn để lại dòng audit; hiển thị JSON thô. | **PARTIAL** — Audit ngoài scope403; JSON trình bày UI chưa đánh giá đầy đủ lượt này. |
| M1 | Phân trang page=-1, size=0, size=100000; tham số sắp xếp lạ; tổng số bản ghi làm lộ dữ liệu ngoài phạm vi. | **PARTIAL** — page âm/size0/size100000 bị400; chưa mọi endpoint. |
| M2 | XSS trong tên, ghi chú, lý do khi hiển thị ở danh sách, chi tiết, audit, phiếu lương, báo cáo, tooltip. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| M3 | Chuỗi 10.000 ký tự, emoji, '; DROP, chữ tiếng Việt Telex và Unicode tổ hợp ở mọi ô nhập. | **PARTIAL** — Tên10.000ký tự400; ký tự tìm kiếm không gây500. |
| M4 | Mất mạng hoặc tắt backend đúng lúc bấm Lưu; hai tab cùng một form; F5 giữa chừng. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| M5 | Gửi thêm role, isActive, status, approvedBy, performedBy vào body (leo quyền); đổi ID trong URL (đọc chéo). | **PARTIAL** — Đọc chéo403 và giả employeeId/status khi nộp đơn không leo quyền; chưa mọi DTO. |
| M6 | 401, 403, 404 không nhất quán; lỗi 500 hiển thị nguyên văn, lộ stack trace, tên bảng hoặc câu SQL. | **FAIL_OR_PARTIAL** — FAIL:4 đường lương trả500 cho lỗi nghiệp vụ. |
| M7 | Màn hình 1280×800 và 1366×768, zoom 125% và 200%: tràn ngang, nút chồng chữ, ngăn kéo không cuộn. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| M8 | Chọn EN còn sót chữ Việt (Lương, Phiếu lương, Báo cáo, Tổng hợp công); nhãn EN dài làm vỡ nút; chế độ tối mất tương phản. | **FAIL_OR_PARTIAL** — FAIL:web đang EN nhưng trang Payroll vẫn tiếng Việt; chưa đo toàn bộ EN/dark. |
| M9 | Dữ liệu lớn (100–500 nhân viên): danh sách, tìm kiếm, tính lương, xuất báo cáo bị chậm. | **NOT_RUN** — Chưa có bằng chứng thực thi trong lượt này; không tính PASS. |
| M10 | Bản ghi mồ côi: phân công không có nhân viên, đơn không có người nộp, quỹ không có loại; hai bảng không khớp nhau (danh sách ≠ chi tiết ≠ audit). | **PARTIAL** — Cuối phiên SQL:không nhân viên thiếu phân công; không hai bằng chính; chưa toàn bộFK. |
| M11 | Quyền chéo: EMPLOYEE gọi API quản trị; HR gọi API chỉ dành cho ADMIN (tái tuyển, thực thi nghỉ việc, cấu hình chính sách, phạm vi HR, tài khoản). | **PARTIAL** — EMPLOYEE quản trị tài khoản/lương403, HR scope403; chưa gọi job dùng chung vì có thể tác động dữ liệu ngoài QA. |
