> Cập nhật sau khi được phép sửa: 5 lỗi đã được vá và kiểm lại. Xem [kết quả sau sửa](fixes/FIX_VERIFICATION.md). Nội dung bên dưới được giữ làm baseline trước sửa.

# Lỗi đã xác minh

Chỉ kiểm tra, không có bản vá. Môi trường chung: XuanAnh / fb9376567f1f9a6c1b61c18ed075961b893c2b2e với working tree chưa commit theo ENVIRONMENT.md; Windows, Java 23, SQL Server hrm_test; API localhost cổng QA 2009; web QA cổng 4203, trình duyệt tích hợp dựa Chromium (phiên bản binary chưa thu được). Các lỗi UI ghi rõ thao tác web; các lỗi API không được coi đã tái hiện trên UI.

## BUG-001 — S1 — HR ngoài phạm vi đọc được số dư phép của nhân viên phòng khác

- Module / Task: FE-006. Tầng: BE / phân quyền dữ liệu.
- Loại: LỖI. Mức độ: S1. Ưu tiên: P1.
- Môi trường: working tree nêu trên; vai trò ADMIN/HR như tiền điều kiện; mật khẩu và JWT không xuất. UI quan sát ở 1440 CSS px, trừ kiểm responsive ghi riêng.
- Tiền điều kiện: Nhân viên 2562748 thuộc phòng A; QA_20261101_205827_hrB chỉ có scope phòng B; quỹ QA mới được cấp 16 giờ.
- Các bước tái hiện: 1. Đăng nhập HR phòng B. 2. GET /employees/2562748/detail để xác nhận bị 403. 3. GET /employee-requests/balances/2562748. 4. Lặp lại request bước 3.
- Kết quả kỳ vọng và căn cứ: 403 hoặc không trả dữ liệu ngoài phạm vi — đề cương 1.3; ENT-03 / BEG-03 của prompt; scope HR được cấp theo phòng.
- Kết quả thực tế: GET /employee-requests/balances/2562748 trả 200 với entitlementId=542, entitledHours=16, usedHours=0, remainingHours=16. GET /leave-entitlements/542 bằng cùng HR bị 403. SELECT xác nhận HR-B chỉ có phòng B và quỹ thuộc nhân viên phòng A.
- Bằng chứng: evidence/http-results.json: ENT-OUTSCOPE-LEAK-0/1, ENT-OUTSCOPE-DIRECT-0/1; evidence/BUG-001-database.json
- Tần suất / F5: 2/2 request có quỹ thật; không phụ thuộc UI. F5/đăng nhập lại chưa lặp.
- Ảnh hưởng: Lộ quỹ phép ngoài phạm vi; kiểm tra quyền khác nhau giữa hai endpoint của cùng dữ liệu.
- Cách tạm né: Tránh sử dụng endpoint balances để tra cứu ngoài phạm vi; ẩn nút UI không khắc phục được API.
- Nghi ngờ nguyên nhân: Nghi ngờ balances chỉ kiểm ownerOrManager mà thiếu kiểm tra employee scope theo đường dẫn /balances/{employeeId}; chưa sửa.
- Liên quan: ENT-03, BEG-03; không xác nhận là lỗi trùng D01…D12.

## BUG-002 — S1 — Đăng xuất trả 500 và không thu hồi token đang dùng

- Module / Task: FE-001. Tầng: BE / phiên đăng nhập.
- Loại: LỖI. Mức độ: S1. Ưu tiên: P1.
- Môi trường: working tree nêu trên; vai trò ADMIN/HR như tiền điều kiện; mật khẩu và JWT không xuất. UI quan sát ở 1440 CSS px, trừ kiểm responsive ghi riêng.
- Tiền điều kiện: Tài khoản ADMIN QA mới QA_20261101_205827_qaAdmin đang hoạt động, có JWT thật; sử dụng hrm_test.
- Các bước tái hiện: 1. Đăng nhập tài khoản QA. 2. POST /api/auth/logout với JWT đó. 3. Dùng lại JWT gọi GET /api/auth/me. 4. Đăng nhập lại và lặp toàn bộ.
- Kết quả kỳ vọng và căn cứ: Logout hoàn thành 204; token cũ bị 401 theo SES-05. Không 500 trên chức năng đăng xuất.
- Kết quả thực tế: Hai lần POST logout đều 500. Hai lần dùng token cũ gọi /me đều 200. Log: IllegalStateException tại AuthService.logout: Không thể vô hiệu phiên đăng nhập. Sau đó khóa chính tài khoản QA qua API thì token bị chặn đúng.
- Bằng chứng: evidence/http-results.json: LOGOUT-0/1, LOGOUT-OLD-TOKEN-0/1, QA-LOCK, LOCKED-TOKEN-0/1; evidence/backend.log
- Tần suất / F5: 2/2 sau hai lượt đăng nhập API mới; chưa tái hiện thao tác nút logout trên UI.
- Ảnh hưởng: Không kết thúc phiên ở server; UI xóa token cục bộ không bảo đảm token đã sao chép bị thu hồi.
- Cách tạm né: Khóa tài khoản QA chặn được token; không xem đây là thay thế logout trong vận hành.
- Nghi ngờ nguyên nhân: AuthService kiểm giá trị trả về incrementTokenVersion phải bằng 1; nguyên nhân trả khác 1/transaction chưa được xác minh.
- Liên quan: SES-05; đề cương 1.1/1.3. Cơ chế thu hồi trong đề cương còn ghi cần xác nhận nhưng API logout hiện có không được phép trả 500.

## BUG-003 — S3 — Danh mục trình độ và loại nghỉ không có bộ lọc hoặc Xem tất cả

- Module / Task: FE-005 / FE-006. Tầng: FE.
- Loại: THIẾU. Mức độ: S3. Ưu tiên: P2.
- Môi trường: working tree nêu trên; vai trò ADMIN/HR như tiền điều kiện; mật khẩu và JWT không xuất. UI quan sát ở 1440 CSS px, trừ kiểm responsive ghi riêng.
- Tiền điều kiện: ADMIN đăng nhập web QA, dữ liệu danh mục có nhiều bản ghi.
- Các bước tái hiện: 1. Mở /qualifications. 2. Tìm ô tìm kiếm, bộ lọc và nút Xem tất cả. 3. F5 và kiểm lại. 4. Lặp trên /leave-types.
- Kết quả kỳ vọng và căn cứ: Có tìm/lọc và xóa lọc theo quy tắc danh sách thống nhất của chủ dự án; FEG-02. Không yêu cầu mọi danh mục có cùng tiêu chí nghiệp vụ.
- Kết quả thực tế: Cả hai route không có filter-actions, không có nút tìm kiếm hoặc Xem tất cả. Component Catalog chỉ hiển thị filter cho positions/departments.
- Bằng chứng: evidence/ui-observations.json: extraObservations.catalogFilters; source frontend/src/app/pages/catalog/catalog.html dòng điều kiện filter-bar
- Tần suất / F5: 2/2 mỗi route, gồm mở lại sau F5; source xác nhận điều kiện.
- Ảnh hưởng: Không thể tìm nhanh trên UI hoặc bỏ lọc theo cùng mẫu; vẫn phân trang được.
- Cách tạm né: Duyệt phân trang hoặc gọi API bằng công cụ kiểm thử.
- Nghi ngờ nguyên nhân: Điều kiện config.path loại trừ qualification-levels và leave-types.
- Liên quan: FEG-02; đây là thiếu UX theo yêu cầu đã thống nhất, không kết luận đề cương bắt mọi danh mục phải có tìm nâng cao.

## BUG-004 — S3 — Trang tài khoản giữ nhãn tiếng Việt khi chọn EN

- Module / Task: FE-001 / Tier 2 accounts. Tầng: FE / ngôn ngữ.
- Loại: LỖI. Mức độ: S3. Ưu tiên: P2.
- Môi trường: working tree nêu trên; vai trò ADMIN/HR như tiền điều kiện; mật khẩu và JWT không xuất. UI quan sát ở 1440 CSS px, trừ kiểm responsive ghi riêng.
- Tiền điều kiện: Chọn EN; html lang=en; trang bằng cấp đã hiển thị Degrees and certificates / Search / View all.
- Các bước tái hiện: 1. Chọn EN tại trang bằng cấp. 2. Mở /accounts. 3. Quan sát tiêu đề và nhãn tìm kiếm. 4. F5 và kiểm lại.
- Kết quả kỳ vọng và căn cứ: Các nhãn giao diện chuyển sang EN, dữ liệu tài khoản giữ nguyên; FEG-12.
- Kết quả thực tế: html lang=en nhưng h1 vẫn Tài khoản và phân quyền; các label vẫn Tìm kiếm, Trạng thái, Vai trò. Trong cùng phiên, trang bằng cấp dịch đúng.
- Bằng chứng: evidence/ui-observations.json: extraObservations.accountsEnglish; source frontend/src/app/pages/accounts/accounts.html
- Tần suất / F5: 2/2 gồm F5, lựa chọn EN được lưu.
- Ảnh hưởng: Giao diện lẫn ngôn ngữ giữa các module, không mất dữ liệu.
- Cách tạm né: Dùng VI; chưa khẳng định toàn bộ nhãn của mọi trang sai.
- Nghi ngờ nguyên nhân: Nhãn tĩnh ở template accounts chưa dùng dịch như component khác.
- Liên quan: FEG-12; Tier 2 chỉ smoke, không kết luận hoàn thành tất cả bản dịch.

## BUG-005 — S3 — Thiếu bộ lọc ngày tra cứu lịch trả 401 thay vì lỗi dữ liệu 400

- Module / Task: FE-009 / Tier 2 schedules. Tầng: BE / xử lý lỗi.
- Loại: LỖI. Mức độ: S3. Ưu tiên: P2.
- Môi trường: working tree nêu trên; vai trò ADMIN/HR như tiền điều kiện; mật khẩu và JWT không xuất. UI quan sát ở 1440 CSS px, trừ kiểm responsive ghi riêng.
- Tiền điều kiện: JWT ADMIN hợp lệ; /api/auth/me và API khác dùng được.
- Các bước tái hiện: 1. GET /api/management/schedules/search không có from/to bằng JWT ADMIN. 2. Gọi cùng endpoint có from/to ngày 2026-11-01. 3. Lặp lại cả hai request.
- Kết quả kỳ vọng và căn cứ: Thiếu tham số bắt buộc trả 400 và chỉ ra from/to; 401 dành cho phiên không hợp lệ (BEG-06 / mã lỗi §4).
- Kết quả thực tế: Không có ngày: 401, thông báo Phiên đăng nhập không hợp lệ hoặc tài khoản không còn được phép truy cập. Có from/to: 200 bằng JWT tương ứng.
- Bằng chứng: evidence/http-results.json: SCHEDULE-MISSING-DATES-0/1, SCHEDULE-VALID-DATES-0/1
- Tần suất / F5: 2/2 cho từng biến thể.
- Ảnh hưởng: Client có thể hiểu nhầm là hết phiên và buộc đăng nhập lại thay vì cho sửa bộ lọc.
- Cách tạm né: Luôn gửi đủ from/to.
- Nghi ngờ nguyên nhân: Chưa xác minh lớp nào chuyển lỗi binding thành 401; không coi JWT thực sự hết hạn.
- Liên quan: BEG-06, FEG-14; chưa kiểm việc UI có thực tế bị logout vì request này.
