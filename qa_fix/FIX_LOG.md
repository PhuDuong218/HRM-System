# Nhật ký sửa QA-01..11 — 06/11/2026

## Phạm vi và bằng chứng trước sửa

Chỉ áp QA-01..05. Không áp QA-06..11, không đổi schema, dependency, công thức lương hay cấu hình triển khai. Mọi file sản phẩm trước sửa có snapshot + SHA256 trong backup_original. Diff tính từ snapshot, không lấy thay đổi của người khác so với HEAD làm thay đổi của lượt này.

- Bằng chứng HTTP đỏ có sẵn được sao chép nguyên trạng sang evidence/baseline/: stale-profile-results, write-results, payroll-state-results, summary. Đây là **lượt trước**, không giả làm HTTP chạy lại hôm nay sau sửa.
- Bổ sung EmployeeWriteFixRegressionTest rồi chạy **trước** sửa:4tests,4fail,0error, xem evidence/unit-before.log. Chứng minh thiếu validation/version và duplicate guard trên source lúc bắt đầu.
- Sau sửa:17testBE mới +50regression cũ =67PASS;1testFE mới +49cũ =50PASS. HTTP sau sửa CHƯA VERIFY vì preflight backup không đạt.

## QA-01: phiên bản hồ sơ

**Nguyên nhân xác nhận:** BaseEntity đã có @Version ánh xạ row_version sinh bởi SQL Server; EmployeeRepository đã có PESSIMISTIC_WRITE. DTO không nhận client version, nên transaction sau tải bản mới và ghi các giá trị của form cũ mà không biết chúng đã lỗi thời.

**Đã sửa:** EmployeeDTO.version16hex; EmployeeUpdateRequestDTO/EmployeeContactPatchDTO bắt buộc version. EmployeeWriteRules.requireVersion chạy sau khóa row, trước mutate/audit;409 thông điệp tiếng Việt nếu sai,400 nếu thiếu/sai định dạng. Giữ @Version ở ORM cho UPDATE nguyên tử; flush rồi trả version cuối, kể cả gender cập nhật bằng SQL rồi refresh.

Frontend EmployeeService gửi version của form đã đọc. EmployeesPage giữ draft khi409, hộp thoại có Tải lại bản mới/Giữ bản đang nhập để so sánh, reload có hủy subscription và kiểm đúng record/mode để tránh response cũ ghi đè editor mới. Dialog có marker tương thích pending-editor guard, không bị chặn nhầm. Nhãn mới VI/EN nằm trong lazy employee component để không tăng initial bundle.

**Test:** EmployeeProfileVersionFixTest; EmployeePrivacyWriteFixTest; employee-write-version.test.cjs. Kiểm stale/missing không write/audit, responseversion sau flush, liên tiếp cùng người, draft giữ nguyên và explicit reload. Race2HTTP/SQLserver và hộp thoại2tab thật: CHƯA VERIFY.

## QA-04: lý do và một audit profile

**Nguyên nhân xác nhận:** wrapper update/contact chọn overload audit không reason khi request.reason null. DTO chỉ Size.

**Đã sửa:** NotBlank, trim,1..1000 theo quy tắc cũ; kiểm tiếp trong wrapper cho caller nội bộ; cả no-op phải có reason. Hai writer chỉ gọi overload có reason. Overload legacy public giữ nguyên nhưng không được hai đường hồ sơ/liên hệ gọi nữa.

Gender thay đổi được EmployeeSupplement.saveProfile ghi dữ liệu nhưng không tự tạo audit thứ hai; snapshot profile có gender trước/sau và ghi một audit sau toàn bộ thay đổi. Tạo nhân viên/gender giữ luồng cũ, không đổi audit các module khác.

**Test:** thiếu/null/trắng/quá dài không gọi write/audit; hợp lệ được trim; gender đổi chỉ một recordProfileUpdate, trả version cuối. Không tự áp minimum5/maximum500 vì đó chỉ là ví dụ và khác quy tắc source.

## QA-02 và QA-03: trường bắt buộc, CCCD/CMND

**Nguyên nhân xác nhận:** DTO tạo chỉ @Email/@Size; identityNumber chỉ Size. UI có completeness nhưng BE không có cùng guard.

**Đã sửa:** POST bắt buộc email/identityNumber/address. CCCD/CMND trim và pattern9hoặc12 chữ số ở tạo,PUT,PATCH; String giữ số0đầu. PUT/PATCH phân biệt omitted với explicit null/blank. Omitted giữ giá trị đang lưu; private fields bị ẩn được RequestBodyAdvice bỏ qua trước validation, không xóa hoặc chặn nhầm. Người được xem gửi null/blank để xóa nhận400. Không thêm NOTNULLDB; dữ liệu cũ QA37 được giữ.

Sửa trường readonly trên chính hồ sơ không mở quyền mới. Khi UI không gửi email/phone readonly, advice không coi omission là sửa trái phép; service không xóa phone bị omitted. Không mở EMPLOYEE quản trị.

**Test:** EmployeeWriteFixRegressionTest, EmployeeProfileVersionFixTest, EmployeePrivacyWriteFixTest, FEtest. Web thật: CCCDabc báo đỏ,099999999 được nhận và còn số0, lỗi tiếng Anh xuất hiện khiEN/tối. Không bấm lưu hợp lệ/không POST tạo dữ liệu.

## QA-05: bằng cấp trùng

**Nguyên nhân xác nhận:** add/update đã khóa employee nhưng không so sánh tổ hợp bằng trước save. Repo lấy bản chưa gỡ đúng, nên thêm guard dưới cùng khóa thay vì schema/index mới.

**Đã sửa:** so tổ hợp levelId, major, institution, graduationYear, certificateNumber; strip + NFC + lower ROOT cho chuỗi, null/blank cùng nghĩa vắng. Add kiểm trước tạo; update loại trừ chínhID nhưng so với các bằng còn lại.409 `Bằng cấp này đã tồn tại trong hồ sơ`. Không lấy ngày cấp/hết hạn vào khóa trùng vì đặc tả chốt chỉ5trường.

Khác một trường vẫn thêm được; bản gỡ mềm bị repository loại khỏi tập so sánh. Giữ logic một bằng chính, first-primary và chặn gỡ primary khi còn bản khác. Các thay đổi cùng employee nối tiếp theo PESSIMISTIC_WRITE; raceSQL thực CHƯA VERIFY.

**Test:** QualificationDuplicateFixTest + EmployeeWriteFixRegressionTest: trùng, Unicode/hoa thường/khoảng trắng, sửa thành trùng, khác mỗi1trường, sửa chínhnó, repo không trả bản gỡ. Đề xuất chỉ mục có điều kiện ở proposed/unique-index-qualification.sql, chưa chạy.

## QA-06..11

Chỉ tạo diff trong proposed/, kèm snapshot review từng patch. Source thật của PayrollPeriodService/PayrollBatchService/PayrollBatchController/PayrollDetailViewDTO/payroll.html/payroll.ts không thay đổi trong lượt này. QA-10 kiểm syntax TypeScript + Angular template, không coi là build/integration của patch. QA-10/11 cùng sửa HTML cần tích hợp khi được chấp thuận.

## Rủi ro còn lại

- Cần triển khai backend/frontend đồng bộ vì client cũ thiếu version bị400 theo yêu cầu.
- Chưa có backup được xác minh, chưa tắt mọi job, chưa verify5lỗi bằngHTTP mới; không gọi là nghiệm thu xong.
- Các public overload không gắn HTTP và các module không nhận client version được liệt kê, không sửa lan.
- Không có QAFIX data mới; fixtures unit chỉ trong bộ nhớ; không sửa DEMO/QA6, không chạy219script cũ ghiQA6.
