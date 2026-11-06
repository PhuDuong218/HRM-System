# Báo cáo kiểm thử HRM — 06/11/2026

## Kết luận

Chưa thể nghiệm thu toàn bộ hệ thống. Lượt này xác minh **9 ca API FAIL và 2 lỗi giao diện**, tổng cộng **11 điểm cần xử lý**. Không sửa source sản phẩm trong lượt QA này; chỉ tạo công cụ kiểm thử, bằng chứng và dữ liệu QA.

| Phần kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| API thật, Backend cổng 2005, SQL Server `hrm_center_full` | **219 kiểm tra: 210 PASS, 9 FAIL** | Bao gồm đăng nhập, chuẩn bị và thu hồi dữ liệu QA; không phải 219 nghiệp vụ độc lập |
| Regression Frontend | **49/49 PASS** | Harness tự động; không thay thế đo responsive trên trình duyệt |
| Regression Backend | **50/50 PASS, 9 suite** | Unit/mocked authorization; không phải 50 integration test trên full DB |
| Angular production build | **PASS** | Initial bundle 825,95 kB, vượt ngưỡng cảnh báo 750 kB khoảng 75,95 kB |
| Web trực tiếp | Đăng nhập ADMIN; form nhân viên; cảnh báo bản nháp; validation; tìm không dấu; kiểm tra EN ở loại phép và lương | Chưa kiểm tra đầy đủ CRUD trên từng trang/thiết bị/vai trò |

Danh sách gốc có **145 mục A1–M11**, nhiều mục chứa nhiều tình huống. Phụ lục phân biệt **PARTIAL / FAIL_OR_PARTIAL / NOT_RUN**; tuyệt đối không quy đổi API smoke thành PASS toàn bộ một mục. **87 mục chưa có bằng chứng thực thi trực tiếp trong lượt này.**

## 11 lỗi đã xác minh

### QA-01 — P1: Phiên cũ ghi đè mất thay đổi hồ sơ mới

- Đối tượng QA: nhân viên 36 (`QA6-mw4lfj2n`), phòng DT.
- ADMIN và HR cùng đọc hồ sơ ban đầu. ADMIN đổi tên thành `QA tên mới ở phiên A` và lưu. HR gửi lại form cũ, chỉ chủ ý đổi điện thoại.
- `PUT /employees/36` của HR trả **200**; tên vừa lưu ở phiên A bị trả về tên cũ.
- Mong đợi: từ chối bản chỉnh sửa đã lỗi thời với 409/412, hoặc cơ chế cập nhật chỉ các trường thay đổi và phát hiện xung đột.
- Khuyến nghị: version/ETag xuyên suốt DTO, form, service và UPDATE; khóa giao dịch đơn thuần không phát hiện bản nháp cũ.
- Bằng chứng: `stale-profile-results.json` → `D11-stale-profile-must-not-silently-overwrite`. Hồ sơ QA đã được khôi phục.

### QA-02 — P2: API tạo nhân viên bỏ qua trường bắt buộc mà UI đang yêu cầu

- `POST /employees` có mã, họ tên, ngày vào làm, phòng, chức vụ nhưng bỏ `email`, `identityNumber`, `address` vẫn trả **201**; tạo nhân viên QA 37.
- UI thật có kiểm tra ba trường này; gọi API trực tiếp vẫn vượt qua được.
- Mong đợi theo yêu cầu đã chốt của người dùng: **400** với lỗi từng trường.
- Nguyên nhân đối chiếu: `EmployeeRequestDTO` chỉ có Email/Size, không có NotBlank cho ba trường; cần kiểm tra tương ứng ở cập nhật.
- Bằng chứng: `write-results.json` → `D5-required-private-contact-fields`.

### QA-03 — P2: API chấp nhận CCCD chứa chữ

- `PUT /employees/36` với `identityNumber: "CCCDabc"` và lý do hợp lệ trả **200**.
- Mong đợi: **400**, không lưu giá trị này. Cần chốt rõ định dạng CMND/CCCD mà đồ án hỗ trợ; tối thiểu không chấp nhận chữ tùy ý.
- Khuyến nghị: chuẩn hóa validation Backend/Frontend cùng quy tắc; không dùng kiểu số làm mất số 0 đầu.
- Bằng chứng: `stale-profile-results.json` → `D5-invalid-ID-letters`. Đã khôi phục CCCD QA.

### QA-04 — P2: Bỏ lý do sửa hồ sơ vẫn lưu thành công

- `PUT /employees/36` với tên mới, không gửi `reason`, trả **200**.
- Mong đợi theo nghiệp vụ đã chốt: **400**, giao dịch không cập nhật và không tạo audit thiếu lý do.
- Đối chiếu source: `EmployeeCreationAuditService.update()` đi vào overload audit cũ nếu reason null; overload này cho phép ghi reason null.
- Khuyến nghị: một đường ghi hồ sơ bắt buộc reason, tránh giữ overload legacy thành đường vượt validation.
- Bằng chứng: `write-results.json` → `D11-update-requires-reason`.

### QA-05 — P2: Thêm bằng cấp giống hệt tạo bản ghi trùng

- Gửi lại cùng payload của bằng đầu: trình độ, chuyên ngành, nơi cấp, năm, số chứng chỉ, ngày cấp.
- `POST /employees/36/qualifications` trả **201**, tạo thêm bằng ID 36 thay vì cảnh báo trùng.
- Mong đợi: **409** hoặc xác nhận nghiệp vụ có kiểm soát; không âm thầm nhân đôi cùng một bằng.
- Khuyến nghị: quy tắc nhận diện bằng trùng theo nhân viên + số chứng chỉ/đơn vị cấp; với bằng không có số, kiểm tra tổ hợp nghiệp vụ. Bảo vệ cả trường hợp đồng thời.
- Bằng chứng: `write-results.json` → `D15-identical-degree`.

### QA-06 — P2: Tạo kỳ lương trùng trả 500

- Tạo lại mã `QA6-PAY-MW4LVRAA` qua `POST /api/payroll-periods`.
- Thực tế **500**; mong đợi **409** và thông báo mã đã tồn tại.
- Source `PayrollPeriodService.createPeriod()` ném RuntimeException cho lỗi trùng.
- Bằng chứng: `payroll-state-results.json` → `duplicate-period`.

### QA-07 — P2: Kỳ lương đảo ngày trả 500

- POST kỳ mới với `periodStart=2041-02-20`, `periodEnd=2041-02-01`.
- Thực tế **500**; mong đợi **400** và thông báo ngày không hợp lệ.
- Source kiểm tra ngày nhưng ném RuntimeException.
- Bằng chứng: `payroll-state-results.json` → `reversed-period`.

### QA-08 — P2: Công bố bảng lương chưa chốt trả 500

- Kỳ QA riêng 2, batch 2 trạng thái NHAP. `POST /api/payroll-batches/2/publish`.
- Thực tế **500**; mong đợi **409** và thông báo phải chốt trước.
- Ràng buộc đã chặn việc công bố; lỗi nằm ở phân loại exception/HTTP và trải nghiệm xử lý.
- Bằng chứng: `payroll-state-results.json` → `K8-publish-before-lock`.

### QA-09 — P2: Chốt bảng lương chưa tính trả 500

- `POST /api/payroll-batches/2/lock` trên batch QA chưa có kết quả tính.
- Thực tế **500**; mong đợi **409** và hướng dẫn tính trước.
- Không có bảng lương nào bị chốt bởi kiểm thử này.
- Bằng chứng: `payroll-state-results.json` → `K6-lock-before-calculation`.

### QA-10 — P3: Trang Tiền lương chưa dịch khi chọn EN

- Web thật: chọn EN; menu/breadcrumb đã hiện Payroll, nhưng H1 vẫn `Quản lý Tiền lương`.
- Các nhãn `Chọn kỳ tính lương`, `TỔNG QUỸ GROSS`, `TỔNG KHẤU TRỪ`, `Tính tự động`, `Chốt lương`, `Công bố` vẫn tiếng Việt.
- Loại nghỉ phép đã đổi nhãn EN đúng ở lượt kiểm tra đối chứng; tên dữ liệu như Phép năm giữ nguyên là hợp lý.
- Khuyến nghị: dùng LanguageService cho toàn bộ nhãn, trạng thái, lỗi và câu xác nhận của payroll, không chỉ header chung.

### QA-11 — P3: Trang Tiền lương tự dựng mã nhân viên không khớp hồ sơ

- Web thật hiển thị `NV-001`, `NV-002`... thay vì mã thực tế `DEMO001`, `DEMO002`...; không có họ tên để đối chiếu.
- Source `frontend/src/app/pages/payroll/payroll.html` dòng 110 và 146 ghép `NV-00{{ employeeId }}`.
- DTO chi tiết lương chỉ có employeeId, chưa có mã/tên.
- Khuyến nghị: bổ sung mã và tên thực từ response hoặc lookup được phân quyền; không tự sinh mã hiển thị từ khóa nội bộ DB.

## Các luồng quan trọng đã qua

- Đăng nhập API ADMIN/HR/trưởng phòng HCNS/trưởng phòng DT/nhân viên HCNS/nhân viên DT thành công. Web ADMIN đăng nhập được.
- HR ngoài HCNS: hồ sơ, quỹ, số dư, bằng, hợp đồng, đơn và audit của DEMO008 bị403; truy cập DT có ca dương tính200 đối chứng.
- Lịch/công/tổng hợp công ngoài phạm vi trả200 nhưng **danh sách rỗng**, không coi đây là lộ dữ liệu.
- Danh sách/tìm kiếm HR không chứa DEMO008; phụ cấp/tính phụ cấp/xem trước phiếu ngoài phạm vi403, trong phạm vi200.
- CCCD/địa chỉ QA hiện đúng cho ADMIN, HR đúng phạm vi và trưởng phòng DT, cả trong audit.
- EMPLOYEE bị403 khi tạo tài khoản, đọc quản trị lương, giả employeeId xem phiếu người khác; HR tự cấp phạm vi403.
- Logout204; JWT đã logout gọi `/api/auth/me` nhận401.
- Thiếu/sai/đảo tham số ngày tìm lịch400; không có hồ sơ404; chưa đăng nhập401.
- Hai request tạo cùng mã chức vụ đồng thời: một201, một409. Trùng khác hoa thường409.
- Thêm nhân viên hợp lệ tạo đúng một phân công. PUT hợp lệ giữ email/CCCD/địa chỉ.
- Bằng đầu tự chính; thêm bằng thứ hai giữ bằng cũ; cập nhật chính đồng thời chỉ còn một chính; gỡ bằng chính khi còn bằng khác409.
- Nghỉ phép sai khoảng400; không có lịch409; nộp bù quá7ngày400; lý do trắng400; đơn chồng đang chờ409.
- Giả employeeId/status/approvedBy trong payload không đổi được chủ đơn hoặc tự duyệt.
- Duyệt cuối trước cấp1 bị409; trưởng phòng khác phòng403; duyệt cấp1 lặp409; từ chối trắng400.
- Nửa ca08–12 trừ4giờ; rút sau cấp1 được, duyệt sau rút409; nộp lại sau hủy được.
- Hai đơn tranh quỹ8giờ: chỉ một duyệt200, một409; số dư0, không âm. Hủy đồng thời200/409, hoàn đúng một lần về8giờ.
- Điều chuyển tương lai lập được. Thôi việc trước phân công tương lai409. Quyết định thôi việc tương lai204 nhưng hồ sơ vẫn DANG_LAM; quyết định lặp409.
- Tìm Hân/han/Unicode tổ hợp cho cùng tập ID; ký tự %, _, nháy không gây500; size0/quá lớn/page âm400; tên10.000ký tự400.
- Bốn endpoint xuất báo cáo ADMIN trả XLSX có chữ kýZIP đúng; HR/EMPLOYEE403. Đây chưa phải kiểm chứng nội dung từng ô Excel.
- Web: đang nhập nhân viên rồi chọn menu khác có cảnh báo; Tiếp tục nhập giữ dữ liệu; lưu thiếu thông tin hiện lỗi từng trường; hủy/bỏ bản nháp hoạt động.

## Những phần chưa được nghiệm thu

1. Chạy tới đúng ngày job thôi việc/hết nhiệm kỳ/hết hạn hợp đồng; giờ check-in 29/30/31phút, UTC gần nửa đêm. Không đổi đồng hồ máy/chạy job toàn DB để tránh tác động ngoài QA.
2. Nghỉ qua đêm, nhiều đoạn nghỉ, giao năm/kỳ quỹ, kỳ công/lương khóa; thu hồi scope/đổi trưởng phòng giữa lúc đang mở form.
3. Tính/chốt/công bố đầy đủ, nguồn đổi sau chốt, công bố đồng thời, phiên bản điều chỉnh và phiếu đã công bố. DB hiện chưa có payslip thật để thử IDOR trên một ID phiếu tồn tại. Kỳ dùng chung không bị chốt.
4. Thuế, người phụ thuộc, OT ngày lễ/ngày nghỉ và sàn/trần bảo hiểm: cần đặc tả đồ án được chốt trước khi tính PASS/FAIL; báo cáo này không suy diễn luật hay hằng số phải dùng.
5. Mất mạng đúng lúc lưu, đóng tab/F5 đang gửi, hai tài khoản nhiều tab; mọi CRUD trên tất cả màn hình.
6. Responsive1280×800/1366×768/mobile, zoom125%/200%, mọi trang EN/dark; công cụ chụp ảnh của tab QA không chụp được, nên không có số đo pixel hay ảnh để xác nhận các ca này.
7. XSS khi render mọi bề mặt, công thức Excel, inPDF, tải500 nhân viên và các hành vi bảo mật chưa thực thi. Không coi việc tìm kiếm chuỗi đặc biệt200 là đã kiểm chứng chốngXSS/SQLi toàn hệ thống.
8. Các integration test cũ yêu cầu DB_NAME=hrm_test không được chuyển bừa sang fullDB. Bằng chứng fullDB ở đây là API thật và truy vấn SQL; 50 regression BE nêu trên là unit/mocked tests.

## Dữ liệu QA và ảnh hưởng sau kiểm thử

- Nhân viên QA36: `QA6-mw4lfj2n`; QA37: `QA6-mw4lfj2nEMPTY`. Không xóa cứng hồ sơ.
- Hai chức vụ QA ID8/11; ID8 từ lần chuẩn bị bị từ chối vì giới tính gửi sai enum, không tạo nhân viên ở lần đó.
- QA36 có hai lịch10–11/11/2026, một quỹ8giờ, ba bằng còn lưu (bao gồm bản trùng để tái hiện lỗi).
- Các đơn nghỉ QA đã hủy/rút; quỹ QA còn8giờ, dùng0. Tài khoản QA ID35 `.worker` đã khóa.
- QA36 có quyết định thôi việc ngày làm cuối20/11/2026; job tương lai có thể thực thi **trên hồ sơ QA này**. QA37 có phân công tương lai25/11/2026. Giữ lại để tái hiện ràng buộc.
- Kỳ lương QA2 `QA6-PAY-MW4LVRAA`,02/2040, batch2 vẫnNHAP. Batch1 kỳ demo vẫnNHAP; không tính/chốt/công bố kỳ dùng chung.
- Logout thử dùng `demo.emp009` làm tăng token_version; phiên cũ của tài khoản đó hết hiệu lực, đăng nhập lại bình thường bằng mật khẩu cũ. Không đổi mật khẩu demo.
- SQL cuối phiên:0 nhân viên thiếu phân công,0 nhân viên có nhiều bằng chính, vẫn1ADMIN. Không sửa quyền HR mẫu hoặc vai trò tài khoản demo.
- Không git add/commit/push, không đưa qa_tester lên Git.

## Ưu tiên sửa

1. Chống mất cập nhật hồ sơ (QA-01).
2. Khóa validation Backend và lý do thay đổi; chặn bằng trùng (QA-02–05).
3. Đổi RuntimeException nghiệp vụ lương thành lỗi400/409 có thông điệp; kiểm tra lại rollback và trạng thái (QA-06–09).
4. Dịch trang lương và hiển thị mã/tên nhân viên thật (QA-10–11).
5. Sau sửa: chạy lại các ca FAIL này và regression, rồi xử lý các mục NOT_RUN ở phụ lục.

## Bằng chứng

Các file `*-results.json` lưu mã ca, actor, endpoint, mãHTTP và assertion; không lưu JWT/password/hash. `summary.json` thống kê regression và các ca FAIL. `checklist-coverage.json` đối chiếu145 mục gốc.

