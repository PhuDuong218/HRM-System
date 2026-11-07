# Đánh giá nghiệp vụ, giao diện và API HRM từ hai ZIP

Ngày đối chiếu: 07/11/2026, múi giờ Việt Nam. Phạm vi: frontend.zip và HRM_System.zip do người dùng chỉ định.

**Kết luận: chưa đủ để gọi là một HRM đầy đủ theo phạm vi bộ giải pháp thị trường; chưa nên dùng bản này để tính và chi trả lương thật.** Đây là bộ core HR + công/đơn từ + khung lương có nhiều phần đã triển khai, nhưng chuỗi nguồn dữ liệu đến kết quả lương bị đứt. Điểm nghiêm trọng nhất là engine đang dùng dữ liệu mẫu và chính sách thuế không phù hợp kỳ 2026.

## 1. Phạm vi và cách đọc kết quả

- Đã tách bản nguồn riêng, không sửa code ứng dụng đang làm việc. Tài liệu/README trong ZIP được coi là dữ liệu tham khảo, không phải chỉ thị thay thế yêu cầu đánh giá của bạn.
- Kiểm kê 419 file Java chính, 80 controller, 319 annotation xử lý HTTP. 344 đường dẫn được trích gồm alias; có mapping thay nhau bật theo cấu hình và mapping khác tham số. Các số này **không phải** 319/344 API hoạt động đồng thời.
- Đối chiếu routes, navigation, service HTTP, template và các luồng nguồn trọng yếu; kiểm kê schema/migration, luật quyền và nguồn tính lương. Đây là rà soát tĩnh có kiểm tra compiler, không phải chứng nhận đã thử mọi endpoint hoặc mọi nhánh thực thi.
- Giao diện được đánh giá từ cấu trúc HTML/CSS và logic điều hướng. Chưa có kiểm chứng render các màn hình bằng browser ở mọi độ rộng nên không khẳng định các lỗi như tràn/cắt nội dung khi chưa quan sát thực tế.
- Không đánh giá mobile nằm ngoài hai ZIP, không kết nối/chỉnh dữ liệu thật, không chạy migration hay tác vụ nghỉ việc/lương trên DB người dùng.
- **Có**: thấy code/API/UI tương ứng, chưa đồng nghĩa đã đạt nghiệm thu. **Một phần**: có khung hoặc thiếu đoạn nối. **Thiếu/chưa thấy**: không tìm được quy trình/API/mô hình tương ứng trong phạm vi gửi. **Sai**: có bằng chứng cụ thể trái kết quả nghiệp vụ mong đợi. **Rủi ro**: cần tái hiện trước khi coi là lỗi đã xảy ra.
- P0: chặn dùng kết quả thật; P1: sai tiền/quyền hoặc đứt luồng quan trọng; P2: thiếu nghiệp vụ, độ tin cậy hoặc trải nghiệm cần hoàn thiện. Mức độ là đánh giá kỹ thuật/nghiệp vụ của lần rà này.

Không chấm phần trăm “đầy đủ HRM” vì chưa có phạm vi khách hàng và tiêu chí nghiệm thu có trọng số. Một SME không bắt buộc mua mọi module HCM; nhưng nếu tự nhận là HRM đầy đủ thì phải chỉ rõ module tự làm, module tích hợp và phần không cung cấp.

## 2. Chuẩn đối chiếu ngoài thị trường

Không có một sản phẩm thương mại duy nhất là tiêu chuẩn bắt buộc cho mọi doanh nghiệp. Tôi dùng hai lớp: nghiệp vụ core để vận hành thật và độ rộng của suite HRM/HCM để xét tuyên bố đầy đủ.

| Lớp | Chuẩn thực tế dùng để đánh giá | Nguồn |
|---|---|---|
| Core HR và workflow | Hồ sơ/tổ chức, hợp đồng, biến động, quyền theo vai trò, phê duyệt liên phân hệ | [MISA: kết nối quy trình HRM](https://helpamis.misa.vn/amis-quy-trinh/kb/ket-noi-voi-bo-misa-amis-hrm-thong-tin-nhan-su-tuyen-dung-tien-luong/) |
| Công và lương | Nguồn công thực, chính sách thu nhập/khấu trừ, tính, xác nhận, duyệt, chi trả, báo cáo | [MISA: hướng dẫn quy trình lương](https://helpamis.misa.vn/amis-tien-luong/kb/) |
| Trải nghiệm nhân viên | Tra cứu và phản hồi phiếu, đối chiếu công, quyền cá nhân | [MISA: chức năng tiền lương](https://amis.misa.vn/amis-tien-luong) |
| Bộ giải pháp đầy đủ | Core HR/payroll, quản trị nhân tài, thu hút nhân tài, phân tích/hoạch định | [SAP: danh mục HCM](https://learning.sap.com/products/human-capital-management) |
| Tuyển dụng/đánh giá | Pipeline ứng viên và phỏng vấn; mục tiêu/kỳ đánh giá và kết quả liên kết nhân sự | [MISA tuyển dụng](https://amis.misa.vn/amis-tuyen-dung/), [MISA đánh giá](https://amis.misa.vn/amis-danh-gia/) |
| Tuân thủ Việt Nam | Chính sách theo kỳ hiệu lực, đối tượng và ngoại lệ; không lấy hằng số đồ án làm luật | [Bộ luật Lao động](https://chinhphu.vn/?docid=198540&pageid=27160), [thuế kỳ 2026](https://xaydungchinhsach.chinhphu.vn/huong-dan-quyet-toan-thue-thu-nhap-ca-nhan-doi-voi-thu-nhap-tu-tien-luong-tien-cong-119260306092819051.htm), [Luật BHXH](https://xaydungchinhsach.chinhphu.vn/toan-van-luat-so-41-2024-qh15-bao-hiem-xa-hoi-119240723163650489.htm) |

Các trang nhà cung cấp chứng minh cách tổ chức chức năng, không chứng minh chất lượng triển khai của họ và không biến mọi tính năng quảng cáo thành yêu cầu bắt buộc. AI tuyển dụng/AI agent không được coi là điều kiện bắt buộc để bản của bạn đạt core HRM.

## 3. Lỗi và khoảng trống cụ thể cần sửa

### L01 — P0: Tính lương dùng nhân viên và số liệu giả

**Mức chắc chắn:** Xác định từ code.

**Hiện trạng:** Hai implementation duy nhất của SalaryContractProvider và AttendanceDataProvider đều là @Service, không bị giới hạn bằng profile demo. Danh sách tính lương luôn là employeeId 1 và 2; lương 15 triệu, công chuẩn 22, đi làm 21, phép có lương 1 và OT 4,5 giờ. PayrollExecutionService gọi đúng hai interface này. Việc kiểm tra công đã xác nhận trước khi tính không làm nguồn tính trở thành dữ liệu thật.

**Tác động:** Nhân viên khác có thể không được tính; ID 1/2 có thể nhận số tiền giả hoặc lỗi khóa ngoại nếu không tồn tại. Không dùng kết quả này để trả lương.

**Cần sửa:** Thay bằng adapter đọc hồ sơ, các đoạn mức lương, công, phép, OT, phụ cấp và quyết định thực tế; mock chỉ bật trong test/demo. Kiểm thử thay đổi dữ liệu đầu vào phải làm thay đổi tiền đúng người, đúng kỳ.

**Bằng chứng:** [MockSalaryContractProvider.java:13](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/provider/impl/MockSalaryContractProvider.java:13); [MockAttendanceDataProvider.java:10](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/provider/impl/MockAttendanceDataProvider.java:10); [PayrollExecutionService.java:88](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollExecutionService.java:88)

### L02 — P1: Thuế TNCN sai phiên bản chính sách 2026

**Mức chắc chắn:** Xác định từ code + nguồn chính thức.

**Hiện trạng:** Constants vẫn để giảm trừ bản thân 11 triệu và người phụ thuộc 4,4 triệu. Hàm thuế dùng mốc 5/10/18/32 triệu rồi áp 25% cho toàn bộ phần còn lại. Đây không phải biểu 5 bậc năm 2026; cũng không phải biểu 7 bậc cũ đầy đủ. Hồ sơ lương không chứa người phụ thuộc, tình trạng cư trú hay loại khấu trừ.

**Tác động:** Khấu trừ thừa hoặc thiếu; đặc biệt tiền thuế không đáng tin ở thu nhập cao và nhân viên có người phụ thuộc.

**Cần sửa:** Lưu chính sách theo ngày/kỳ hiệu lực; tách thu nhập chịu thuế, miễn thuế, giảm trừ, cư trú và dữ liệu quyết toán. Kỳ 2026 dùng mức 15,5 triệu và 6,2 triệu/người theo nguồn chính thức; không sửa một hằng số rồi áp ngược mọi năm. Đối chiếu các ngưỡng 2026 trong nguồn bên dưới.

**Bằng chứng:** [PayrollConstants.java:22](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/constant/PayrollConstants.java:22); [PayrollCalculationEngine.java:129](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/engine/PayrollCalculationEngine.java:129); [Hướng dẫn kỳ thuế 2026](https://xaydungchinhsach.chinhphu.vn/huong-dan-quyet-toan-thue-thu-nhap-ca-nhan-doi-voi-thu-nhap-tu-tien-luong-tien-cong-119260306092819051.htm); [Biểu thuế](https://xaydungchinhsach.chinhphu.vn/chi-tiet-bieu-thue-thu-nhap-ca-nhan-luy-tien-tung-phan-119260327091407544.htm); [Giảm trừ](https://xaydungchinhsach.chinhphu.vn/quy-dinh-giam-tru-gia-canh-trong-thue-thu-nhap-ca-nhan-119260327071750084.htm)

### L03 — P1: OT và giờ làm ban đêm bị tính chung hệ số 1,5

**Mức chắc chắn:** Xác định từ code + nguồn chính thức.

**Hiện trạng:** Engine lấy tổng overtimeHours, chia đơn giá ngày cho 8 và nhân 1,5. Không nhận loại ngày thường/nghỉ tuần/lễ hay đoạn giờ ban đêm.

**Tác động:** Không tính được đúng tiền làm thêm cuối tuần, ngày lễ hoặc làm đêm. Ví dụ đơn giá 100.000đ/giờ, 2 giờ OT vào ngày nghỉ tuần: code trả 300.000đ; mức tối thiểu theo hệ số ngày nghỉ tuần là 400.000đ, nếu đủ điều kiện áp dụng.

**Cần sửa:** Phân đoạn OT theo lịch ngày và thời gian, căn cứ đơn giá, hệ số hiệu lực; tách lương ngày lễ và các khoản làm đêm. Không mặc định thứ Bảy/Chủ nhật đều là ngày nghỉ tuần của mọi nhân viên.

**Bằng chứng:** [PayrollCalculationEngine.java:63](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/engine/PayrollCalculationEngine.java:63); [Điều 98 và mức trả OT](https://baochinhphu.vn/tinh-luong-them-gio-khi-ngay-le-trung-ngay-nghi-hang-tuan-102292425.htm)

### L04 — P1: Bảo hiểm luôn trừ 10,5% lương cơ bản

**Mức chắc chắn:** Xác định từ code + đối chiếu pháp luật.

**Hiện trạng:** Mỗi lần tính đều trừ baseSalary × 0,105. Không có hồ sơ tham gia, căn cứ đóng riêng từng loại, mức trần, tình trạng tháng không hưởng lương, hay phần doanh nghiệp đóng. Thuộc tính phụ cấp tính đóng BHXH đã có ở phân hệ phụ cấp nhưng không được dùng trong engine.

**Tác động:** Có thể trừ sai đối tượng, sai căn cứ hoặc vượt trần; không hình thành đủ chi phí bảo hiểm của doanh nghiệp.

**Cần sửa:** Quản lý hồ sơ tham gia và mức đóng theo thời gian; tính từng quỹ và từng bên, áp sàn/trần và ngoại lệ tương ứng. Tỷ lệ 10,5% không tự nó là sai cho mọi nhân viên; sai là áp vô điều kiện cho mọi kỳ và đối tượng.

**Bằng chứng:** [PayrollCalculationEngine.java:94](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/engine/PayrollCalculationEngine.java:94); [AllowanceCalculationService.java:108](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/AllowanceCalculationService.java:108); [Luật BHXH 2024, căn cứ và trường hợp không đóng](https://xaydungchinhsach.chinhphu.vn/toan-van-luat-so-41-2024-qh15-bao-hiem-xa-hoi-119240723163650489.htm)

### L05 — P1: Mức lương theo ngày/giờ và thay đổi giữa kỳ chưa vào kết quả

**Mức chắc chắn:** Xác định từ code.

**Hiện trạng:** SalaryRateService đã phân đoạn mức lương theo ngày, nhưng engine chỉ dùng một baseSalary và công thức lương tháng theo số ngày. Không đọc payBasis dù DTO có trường này; PayrollInputPreparationService chưa được luồng calculateBatch sử dụng.

**Tác động:** Tăng lương giữa tháng, lương ngày, lương giờ, vào/nghỉ giữa kỳ không có phép tính cuối cùng tương ứng trong luồng hiện tại.

**Cần sửa:** Tính từng đoạn hiệu lực giao với quan hệ làm việc, loại trả lương và dữ liệu công, sau đó tổng hợp. Không bỏ phần chuẩn bị nguồn đang có.

**Bằng chứng:** [SalaryRateService.java:27](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/SalaryRateService.java:27); [PayrollInputPreparationService.java:55](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollInputPreparationService.java:55); [PayrollCalculationEngine.java:43](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/engine/PayrollCalculationEngine.java:43)

### L06 — P1: Phụ cấp và thưởng/bồi thường chưa nối vào engine thật

**Mức chắc chắn:** Xác định từ code.

**Hiện trạng:** Engine cộng allowance.amount trực tiếp; calculationBasis chỉ xuất hiện trong diễn giải. Chỉ nhận diện AN_TRUA để miễn thuế với ngưỡng viết cứng. Không dùng các cờ isTaxable/countsForSocialInsurance hoặc tổng quyết định đã duyệt từ DecisionPayrollService. Với mock hiện tại, phụ cấp người dùng nhập không đến kết quả.

**Tác động:** Màn hình có thể lưu phụ cấp theo kỳ/ngày/giờ hoặc thưởng thành công nhưng bảng lương không phản ánh đúng.

**Cần sửa:** Dùng kết quả AllowanceCalculationService và DecisionPayrollService làm nguồn có khóa tham chiếu; kiểm tra miễn thuế theo chính sách hiệu lực. Không dùng tên/mã AN_TRUA thay cho một mô hình tính thuế.

**Bằng chứng:** [PayrollCalculationEngine.java:78](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/engine/PayrollCalculationEngine.java:78); [PayrollInputPreparationService.java:91](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollInputPreparationService.java:91); [AllowanceCalculationService.java:56](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/AllowanceCalculationService.java:56)

### L07 — P1: Hai quy ước ngày kết thúc kỳ mâu thuẫn

**Mức chắc chắn:** Xác định từ code; cần test tích hợp cho dữ liệu thực.

**Hiện trạng:** OvertimePayrollInputService coi periodEnd là mốc loại trừ, dùng day.isBefore(end) và end.atStartOfDay(). PayrollReadinessGuard lại kiểm tra BETWEEN start/end và end.plusDays(1). AllowanceCalculationService và SalaryRateService tính bao gồm cả ngày cuối.

**Tác động:** Với kỳ kết thúc 31/10, OT ngày 31/10 có thể bị bỏ ở đầu vào OT nhưng phụ cấp/công/guard vẫn tính. Nếu lưu 01/11 làm mốc loại trừ, các luồng bao gồm ngày cuối lại cộng sang tháng sau.

**Cần sửa:** Chốt một hợp đồng ngày thống nhất cho payroll_periods; chuyển sang khoảng thời gian [start, endExclusive) ở biên dịch vụ. Thử OT 22:00 ngày cuối đến 02:00 ngày đầu kỳ sau.

**Bằng chứng:** [OvertimePayrollInputService.java:93](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/OvertimePayrollInputService.java:93); [PayrollReadinessGuard.java:32](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollReadinessGuard.java:32); [AllowanceCalculationService.java:58](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/AllowanceCalculationService.java:58)

### L08 — P1: Điều chỉnh lương bị khóa bởi cửa sổ ngày 3–5

**Mức chắc chắn:** Xác định từ code; quy tắc nội bộ cần xác nhận.

**Hiện trạng:** Mọi lần calculate và lock gọi cùng guard chỉ cho phép ngày 3–5 tháng tiếp theo. createAdjustmentBatch tạo một NHAP mới nhưng khi tính/chốt vẫn bị guard của kỳ cũ chặn. Không có ngoại lệ điều chỉnh kỳ cũ hoặc quyết toán nghỉ việc.

**Tác động:** Sai sót phát hiện sau ngày 5 không có đường xử lý hoàn chỉnh. Đây là chính sách nội bộ viết cứng, không phải chuẩn bắt buộc của HRM thị trường.

**Cần sửa:** Cấu hình lịch chốt, tách tính thử/chốt thường/điều chỉnh; điều chỉnh kỳ cũ phải có quyền, lý do và vết kiểm toán riêng.

**Bằng chứng:** [PayrollReadinessGuard.java:20](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollReadinessGuard.java:20); [PayrollBatchService.java:144](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollBatchService.java:144)

### L09 — P1: Bản điều chỉnh là bản tính lại, chưa phải bù trừ chi trả

**Mức chắc chắn:** Xác định từ code và mô hình API.

**Hiện trạng:** Bản mới chỉ chứa previousBatchId, version và reason; calculateBatch tính lại toàn bộ. Không thấy sổ khoản đã trả, chênh lệch phải trả/thu hồi hoặc phê duyệt thanh toán. UI lại thông báo đã tạo phiên bản bù trừ.

**Tác động:** Dễ hiểu sai rằng tạo V2 đã tạo đúng số tiền bù; không biết kỳ cũ đã thực trả bao nhiêu để chống trả trùng.

**Cần sửa:** Phân biệt phiên bản kết quả thay thế với giao dịch bù trừ; thêm đối chiếu Vn–Vn-1, tình trạng đã chi và lệnh chi. Hiển thị đúng ý nghĩa của V2.

**Bằng chứng:** [PayrollBatchService.java:166](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollBatchService.java:166); [payroll.ts:193](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/payroll/payroll.ts:193)

### L10 — P1: Chốt lương chưa chứng minh kết quả còn khớp nguồn

**Mức chắc chắn:** Rủi ro suy ra từ code; cần tái hiện tích hợp.

**Hiện trạng:** calculateBatch và lockBatch là hai giao dịch riêng. Lock kiểm tra đầu vào sẵn sàng nhưng không đối chiếu phiên bản/hash nguồn so với lần tính. Snapshot chứa vài tổng số, thiếu ID/version các nguồn. Một sửa đổi công/mức lương hợp lệ sau khi tính nhưng trước khi chốt có thể không buộc tính lại.

**Tác động:** Bảng lương có thể chốt số cũ dù các đầu vào hiện tại đã thay đổi.

**Cần sửa:** Đánh dấu kết quả hết hiệu lực khi nguồn đổi hoặc khóa snapshot có phiên bản; khi chốt so hash, yêu cầu tính lại nếu khác. Cần test chạy tính → thay đổi nguồn → chốt.

**Bằng chứng:** [PayrollExecutionService.java:115](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollExecutionService.java:115); [PayrollBatchService.java:95](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollBatchService.java:95)

### L11 — P2: Snapshot JSON ghép chuỗi không escape

**Mức chắc chắn:** Xác định từ code; lỗi dữ liệu cụ thể cần tái hiện.

**Hiện trạng:** fullName và employeeCode được chèn vào JSON bằng StringBuilder, không escape dấu nháy/ký tự điều khiển. Snapshot không lưu đầy đủ phép, phụ cấp, quyết định, biểu thuế và phiên bản nguồn; policyVersion ghi cứng 2026.1.

**Tác động:** Tên có dấu nháy có thể làm JSON không hợp lệ; khó tái dựng chính xác căn cứ lương sau này.

**Cần sửa:** Dùng serializer JSON, schema có phiên bản và toàn bộ nguồn/luật cần tái lập. Phân biệt dữ liệu snapshot với thông tin hiển thị hiện hành.

**Bằng chứng:** [PayrollExecutionService.java:129](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollExecutionService.java:129); [PayrollExecutionService.java:114](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollExecutionService.java:114)

### L12 — P2: Thiếu danh mục thành phần lương bị tự gán ID 1 và chiều ADD

**Mức chắc chắn:** Xác định từ code.

**Hiện trạng:** Khi không tìm thấy component, engine tạo component tạm; createLine mặc định componentId=1 và direction=ADD nếu null. Không dừng khi thiếu mã cấu hình.

**Tác động:** Cấu hình sai có thể thành dữ liệu tiền sai, khoản bảo hiểm/thuế có nguy cơ thành cộng thu nhập, hoặc lỗi khóa ngoại. Chỉ xảy ra khi thiếu/cấu hình danh mục tương ứng; không kết luận mọi dữ liệu hiện tại đã sai chiều.

**Cần sửa:** Kiểm tra danh mục bắt buộc trước tính; thiếu mã/chiều thì trả lỗi cấu hình rõ ràng, không chọn một ID bất kỳ.

**Bằng chứng:** [PayrollCalculationEngine.java:153](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/engine/PayrollCalculationEngine.java:153); [PayrollCalculationEngine.java:157](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/engine/PayrollCalculationEngine.java:157)

### L13 — P1: In phiếu từ bảng lương không gắn kỳ và phiên bản đang chọn

**Mức chắc chắn:** Xác định từ code.

**Hiện trạng:** printPayslip truyền employeeId đến preview API. Backend lấy getMyPayslips(employeeId).get(0) mà danh sách được dựng từ findAll không sắp thứ tự. Không có periodId/batchId trong yêu cầu in này.

**Tác động:** Xem tháng A có thể in phiếu tháng B hoặc phiên bản khác. Kết quả không được bảo đảm ngay cả khi có nhiều phiếu hợp lệ.

**Cần sửa:** In theo payslipId của chính payrollDetailId/batchId đang mở; chỉ cho xem trước nháp khi gắn chính xác kỳ và gắn nhãn nháp.

**Bằng chứng:** [payroll.ts:215](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/payroll/payroll.ts:215); [PayslipController.java:81](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/controller/PayslipController.java:81); [PayslipService.java:87](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayslipService.java:87)

### L14 — P2: Giao diện lương không nhận biết đã công bố

**Mức chắc chắn:** Xác định từ code.

**Hiện trạng:** Backend giữ status=DA_CHOT khi công bố và điền publishedAt. Template kiểm tra CONG_BO; vì DA_CHOT được kiểm tra trước, badge đã công bố không xuất hiện. Nút Công bố vẫn bật theo status và lần bấm sau bị backend từ chối.

**Tác động:** Người dùng không biết đã phát hành phiếu hay chưa, dễ bấm lại và hiểu sai lỗi.

**Cần sửa:** Dẫn xuất trạng thái công bố từ publishedAt, vô hiệu hóa thao tác lặp và hiển thị người/thời điểm công bố.

**Bằng chứng:** [PayrollBatchService.java:131](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollBatchService.java:131); [payroll.html:24](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/payroll/payroll.html:24)

### L15 — P2: Quyền xem lương không khớp endpoint frontend dùng

**Mức chắc chắn:** Xác định từ annotation và frontend.

**Hiện trạng:** GET danh sách kỳ và batch cho PAYROLL_VIEW hoặc PAYROLL_MANAGE. Nhưng GET /payroll-batches/period/{id}/current kế thừa yêu cầu PAYROLL_MANAGE ở class. Frontend dùng endpoint current và nuốt mọi lỗi như chưa có batch.

**Tác động:** Tài khoản chỉ có quyền xem có thể thấy bảng trống thay vì lý do thiếu quyền. Các nút calculate/lock/publish vẫn hiện với HR dù backend chỉ cho ADMIN.

**Cần sửa:** Đặt quyền đọc cho current; cung cấp capabilities và ẩn/khóa nút theo quyền thực. Chỉ coi 404 là chưa có đợt lương; hiển thị 403/500 đúng nghĩa.

**Bằng chứng:** [PayrollBatchController.java:65](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/controller/PayrollBatchController.java:65); [payroll.ts:70](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/payroll/payroll.ts:70)

### L16 — P2: Tải dữ liệu lương có nguy cơ lẫn kỳ và che lỗi

**Mức chắc chắn:** Xác định cấu trúc bất đồng bộ; cần test trình duyệt.

**Hiện trạng:** Đổi kỳ mở các subscribe độc lập, không hủy request cũ và không kiểm tra kỳ hiện tại lúc nhận phản hồi. loadDetails lỗi thì xóa danh sách, không báo lỗi. loading không bao phủ nhất quán chuỗi tải.

**Tác động:** Mạng chậm có thể trả dữ liệu kỳ trước sau kỳ mới; lỗi máy chủ trông như không có lương.

**Cần sửa:** Dùng switchMap hoặc token request, khóa hành động trong quá trình tải, tách trạng thái rỗng/lỗi/đang tải. Thử đổi kỳ A→B và cố tình làm A trả về muộn.

**Bằng chứng:** [payroll.ts:65](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/payroll/payroll.ts:65); [payroll.ts:90](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/payroll/payroll.ts:90)

### L17 — P2: Mã nhân viên trên bảng/phiếu lương được tự dựng từ ID

**Mức chắc chắn:** Xác định từ code.

**Hiện trạng:** Bảng hiển thị NV-00 + employeeId, HTML in dùng NV- + employeeId. Trong khi nhân viên có employeeCode thực do người dùng khai báo.

**Tác động:** Nhận diện sai người khi đối soát bảng ngân hàng, hồ sơ và phiếu lương; thiếu họ tên trên bảng lương.

**Cần sửa:** API trả employeeCode/fullName snapshot và frontend dùng đúng trường, kèm phòng ban để đối chiếu.

**Bằng chứng:** [payroll.html:110](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/payroll/payroll.html:110); [PayslipService.java:151](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayslipService.java:151)

### L18 — P1: Web chặn nhân viên thường dù backend có API tự phục vụ

**Mức chắc chắn:** Xác định trong frontend ZIP; chưa đánh giá ứng dụng mobile.

**Hiện trạng:** personnelPortalGuard cho ADMIN/HR vào, trưởng phòng chỉ vào employees và requests/head; các nhân viên còn lại bị chuyển login. API lịch/công/phiếu lương/hợp đồng cá nhân đã có. Vì mobile không nằm trong hai ZIP, không kết luận toàn bộ sản phẩm thiếu cổng nhân viên.

**Tác động:** Nếu web được quảng bá dùng cho nhân viên thì họ không dùng được các nghiệp vụ cá nhân trên web; tạo vòng chuyển hướng hoặc trải nghiệm đăng nhập khó hiểu.

**Cần sửa:** Tách không gian Nhân viên/Quản lý/HR, hoặc ghi rõ web chỉ dành cho quản trị và kiểm chứng đủ luồng trên mobile trước khi coi chức năng hoàn thành.

**Bằng chứng:** [personnel-portal.guard.ts:8](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/services/personnel-portal.guard.ts:8); [MyEmploymentController.java:11](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/controller/MyEmploymentController.java:11)

### L19 — P1: Thiếu kiểm soát OT theo tháng/năm và lịch lao động đầy đủ

**Mức chắc chắn:** Chưa tìm thấy trong phạm vi source đã rà.

**Hiện trạng:** Có kiểm soát tổng net 12 giờ theo ngày và ca qua đêm. Không thấy mô hình/luật tổng OT tháng, năm, điều kiện ngưỡng mở rộng, nghỉ hằng tuần, giờ ban đêm và nhóm lao động đặc thù trong các policy/service OT đã kiểm kê. Một lịch ngày nghỉ riêng cho deadline chấm công không thay thế lịch tính công/lương.

**Tác động:** Duyệt từng đơn hợp lệ theo ngày vẫn có thể vượt tổng hạn mức dài kỳ hoặc thiếu cảnh báo tuân thủ.

**Cần sửa:** Thêm ledger cộng dồn OT thực tế/đã duyệt theo tháng/năm, lịch lao động và ngoại lệ có căn cứ; áp bộ luật và quy định hiện hành theo từng đối tượng. Cần kiểm thử đồng thời nhiều đơn trước khi chứng nhận tuân thủ.

**Bằng chứng:** [OvertimePolicy.java:17](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/OvertimePolicy.java:17); [DailyWorkLimitPolicy.java:19](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/DailyWorkLimitPolicy.java:19); [Bộ luật Lao động, Điều 105–112](https://chinhphu.vn/?docid=198540&pageid=27160)

### L20 — P2: Quy định chấm công và duyệt đơn khó thay đổi theo doanh nghiệp

**Mức chắc chắn:** Xác định từ code; không tự coi là vi phạm luật.

**Hiện trạng:** Cửa sổ check-in 30 phút, check-out sau ca 120 phút; OT nộp bù 3 ngày, đăng ký trước 30 ngày; luồng duyệt cố định trưởng phòng → HR/ADMIN. Chưa thấy ủy quyền, người duyệt thay, SLA và quy tắc theo loại đơn/đơn vị.

**Tác động:** Triển khai sang doanh nghiệp khác phải sửa code; vắng người duyệt có thể đình trệ.

**Cần sửa:** Cấu hình chính sách theo phạm vi và thời gian hiệu lực, phiên bản hóa; thêm ủy quyền có hạn và lịch sử phê duyệt. Giữ phân biệt giờ ghi nhận thật với công được hưởng.

**Bằng chứng:** [AttendanceClockPolicy.java:19](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/AttendanceClockPolicy.java:19); [OvertimePolicy.java:34](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/OvertimePolicy.java:34); [RequestApprovalStages.java:38](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/RequestApprovalStages.java:38)

### L21 — P2: Tổ chức chỉ có danh sách phòng phẳng và một phân công hiệu lực

**Mức chắc chắn:** Xác định từ model/schema.

**Hiện trạng:** Department không có parentDepartment, pháp nhân, chi nhánh hoặc cost center; timeline guard cấm phân công nhân viên chồng nhau. Mô hình này phù hợp một đơn vị nhỏ, chưa đủ cơ cấu nhiều cấp/kiêm nhiệm.

**Tác động:** Khó dựng sơ đồ tổ chức nhiều tầng, tuyến quản lý, tổng hợp chi phí và quyền theo cây.

**Cần sửa:** Bổ sung pháp nhân/đơn vị/địa điểm/cost center và tuyến báo cáo; nếu hỗ trợ kiêm nhiệm, tách phân công chính/phụ với chính sách rõ. Không gỡ constraint chồng lịch một cách tùy tiện.

**Bằng chứng:** [Department.java:15](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/model/Department.java:15); [02_BUSINESS_RULES.sql:199](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/database/consolidated/02_BUSINESS_RULES.sql:199)

### L22 — P2: Hợp đồng là sổ ghi nhận đã ký, chưa có vòng soạn–duyệt–ký

**Mức chắc chắn:** Xác định từ code và API.

**Hiện trạng:** createRecord luôn đặt DA_KY. Có gia hạn, chấm dứt và kiểm tra thời hạn, nhưng chưa thấy draft, người ký, mẫu tài liệu, bản scan/version tài liệu, chữ ký điện tử hoặc ghi nhận đồng ý.

**Tác động:** Nếu giới thiệu là quản lý toàn bộ vòng đời hợp đồng thì chức năng chưa đủ; nếu chỉ lưu hợp đồng đã ký bên ngoài thì hành vi hiện tại có thể chấp nhận.

**Cần sửa:** Ghi rõ phạm vi hoặc bổ sung trạng thái, tác nhân, tài liệu và chứng cứ ký; tách việc kết thúc hợp đồng với nghỉ việc nhân viên.

**Bằng chứng:** [ContractService.java:113](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/ContractService.java:113); [ContractController.java:13](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/controller/ContractController.java:13)

### L23 — P2: Quy tắc thử việc chặn theo người, thiếu theo công việc

**Mức chắc chắn:** Xác định từ code + đối chiếu yêu cầu nghiệp vụ.

**Hiện trạng:** ContractTermPolicy từ chối nếu lịch sử nhân viên từng có bất kỳ hợp đồng thử việc nào. Không gắn lần thử việc với một công việc/vị trí cụ thể. Đồng thời ngưỡng chuỗi hợp đồng xác định thời hạn được reset theo khoảng gián đoạn cấu hình; không thể coi thuật toán này là chứng nhận pháp lý mọi trường hợp.

**Tác động:** Có thể chặn trường hợp thử việc cho công việc khác cần được HR thẩm định; các ngoại lệ hợp đồng cần phân loại riêng.

**Cần sửa:** Gắn thử việc với công việc và căn cứ; rà soát thời hạn, số lần theo Điều 25 và hợp đồng xác định thời hạn theo Điều 20, kể cả ngoại lệ. Giữ cấu hình doanh nghiệp nằm trong giới hạn hợp pháp.

**Bằng chứng:** [ContractTermPolicy.java:51](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/ContractTermPolicy.java:51); [Bộ luật Lao động](https://chinhphu.vn/?docid=198540&pageid=27160)

### L24 — P1: Nghỉ việc mới hoàn tất trạng thái nhân sự, chưa có thanh toán/bàn giao đầy đủ

**Mức chắc chắn:** Xác định chức năng hiện có; thiếu phần nghiệp vụ thị trường.

**Hiện trạng:** Đã có readiness cho hợp đồng, phân công, lịch, công, phép và kỳ lương; thực thi sau ngày làm cuối, vô hiệu tài khoản và tăng token_version. Chưa thấy checklist tài sản, công nợ/tạm ứng, xác nhận các bên, lương cuối, phép chưa dùng và hồ sơ bảo hiểm. Bộ lịch chạy nghỉ việc mặc định false nếu không bật cấu hình.

**Tác động:** Có thể đóng hồ sơ nhưng chưa hoàn tất quyền lợi và nghĩa vụ tài chính. Nếu không vận hành tác vụ thủ công/định kỳ thì tài khoản chưa bị thu hồi đúng hạn.

**Cần sửa:** Bổ sung hồ sơ nghỉ việc có checklist và quyết toán; hiển thị tác vụ đến hạn, trạng thái lỗi/nhắc xử lý. Xác minh cấu hình triển khai, không mặc định scheduler luôn tắt ở mọi môi trường.

**Bằng chứng:** [EmploymentExitGuard.java:19](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/EmploymentExitGuard.java:19); [EmployeeRequestService.java:294](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/EmployeeRequestService.java:294); [ResignationExecutionService.java:15](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/ResignationExecutionService.java:15)

### L25 — P2: Quỹ phép có cấp theo năm, chưa thấy sổ chuyển/hết hạn/thanh toán phép

**Mức chắc chắn:** Xác định API hiện có; phần thiếu cần thiết kế.

**Hiện trạng:** Đã có cấp thủ công, cấp theo chính sách năm, thâm niên và trừ/hoàn theo đơn. Chưa thấy carryover có hạn, hết hạn phần chuyển, quy tắc tích lũy từng tháng và thanh toán phần phép hợp lệ lúc nghỉ việc. calculate dùng tỷ lệ ngày lịch cho cả năm; đây là chính sách được viết rõ, chưa đủ để khẳng định phù hợp mọi loại nghỉ.

**Tác động:** Không xử lý trọn chuyển năm và quyết toán nghỉ việc. Nhân viên tái tuyển giữ ngày vào làm gốc cần thêm kỳ quan hệ lao động để tránh tính cả khoảng không làm việc.

**Cần sửa:** Dùng sổ phát sinh phép theo nguồn và hạn sử dụng, đối chiếu loại nghỉ/nhóm nhân viên; bổ sung kỳ làm việc khi tái tuyển. Thử nhân viên vào/ra rồi quay lại trong cùng năm.

**Bằng chứng:** [LeaveAccrualService.java:38](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/LeaveAccrualService.java:38); [EmployeeRehireService.java:60](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/EmployeeRehireService.java:60); [LeaveEntitlementController.java:15](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/controller/LeaveEntitlementController.java:15)

### L26 — P1: Một ADMIN vừa quản trị hệ thống vừa là điểm duy nhất tính/chốt lương

**Mức chắc chắn:** Xác định từ code; hạn chế thiết kế kiểm soát nội bộ.

**Hiện trạng:** AdminAccessService cấm cấp ADMIN thứ hai; calculate/lock/publish/adjust bắt buộc ADMIN. Có quyền PAYROLL_MANAGE nhưng vẫn không thể giao tác vụ toàn kỳ cho vai trò nghiệp vụ khác. Luồng bảng lương không có bước duyệt bởi một người khác người lập.

**Tác động:** Phụ thuộc một tài khoản, khó thay thế khi vắng; quyền kỹ thuật trộn quyền duyệt tiền và thiếu tách trách nhiệm.

**Cần sửa:** Tách vai trò quản trị, C&B/lập lương, duyệt lương và chi trả; có người thay được kiểm soát. Chốt chính sách quyền theo doanh nghiệp, không coi ADMIN là người duyệt nghiệp vụ mặc định.

**Bằng chứng:** [AdminAccessService.java:102](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/AdminAccessService.java:102); [PayrollBatchController.java:84](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/controller/PayrollBatchController.java:84)

### L27 — P2: Màn hình phân quyền mới xem vai trò/sửa mô tả quyền

**Mức chắc chắn:** Xác định từ API và UI.

**Hiện trạng:** Role controller chỉ GET. Permission PUT/PATCH cập nhật tên và mô tả, không có API chỉnh ma trận role_permissions. Có đổi vai trò tài khoản và phạm vi phòng HR. Đây là phân quyền cố định có quản lý tài khoản, chưa phải bộ dựng quyền tùy biến.

**Tác động:** Tên “Tài khoản và phân quyền” dễ làm người dùng kỳ vọng tạo vai trò C&B, người duyệt, kế toán và gán quyền chi tiết ngay trên UI.

**Cần sửa:** Mô tả đúng giới hạn hoặc thêm ma trận quyền được kiểm soát, vai trò tùy chỉnh, audit thay đổi và bảo vệ quyền quản trị tối thiểu.

**Bằng chứng:** [AdminRoleController.java:10](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/controller/AdminRoleController.java:10); [AdminAccessService.java:253](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/AdminAccessService.java:253)

### L28 — P1: ZIP chứa cấu hình local có trường mật khẩu DB không rỗng

**Mức chắc chắn:** Xác định sự hiện diện; không xác nhận mật khẩu còn dùng.

**Hiện trạng:** HRM_System.zip có src/main/resources/application-local.properties, chứa URL, username và giá trị password datasource không rỗng. Tôi không đưa các giá trị vào báo cáo. Quy tắc loại file khỏi JAR không ngăn file bị đưa vào ZIP nguồn.

**Tác động:** Lộ cấu hình truy cập nếu đó là thông tin thật hoặc dùng lại ở môi trường khác.

**Cần sửa:** Loại file local khỏi gói chia sẻ, cung cấp .example; nếu thông tin là thật và ZIP đã chia sẻ ngoài phạm vi tin cậy thì thay thông tin truy cập. Kiểm tra cả lịch sử và cách đóng gói, không chỉ .gitignore.

**Bằng chứng:** Kiểm tra metadata và trạng thái không rỗng của file trong ZIP; không liên kết trực tiếp tới nội dung bí mật.

### L29 — P2: Lỗi nghiệp vụ lương bị trả thành lỗi máy chủ 500

**Mức chắc chắn:** Xác định từ code.

**Hiện trạng:** Các service kỳ lương/batch dùng RuntimeException cho bản nháp trùng, sai trạng thái, không có kỳ. GlobalExceptionHandler ánh xạ RuntimeException sang 500 chung.

**Tác động:** Người dùng không biết cần sửa gì; frontend không thể phân biệt 404/409 với lỗi hệ thống để phục hồi đúng.

**Cần sửa:** Dùng BusinessException/ResponseStatusException với 400/404/409 tương ứng và mã lỗi ổn định; kiểm tra cả chuỗi responseType text của tính lương.

**Bằng chứng:** [PayrollBatchService.java:62](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayrollBatchService.java:62); [GlobalExceptionHandler.java:243](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/exception/GlobalExceptionHandler.java:243)

### L30 — P2: UI thiếu quản lý kỳ lương/thành phần lương dù backend có API

**Mức chắc chắn:** Xác định qua routes và service frontend.

**Hiện trạng:** PayrollPeriodController có tạo/sửa kỳ; SalaryComponentController có CRUD. Trang lương chỉ chọn kỳ có sẵn và PayrollService chỉ gọi getPeriods, không có tạo/sửa kỳ hoặc quản lý salary components.

**Tác động:** Khi sang tháng mới hoặc thay danh mục, người vận hành phải gọi API/can thiệp ngoài UI. Phải phân biệt đây là thiếu màn hình, không phải thiếu API.

**Cần sửa:** Thêm cấu hình kỳ và thành phần lương cho đúng quyền; kiểm tra đầy đủ cấu hình trước khi mở kỳ tính.

**Bằng chứng:** [PayrollPeriodController.java:37](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/controller/PayrollPeriodController.java:37); [SalaryComponentController.java:14](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/controller/SalaryComponentController.java:14); [payroll.service.ts:19](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/services/payroll.service.ts:19)

### L31 — P2: Bố trí thiếu trang hồ sơ 360 và hộp việc cần làm tập trung

**Mức chắc chắn:** Nhận xét thiết kế dựa trên template/routes.

**Hiện trạng:** Hồ sơ, phân công, chứng chỉ, hợp đồng, phụ cấp và công nằm ở nhiều trang riêng. Dashboard ưu tiên biểu đồ hồ sơ/hợp đồng và có khối “Các chỉ số nên bổ sung”, chưa có hàng đợi việc đến hạn chung. Đơn phép/nghỉ việc, đổi lịch, bổ sung công và OT ở nhiều inbox.

**Tác động:** HR phải nhớ vị trí của từng loại tác vụ; mất ngữ cảnh khi xử lý một nhân viên từ đầu đến cuối.

**Cần sửa:** Thêm hồ sơ nhân viên theo tab và deep link có employeeId; một hộp việc chờ duyệt/đến hạn có loại đơn, hạn xử lý, người phụ trách. Giữ các trang danh sách chuyên sâu cho xử lý hàng loạt.

**Bằng chứng:** [navigation.ts:18](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/navigation.ts:18); [overview.html:102](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/overview/overview.html:102)

### L32 — P2: Một số trang chưa dùng cùng chuẩn tương tác và khả năng truy cập

**Mức chắc chắn:** Nhận xét từ code; chưa xác minh bố cục bằng trình duyệt.

**Hiện trạng:** RecordWorkspace chung đã có focus trap, Escape và bố cục co giãn. Trang payroll lại dùng drawer và modal tự viết; nút đóng không có nhãn dễ truy cập, không thấy focus trap/khôi phục focus tương đương trong component. Bảng lương có nhiều nút ngang trước khi chỉ ra điều kiện tính.

**Tác động:** Trải nghiệm bàn phím và sự nhất quán giữa phân hệ kém; bố cục thực tế ở từng độ rộng cần kiểm tra render mới kết luận tràn/cắt.

**Cần sửa:** Dùng lại component drawer/dialog chuẩn; nhãn nút, focus, Escape và trạng thái busy nhất quán. Kiểm tra màn hình 1440/1024/768/390px với tên dài và dữ liệu thật.

**Bằng chứng:** [record-workspace.ts:79](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/shared/record-workspace.ts:79); [payroll.html:143](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/payroll/payroll.html:143)

### L33 — P2: Tài liệu hợp đồng/phiếu lương chưa có quy trình xuất và chứng cứ đầy đủ

**Mức chắc chắn:** Xác định qua API/template.

**Hiện trạng:** API phiếu lương trả HTML, người dùng tự in/lưu PDF. Chưa thấy tệp PDF do server tạo có phiên bản/hash, xác nhận đã nhận, phản hồi tranh chấp phiếu và quy trình điều chỉnh liên quan. HTML đang ghép chuỗi tên khoản/diễn giải, cần escape dữ liệu do người dùng nhập.

**Tác động:** Khó chứng minh bản nào đã phát hành/nhận; có rủi ro HTML injection khi dữ liệu khoản mục không đáng tin được đưa vào bản in. Chưa tái hiện khai thác.

**Cần sửa:** Escape HTML, bổ sung tệp lưu trữ và quyền tải, xác nhận/ý kiến phiếu. Nếu chỉ hỗ trợ in từ browser thì đặt nhãn đúng khả năng.

**Bằng chứng:** [PayslipService.java:124](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/main/java/com/hrm/service/PayslipService.java:124); [payroll.ts:220](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/frontend/src/app/pages/payroll/payroll.ts:220)

### L34 — P2: Kiểm thử và vận hành chưa đủ chứng minh sẵn sàng triển khai

**Mức chắc chắn:** Xác định từ repo và kết quả kiểm tra phiên này.

**Hiện trạng:** Có nhiều test về quyền và workflow, không phải dự án không có test. Tuy nhiên file PayrollCalculationEngineTest.txt không chạy như JUnit; test miễn thuế ăn trưa đang kiểm theo đặc tả đồ án. Không thấy OpenAPI/springdoc hoặc công cụ migration tự chạy trong pom. SQL phụ thuộc migration thủ công và nhiều bảng/trigger.

**Tác động:** Test xanh theo chính sách mẫu vẫn có thể sai nghiệp vụ bên ngoài; dễ thiếu migration giữa các máy. Chưa có kết quả chứng minh end-to-end công thật → lương thật → thanh toán.

**Cần sửa:** Chuyển các ca cần chạy thành test thực, bộ dữ liệu kỳ chuẩn được người làm C&B duyệt, kiểm thử quyền/biên kỳ/đồng thời và kiểm tra migration khi startup. Theo dõi job lỗi và có phục hồi được thử nghiệm.

**Bằng chứng:** [PayrollCalculationEngineTest.txt:21](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/test/java/com/hrm/service/engine/PayrollCalculationEngineTest.txt:21); [PayrollMealExemptionTest.java:38](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/src/test/java/com/hrm/service/engine/PayrollMealExemptionTest.java:38); [pom.xml:27](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/HRM_System/pom.xml:27)

## 4. Ma trận rà từng chức năng theo danh mục

Tên API dưới đây là backend mapping. Với nhiều API legacy, frontend thêm `/api` rồi proxy bỏ tiền tố; các nhóm `/api/management`, `/api/auth`, `/api/me`… được giữ nguyên. Phải triển khai reverse proxy tương ứng ở production. Không kết luận 404 chỉ từ việc một controller không có tiền tố `/api`.

### A. Cơ cấu tổ chức và danh mục

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| A01 | Danh mục phòng ban | Có | GET/POST/PUT/DELETE /departments | Có danh sách và ngừng dùng; chưa phải cơ cấu doanh nghiệp nhiều cấp. L21. |
| A02 | Quản lý trưởng phòng/nhiệm kỳ | Có | /departments/{id}/head; /history; /candidates; /end | Có bổ nhiệm/thay/kết thúc và lịch sử. Bắt người ngoài phòng phải điều chuyển trước; thiếu bổ nhiệm liên phòng trong một giao dịch. |
| A03 | Pháp nhân, chi nhánh, địa điểm | Thiếu | Chưa thấy controller/mô hình tương ứng | Bổ sung nếu quản lý nhiều đơn vị hoặc lịch/thuế/chi phí khác nhau. |
| A04 | Cây phòng ban, sơ đồ tổ chức, cấp báo cáo | Thiếu | Department chưa có parent | Thiếu cây và tuyến duyệt theo quản lý trực tiếp; L21. |
| A05 | Chức vụ và vị trí việc làm | Một phần | /positions; /positions/search | Có chức vụ; chưa thấy vị trí tuyển dụng, định biên và headcount theo vị trí. |
| A06 | Danh mục ca, loại phép, trình độ, loại hợp đồng | Có | /management/shifts; /leave-types; /qualification-levels; /contract-types | Đã có CRUD/chính sách ở mức khác nhau. Cần chuẩn hóa quyền và ngày hiệu lực. |
| A07 | Trung tâm chi phí, phân bổ nhân sự | Thiếu | Chưa thấy API cost center | Cần cho kế toán quản trị, nhiều dự án/đơn vị. |

### B. Hồ sơ nhân viên

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| B01 | Tạo/tìm/lọc/phân trang nhân viên | Có | /employees; /employees/search | Có mã/họ tên, phòng, chức vụ, trạng thái; không đồng nghĩa đủ mọi trường hồ sơ HR. |
| B02 | Sửa hồ sơ và chống ghi đè | Có | PUT /employees/{id}; /detail; PATCH /contact | Có version và lý do sửa; đây là kiểm soát nên giữ. |
| B03 | Đọc dữ liệu riêng theo quyền | Có kiểm soát trong code | /employees/{id}/detail và service quyền | Có phân biệt CCCD/địa chỉ; cần test ma trận vai trò và export thực tế. |
| B04 | Bằng cấp/chứng chỉ/hết hạn | Có | /employee-qualifications; /employees/{id}/qualifications | Có cấp, hết hạn, trình độ chính và tìm kiếm; chưa thấy kho tệp chứng chỉ. |
| B05 | Người phụ thuộc và mã số thuế | Thiếu | Chưa thấy API hồ sơ thuế/người phụ thuộc | L02; ảnh hưởng trực tiếp số thuế. |
| B06 | Tài khoản ngân hàng và thông tin nhận lương | Thiếu | Chưa thấy API bank account | Không đủ dữ liệu lập danh sách chi trả ngân hàng. |
| B07 | Liên hệ khẩn cấp, thân nhân, hồ sơ pháp lý mở rộng | Thiếu/giới hạn | Chưa thấy quy trình/bộ API riêng | Hồ sơ hiện chủ yếu thông tin định danh/liên hệ; cần theo nhu cầu triển khai. |
| B08 | Kho tài liệu/ảnh và quyền tải | Thiếu | Chưa thấy upload/download hồ sơ | Tham chiếu chứng từ là chuỗi không thay thế lưu tệp có quyền và thời hạn lưu. |
| B09 | Nhập nhân viên hàng loạt, báo lỗi từng dòng | Thiếu | Không thấy endpoint import nhân viên | Cần mẫu Excel, dry-run, trùng mã/CCCD/email và rollback theo quy tắc. |
| B10 | Hồ sơ 360 liên kết các phân hệ | Một phần | Các API từng nghiệp vụ có | Thiếu trang theo nhân viên thống nhất; L31. |
| B11 | Trạng thái trước nhận việc/thử việc/tạm hoãn | Một phần | EmploymentStatus chủ yếu DANG_LAM/DA_NGHI | Hợp đồng có thử việc nhưng hồ sơ chưa mô hình hóa đầy đủ vòng đời. |
| B12 | Tái tuyển và các đợt làm việc | Một phần | POST /employees/{id}/rehire | Có tái tuyển, giữ mã/lịch sử; thiếu employment episode cho tính thâm niên/đối soát khoảng nghỉ. L25. |

### C. Phân công, hợp đồng và quan hệ lao động

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| C01 | Phân công hiện tại/lịch sử/điều chuyển | Có | /employee-assignments; /employees/{id}/assignments; PUT /transfer | Có chống chồng hiệu lực, scope; chưa có điều chuyển phức tạp nhiều đơn vị. |
| C02 | Kiêm nhiệm, nhiều quan hệ làm việc | Thiếu theo mô hình hiện tại | Timeline guard chỉ một phân công giao nhau | Chỉ cần thêm nếu phạm vi khách hàng có nhu cầu. |
| C03 | Lập và lưu hợp đồng đã ký | Có | POST/GET /contracts | Tạo trực tiếp DA_KY; sổ lưu hợp đồng ngoài hệ thống. L22. |
| C04 | Soạn thảo, duyệt nội dung, ký điện tử | Thiếu | Chưa thấy API draft/approval/signature | Không thể gọi là toàn bộ vòng đời ký hợp đồng. |
| C05 | Gia hạn và chấm dứt hợp đồng | Có | POST /contracts/{id}/renew; /terminate | Có liên kết và audit; cần kiểm chứng toàn luồng với nghỉ việc. |
| C06 | Mức lương hiệu lực theo hợp đồng | Có ở nguồn | /contracts/{id}/salary-terms; /change-rate; /employees/{id}/salary-rates | Đã có phân đoạn; chưa nối đúng vào bảng lương L05. |
| C07 | Cảnh báo sắp hết hạn/thiếu hợp đồng | Có | /contracts/expiring; /employees/without-active-contract | Có cảnh báo trên trang; chưa thấy hệ thống nhắc việc đa kênh tập trung. |
| C08 | Thử việc, thời hạn và số lần hợp đồng | Một phần | ContractTermPolicy | Có giới hạn nhưng gắn thử việc theo người, ngoại lệ chưa đủ; L23. |
| C09 | Phụ lục tài liệu, lưu bản ký và người ký | Thiếu/giới hạn | Salary terms không phải kho phụ lục pháp lý | Cần document/version và chứng cứ ký nếu thuộc phạm vi. |
| C10 | Khen thưởng có phê duyệt | Có ở nguồn | /employee-decisions; /approve; /reject | Có cấm người lập/tự hưởng tự duyệt; chưa vào engine thật. |
| C11 | Kỷ luật và bồi thường | Một phần | DecisionRules và /employee-decisions | Đã cấm kỷ luật trừ lương; thiếu bộ hồ sơ xử lý/biên bản, kiểm tra trần khấu trừ bồi thường theo căn cứ. Không gán nhầm là hệ thống cho phạt lương. |

### D. Ca, lịch làm việc và chấm công

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| D01 | Tạo/sửa/ngừng dùng ca và khoảng nghỉ | Có | /api/management/shifts và /work-time-details | Có chi tiết khoảng nghỉ và lịch sử; nhiều facade cần tài liệu hợp đồng API. |
| D02 | Phân lịch theo người/phòng | Có | /api/management/schedule-workspace; /schedules | Có calendar/search, lịch sử, kiểm tra chồng. |
| D03 | Phân ca hàng loạt/lặp/xoay tự động | Chưa thấy đủ | Các API ghi hiện theo từng lịch | Cần bulk preview xung đột, pattern, publish lịch cho vận hành quy mô lớn. |
| D04 | Ca qua đêm và phân giờ nghỉ theo ngày | Có logic | AttendanceDayAllocation; DailyWorkLimitPolicy | Là phần triển khai tương đối kỹ; cần tích hợp đúng kỳ payroll. |
| D05 | Check-in/check-out tự phục vụ | Có backend | /api/attendance/self/{scheduleId}/check-in; /check-out | Lấy giờ server và kiểm tra sở hữu; web EMPLOYEE bị chặn L18. |
| D06 | Nhập công thủ công, vắng, sửa, xác nhận/mở xác nhận | Có | /api/attendance-workspace; /api/attendance | Có workflow và guard, chưa thể coi đã tích hợp payroll thật. |
| D07 | Bổ sung công, tiếp nhận và duyệt | Có | /api/attendance-workspace/requests | Có deadline dựa lịch cấu hình; cần vận hành đầy đủ calendar. |
| D08 | Bất thường và thiếu checkout | Có | /api/attendance/anomalies và scan job | Có policy/job, cần biết job có bật và lỗi ra sao khi triển khai. |
| D09 | Máy chấm công, import log, đồng bộ/chống trùng | Thiếu | Không thấy API connector/import thiết bị | Có nhập tay không đồng nghĩa kết nối máy/vân tay/khuôn mặt. |
| D10 | GPS/Wi-Fi/IP/thiết bị, xác minh nơi chấm công | Chưa thấy | Self clock nhận schedule và giờ server | Không phải mọi doanh nghiệp bắt buộc GPS; cần nếu cam kết xác minh hiện diện tại địa điểm. |
| D11 | Lịch lễ, nghỉ tuần, nghỉ bù dùng chung | Một phần | Calendar hiện phục vụ deadline công | Chưa thấy nền lịch dùng chung tính lương lễ/OT; L03/L19. |
| D12 | Tổng hợp công theo kỳ | Có backend/UI | /api/management/attendance/confirmed-summary; /api/me/attendance-summary | Đếm bản ghi/giờ có chú thích; cần quy đổi ngày công theo ca/chính sách và nối engine. |
| D13 | Khóa kỳ công và phê duyệt bảng công tập thể | Một phần | Có xác nhận từng công và bảo vệ kỳ lương đã chốt | Chưa thấy hồ sơ kỳ công được trình duyệt riêng, đối chiếu đầy đủ trước payroll. |
| D14 | Cấu hình cửa sổ chấm công theo đơn vị | Giới hạn | AttendanceClockPolicy hardcode | L20; không mặc định một khung cho mọi chi nhánh/loại ca. |

### E. Nghỉ phép và phê duyệt

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| E01 | Danh mục loại nghỉ và điều kiện | Có | /leave-types; /leave-rules; /leave-policies | Có hạn mức/đối tượng/tham chiếu minh chứng; không có tệp đính kèm thực. |
| E02 | Cấp phép thủ công | Có | POST/PUT /leave-entitlements | Có kỳ và số giờ; cần kiểm soát lý do/audit và đối chiếu used/remaining. |
| E03 | Cấp phép năm theo chính sách/thâm niên | Có | /employee-requests/leave-accrual/{type}/generate | Có UI trong danh mục loại phép; không được kết luận là thiếu. L25 nêu giới hạn. |
| E04 | Đơn phép, duyệt, từ chối, rút/hủy đã duyệt | Có | /employee-requests/leave; /approve; /reject; /withdraw; /cancel-approved | Có xử lý quỹ; kịch bản phép đã phát sinh công cần test tích hợp. |
| E05 | Nghỉ một phần ca/qua ngày | Có logic | LeaveRequestRules.netMillis và allocations | Dùng giờ nghỉ chi tiết để tránh đoán; cần dữ liệu ca hoàn chỉnh. |
| E06 | Duyệt trưởng phòng rồi HR | Có | /head-queue; /head-decision; RequestApprovalStages | Có skip khi không có trưởng phòng; chưa là bộ thiết kế quy trình linh hoạt. |
| E07 | Ủy quyền, thay người duyệt, SLA/escalation | Thiếu | Chưa thấy API workflow policy/delegation | L20. |
| E08 | Chuyển phép, hết hạn phần phép, mua/bán phép | Thiếu | Chưa thấy API ledger carryover/expiry | L25; phải áp đúng chính sách và căn cứ cho từng trường hợp. |
| E09 | Nghỉ ốm/thai sản và giải quyết chế độ | Một phần danh mục | Loại nghỉ có thể cấu hình | Mã loại nghỉ không thay thế hồ sơ chế độ/bảo hiểm và tính quyền lợi. |
| E10 | Hộp đơn thống nhất và thông báo người duyệt | Một phần | Nhiều inbox từng nghiệp vụ | Có feedback/toast; chưa thấy kho thông báo nhiệm vụ lưu server tập trung. |

### F. Tăng ca

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| F01 | Đăng ký, duyệt/từ chối, hủy | Có | /api/overtime/requests; /workspace/requests | Có check quyền và workflow; không phải chỉ CRUD. |
| F02 | Ghi giờ OT thực tế, khoảng nghỉ, xác nhận | Có | /actual; /confirm; /reopen-confirmation; calendar | Phân biệt giờ yêu cầu và giờ thực tế là đúng hướng. |
| F03 | Bổ sung công OT và xử lý overflow | Có | /workspace/supplements; /overflow-request; /inbox | Có quy trình; cần sổ hạn mức tháng/năm và chính sách trả tiền. |
| F04 | Chống trùng công/OT và phân bổ qua đêm | Có logic | OvertimeDailyLedger; OvertimeRecognizedMinutes | Cần giữ và test biên kỳ L07. |
| F05 | Giới hạn ngày | Có nhưng chưa đủ tuân thủ | DailyWorkLimitPolicy | Giới hạn 12 giờ net không thay mọi quy tắc theo chế độ làm việc/đối tượng. |
| F06 | Giới hạn tháng/năm, trường hợp đặc thù | Thiếu/chưa tìm thấy | Chưa thấy policy hoặc API riêng | L19; cần nghiệp vụ tích lũy và hồ sơ ngoại lệ. |
| F07 | OT ngày nghỉ/lễ/ban đêm thành tiền | Sai/thiếu ở engine | Engine chỉ ×1,5 | L03. |
| F08 | Xuất nguồn OT cho bảng lương | Có API, chưa tích hợp đúng engine | GET /api/overtime/payroll-inputs | L01/L07; API input không chứng minh bảng lương đã dùng. |

### G. Lương, phụ cấp, thuế, bảo hiểm và chi trả

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| G01 | Tạo/sửa kỳ lương | Có API, thiếu UI | POST/PUT /payroll-periods | L30. DB có trigger bảo vệ kỳ đã chốt; không kết luận tùy ý sửa kỳ đã chốt. |
| G02 | Danh mục thành phần lương | Có API, thiếu UI | CRUD /salary-components | L12/L30; chưa thấy công cụ dựng công thức/mẫu bảng lương theo doanh nghiệp. |
| G03 | Tạo đợt, tính, chốt, công bố | Có khung, tính sai nguồn | /payroll-batches/draft; /calculate; /lock; /publish | L01 là lỗi chặn vận hành; luồng trạng thái không thay thế đúng số tiền. |
| G04 | Lương tháng/ngày/giờ và đổi lương giữa kỳ | Thiếu trong engine | SalaryRateService có nguồn | L05. |
| G05 | Công chuẩn theo đơn vị/kỳ và quy đổi công | Thiếu nguồn thực | Mock 22 ngày, engine fallback 22 | Cần lịch thực tế/chính sách trả lương rõ. Không có chuẩn thị trường bắt buộc mọi nơi đều 22 hoặc 26 ngày. |
| G06 | Danh mục/gán/thay mức/kết thúc phụ cấp | Có ở phân hệ | /allowance-types; /employees/{id}/allowances; /change-from; /end-from | Có isTaxable/countsForSocialInsurance và xem trước; chưa được engine tận dụng. |
| G07 | Phụ cấp theo kỳ/công/giờ/tỷ lệ | Có tính nguồn, thiếu tích hợp | AllowanceCalculationService | L06; chính sách UNCONFIRMED có thể chặn có chủ đích, cần màn hình vận hành cấu hình. |
| G08 | Thưởng/bồi thường được duyệt vào lương | Thiếu tích hợp | /employees/{id}/decision-payroll-items | L06; không thay bằng tự trừ lương cho kỷ luật. |
| G09 | Thuế TNCN theo kỳ, người phụ thuộc/cư trú | Sai/thiếu | PayrollConstants/engine | L02. |
| G10 | Hồ sơ và tính BHXH/BHYT/BHTN | Sai/thiếu | Engine trừ một tỷ lệ tổng | L04; chưa có nghiệp vụ hồ sơ bảo hiểm độc lập. |
| G11 | Tạm ứng, hoàn ứng, khoản vay/công nợ lương | Thiếu | Chưa thấy API | Cần nguồn, hạn mức, lịch thu hồi và đối chiếu. |
| G12 | Lương KPI/doanh số/sản phẩm, thưởng kỳ | Thiếu | Chưa thấy bộ input/công thức tương ứng | Là mở rộng theo mô hình doanh nghiệp, không bắt buộc mọi khách hàng. |
| G13 | Duyệt bảng lương tách người lập/duyệt | Thiếu | NHAP → DA_CHOT trực tiếp | L26. |
| G14 | Điều chỉnh kỳ cũ và đối chiếu chênh lệch | Một phần, có điểm bế tắc | POST /payroll-batches/{id}/adjust | L08/L09. |
| G15 | Snapshot/tái lập kết quả | Một phần | payroll_snapshots | Có snapshot và version batch nhưng thiếu nguồn/escape/đối chiếu phiên bản L10/L11. |
| G16 | Phiếu lương cá nhân và bản in | Có API, có lỗi chọn phiếu | /payslips; /api/me/payslips | L13/L14/L17/L18. |
| G17 | Xác nhận/ý kiến/tranh chấp phiếu lương | Thiếu | Chưa thấy API acknowledge/dispute | L33. |
| G18 | Chi trả ngân hàng, trạng thái trả và đối soát | Thiếu | Không thấy API payment/bank/reconciliation | “Thực nhận” là kết quả tính; chưa chứng minh tiền đã chuyển. |
| G19 | Hạch toán, phân bổ lương/kết nối kế toán | Thiếu | Không thấy API accounting/cost allocation | Cần mapping tài khoản và chống hạch toán trùng. |
| G20 | Khai/quyết toán thuế và hồ sơ bảo hiểm điện tử | Thiếu | Không thấy API filing/settlement | Có phép tính thuế trong lương không tương đương phân hệ kê khai. |

### H. Nghỉ việc, tài sản và tiếp nhận

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| H01 | Xin nghỉ việc, cảnh báo báo trước, duyệt ngày cuối | Có | /employee-requests/resignation; ResignationNoticeAdvice | Cảnh báo advisory là hợp lý vì có ngoại lệ, không nên tự chặn mọi đơn thiếu ngày báo trước. |
| H02 | Kiểm tra vướng mắc trước nghỉ | Có | /employee-requests/{id}/resignation-readiness | Kiểm tra nhiều phân hệ, có đường dẫn xử lý; L24 nêu phần còn thiếu. |
| H03 | Thực thi nghỉ việc và thu hồi token | Có | /resignations/execute-due | Sau ngày làm cuối, giữ lịch sử, tăng token version; xác minh scheduler. |
| H04 | Bàn giao công việc/tài sản, xác nhận nhiều bên | Thiếu | Readiness hiện không có tài sản/checklist các bên | Cần task người nhận, hạn và chứng từ. |
| H05 | Quyết toán nghỉ việc, phép còn, nợ/tạm ứng | Thiếu | Không thấy API final settlement | L24, phụ thuộc hoàn thiện engine và payment. |
| H06 | Báo giảm/chốt hồ sơ bảo hiểm, giấy tờ nghỉ | Thiếu | Không thấy bộ hồ sơ bảo hiểm liên quan | Không đồng nghĩa chỉ cần đổi trạng thái DA_NGHI. |
| H07 | Tiếp nhận nhân viên mới theo checklist | Thiếu | Tạo employee trực tiếp không phải onboarding | Thiếu nhiệm vụ HR/IT/quản lý, thiết bị, tài liệu, lịch thử việc. |
| H08 | Tài sản cấp phát và công nợ tài sản | Thiếu | Không thấy API assets | Có thể tích hợp hệ thống khác nếu không xây trong HRM. |

### I. Các phân hệ HRM đầy đủ còn thiếu

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| I01 | Định biên và kế hoạch nhân sự | Thiếu | Không thấy API workforce plan | Nhu cầu/vị trí/ngân sách, duyệt yêu cầu tuyển. |
| I02 | Tuyển dụng: requisition/JD/tin tuyển | Thiếu | Không thấy API recruitment/jobs | Phân hệ thị trường phổ biến, có thể triển khai hoặc tích hợp ATS. |
| I03 | Ứng viên/CV/pipeline/phỏng vấn/đánh giá | Thiếu | Không thấy API candidates/interviews | Thiếu sổ ứng viên, quyền CV, lịch và đánh giá vòng. |
| I04 | Offer → tiếp nhận nhân viên | Thiếu | Không thấy API offers/conversion | Không có đường nối tuyển dụng sang hồ sơ. |
| I05 | Mục tiêu/KPI/OKR và kỳ đánh giá | Thiếu | Không thấy API goals/performance | Không coi biểu đồ nhân sự là đánh giá hiệu suất. |
| I06 | Đánh giá nhiều phía, hiệu chỉnh/kế hoạch phát triển | Thiếu | Không thấy API review/calibration | Theo nhu cầu doanh nghiệp, không bắt buộc bản core HR tối thiểu. |
| I07 | Đào tạo: khóa/lớp/ghi danh/kết quả | Thiếu | Không thấy API courses/training | Bằng cấp hiện có không thay thế quản lý đào tạo. |
| I08 | Khung năng lực, kỹ năng, kế nhiệm | Thiếu | Không thấy API competency/succession | Phân hệ nâng cao để so với suite HCM đầy đủ. |
| I09 | Phúc lợi ngoài phụ cấp tiền mặt | Thiếu | Không thấy API benefit enrollment/claim | Bảo hiểm sức khỏe, quyền lợi theo nhóm, lựa chọn/đề nghị phúc lợi. |
| I10 | Công tác/chi phí/hoàn phí và dịch vụ HR | Thiếu | Không thấy API business trip/expense/HR case | Có thể tích hợp phần mềm riêng; phải công bố ranh giới. |
| I11 | Khảo sát, thông báo nội bộ và chính sách | Thiếu/giới hạn | Có UI feedback/toast, chưa thấy kho nghiệp vụ | Toast thành công không phải thông báo việc cần duyệt có lịch sử đọc. |

### J. Báo cáo, phân quyền và tích hợp

| Mã | Chức năng | Đánh giá | API/bằng chứng hiện có | Nhận xét và phần còn thiếu |
|---|---|---|---|---|
| J01 | Báo cáo nhân sự/hợp đồng/công/lương | Có | GET /api/reports/employees / contracts / work / payroll | Đã có số liệu và xuất Excel; số lương phụ thuộc dữ liệu nguồn nên chưa đủ tin cậy. |
| J02 | Dashboard HR theo phạm vi | Có | GET /api/overview/hr | Có scope; khối insight đang thiên về thống kê hơn xử lý công việc. |
| J03 | Biến động/headcount/FTE/chi phí theo kỳ | Thiếu/giới hạn | Dashboard tự liệt kê các chỉ số cần bổ sung | Cần định nghĩa chỉ số, lịch sử theo thời gian và drill-down. |
| J04 | Tài khoản, đổi/reset/khôi phục mật khẩu | Có | /api/admin/accounts; /api/auth/* | Có BCrypt/token revocation và recovery; chưa kiểm thử xâm nhập hoặc gateway rate limiting. |
| J05 | Phân quyền vai trò và phòng HR | Có ở mức cố định | /api/admin/roles; /permissions; /hr-scopes | L26/L27; trưởng phòng được suy từ nhiệm kỳ, không cấp HR toàn cục. |
| J06 | Vai trò tùy chỉnh, ủy quyền, tách trách nhiệm | Thiếu/giới hạn | Chưa thấy role_permissions mutation/delegation | Cần cho người lập/duyệt lương, kế toán, lãnh đạo. |
| J07 | Audit thay đổi và bảo vệ lịch sử | Có | /personnel/audit; /g1/audit; /audit-logs | Đã có correlation/audit; cần chính sách lưu, quyền đọc và bảo vệ dữ liệu nhạy cảm dài hạn. |
| J08 | SSO/MFA và chống dò mật khẩu đăng nhập | Chưa thấy đầy đủ trong app | SecurityConfig/AuthService chưa có cơ chế rõ | Recovery có giới hạn lần thử; không khẳng định gateway bên ngoài không có bảo vệ. |
| J09 | OpenAPI/hợp đồng API, version và test tương thích | Thiếu/chưa thấy | 80 controller, nhiều facade/alias | L34; cần công bố method/path/DTO/quyền/lỗi/trạng thái theo cấu hình. |
| J10 | Migration có phiên bản, giám sát job, phục hồi | Một phần tài liệu/script | SQL thủ công, có scheduled jobs | Cần triển khai kiểm chứng, không dựa vào README là đã vận hành. |
| J11 | Webhook/event và kết nối ATS/kế toán/ngân hàng | Thiếu | Không thấy bộ API tích hợp nghiệp vụ tương ứng | Có REST nội bộ không đồng nghĩa có tích hợp bên ngoài ổn định. |
| J12 | Đóng gói cấu hình và dữ liệu nhạy cảm | Có lỗi cần xử lý | application-local.properties trong ZIP | L28. |

## 5. API có đầy đủ chưa?

**Chưa. Cần phân biệt bốn tình huống:**

| Tình huống | Ví dụ xác định được | Cách xử lý |
|---|---|---|
| Có UI + API nhưng nghiệp vụ chưa thật | Tính bảng lương gọi được API nhưng nguồn vẫn là mock | Sửa nguồn và phép tính, không chỉ thêm endpoint |
| Có API, thiếu UI | Tạo/sửa kỳ lương, quản lý thành phần lương | Bổ sung trang/capability đúng quyền |
| Có API cá nhân, web không cho nhân viên dùng | Công, lịch, hợp đồng và phiếu lương cá nhân | Chốt chiến lược web/mobile và kiểm thử cổng được cung cấp |
| Thiếu toàn bộ luồng/API tương ứng | ATS, đánh giá, đào tạo, ngân hàng/chi trả, hồ sơ thuế/bảo hiểm, quyết toán nghỉ việc | Xây module hoặc tích hợp có hợp đồng dữ liệu rõ |

Các nhóm nguồn cho lương đã tồn tại và nên tái sử dụng:

- Mức lương hiệu lực: `GET /employees/{employeeId}/salary-rates`.
- Phụ cấp theo kỳ: `GET /employees/{employeeId}/allowance-calculations`.
- Quyết định ảnh hưởng lương: `GET /employees/{employeeId}/decision-payroll-items`.
- Công xác nhận: `GET /api/management/attendance/confirmed-summary`.
- OT đủ điều kiện: `GET /api/overtime/payroll-inputs`.

Không nên nối backend với chính nó qua HTTP một cách máy móc: orchestration lương có thể gọi các service nguồn trực tiếp trong giao dịch/phạm vi dữ liệu phù hợp. Các API trên cho thấy nguồn đã có, còn luồng cuối vẫn phải được triển khai.

Các nhóm API đề xuất còn thiếu (tên minh họa, **không phải endpoint đang có**):

| Nhóm | Các tác vụ cần API |
|---|---|
| Hồ sơ thuế/ngân hàng/bảo hiểm | người phụ thuộc có hiệu lực; tình trạng cư trú; căn cứ đóng; tài khoản nhận lương; lịch sử thay đổi |
| Chu kỳ công/lương | kiểm tra readiness có cấu trúc; snapshot nguồn; tính thử; đối chiếu sai lệch; gửi duyệt; duyệt/trả lại; chốt; điều chỉnh kỳ cũ |
| Chi trả | tạo lệnh trả; export ngân hàng; ghi nhận đã trả/thất bại; nhập kết quả; đối soát; chống gửi trùng |
| Thuế/bảo hiểm | kê khai/điều chỉnh/quyết toán; báo tăng/giảm; chứng từ và trạng thái gửi |
| Tuyển dụng/onboarding | yêu cầu tuyển; vị trí; ứng viên; vòng phỏng vấn; offer; chuyển nhân viên; checklist tiếp nhận |
| Hiệu suất/đào tạo | mục tiêu; kỳ/phiếu đánh giá; người đánh giá; kết quả; khóa/lớp/ghi danh/kết quả học |
| Nghỉ việc/tài sản | checklist nhiều bên; giao/thu tài sản; công nợ; quyết toán cuối; hồ sơ đóng/bàn giao |
| Workflow/tích hợp | chính sách duyệt; ủy quyền; thông báo lưu server; webhook/event; import preview/commit |

Danh sách trích xuất từng method/path/file nằm tại [API_INVENTORY.md](C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107/API_INVENTORY.md). Tài liệu này là bản kiểm kê code, không thay OpenAPI và không phải kết quả gọi HTTP. Chưa khẳng định đã xác minh tất cả DTO, quyền, error và mọi tổ hợp cấu hình.

## 6. Bố trí giao diện nên điều chỉnh thế nào?

### Điểm nên giữ

- Hướng bố trí danh sách–chi tiết giúp HR không mất bảng đang tra cứu; RecordWorkspace có chuyển drawer khi hẹp, xử lý focus và bàn phím.
- Có bộ token màu/kích thước, trạng thái xử lý, pagination, thông báo và bảo vệ dữ liệu biểu mẫu chưa lưu.
- Phòng ban/trưởng phòng đã có giải thích nhiệm kỳ tương lai và lịch sử; quỹ phép và loại nghỉ có cấu hình riêng.

### Những thay đổi cần thiết

| Khu vực | Vấn đề | Bố trí đề xuất |
|---|---|---|
| Trang đầu | Nhiều biểu đồ, thiếu hành động đến hạn | Đưa đơn chờ duyệt, công bất thường, hợp đồng sắp hết và vướng mắc khóa lương lên trước; số liệu/biểu đồ ở dưới |
| Menu | Phòng ban và Danh mục phòng ban dễ bị hiểu là trùng; thực tế một trang quản lý trưởng phòng, một trang CRUD | Đặt rõ “Cơ cấu & trưởng phòng” và “Thiết lập phòng ban”, hoặc hợp vào một khu vực có tab |
| Hồ sơ nhân viên | Thông tin liên quan nằm rải nhiều module | Trang một người có tab Tổng quan / Công tác / Hợp đồng & Lương / Bằng cấp / Công–phép / Tài liệu / Lịch sử; tab nhạy cảm theo quyền |
| Đơn từ | Nghỉ phép/nghỉ việc, đổi lịch, bổ sung công và OT có inbox riêng | Hộp Việc cần xử lý chung, lọc loại/đơn vị/hạn và mở đúng màn chi tiết |
| Lương | Nhiều nút thao tác ngay lập tức, không biết thiếu điều kiện nào | Trình tự: chọn kỳ → kiểm tra nguồn → tính & đối soát → gửi/duyệt → chốt → công bố → chi trả; checklist hiển thị ngay trước tính/chốt |
| Bảng lương | Mã tự dựng, thiếu tên/phòng, khó tìm người | Mã thật + tên + phòng; tìm/lọc, các cột tổng nhất quán; chi tiết giải thích từng dòng và nguồn |
| Phiếu lương | In chưa gắn kỳ, nhãn PDF là browser print | Hiển thị kỳ/phiên bản/trạng thái; in đúng phiếu; thêm nhận/phản hồi nếu thuộc phạm vi |
| Thiết lập | Thiếu UI kỳ và thành phần lương; chính sách rải ở nhiều nơi | Một khu Thiết lập có đơn vị, lịch, loại nghỉ, lương/thuế/bảo hiểm, quy trình và quyền; nêu hiệu lực và người sửa |
| Vai trò | Web dùng chung shell nhưng EMPLOYEE bị chặn | Tách không gian cá nhân/quản lý/HR. Nếu chỉ cung cấp mobile cho nhân viên, thể hiện rõ và kiểm thử liên thông |
| Khả năng truy cập | Payroll tự viết modal/drawer khác chuẩn chung | Dùng chung dialog/drawer có focus trap, nhãn nút, Escape và bảo vệ busy; kiểm tra render trước kết luận đẹp/xấu |
| Nội dung sản phẩm | Khối “Các chỉ số nên bổ sung” là ghi chú phát triển trong dashboard người dùng | Chuyển thành backlog; UI người dùng chỉ hiển thị chỉ số đã có hoặc trạng thái thiếu dữ liệu có hành động cụ thể |

Đây là đề xuất cấu trúc tác nghiệp. Chưa chấm điểm thẩm mỹ hoặc khẳng định màn hình lỗi kích thước khi chưa có phiên render được kiểm chứng.

## 7. Thứ tự sửa để dùng được ngoài thực tế

| Ưu tiên | Việc cần hoàn tất | Điều kiện nghiệm thu |
|---|---|---|
| 1 | Loại mock khỏi runtime, nối nguồn công/lương/phụ cấp/quyết định thật; xử lý cấu hình local trong ZIP | Thay đổi một nguồn chỉ ảnh hưởng đúng người/kỳ; không xuất hiện nhân viên mẫu tự sinh; gói chia sẻ không có cấu hình truy cập thật |
| 2 | Chuẩn hóa kỳ, công thức lương, OT/đêm/lễ, thuế 2026, bảo hiểm | Bộ bảng tính chuẩn do C&B kiểm tra cho đủ trường hợp; khớp từng dòng và số làm tròn |
| 3 | Đóng chu kỳ: nguồn phiên bản, kiểm tra trước chốt, duyệt độc lập, sửa kỳ cũ, in đúng kỳ | Không chốt kết quả cũ; duyệt đúng thẩm quyền; điều chỉnh sau ngày 5 vẫn theo quy trình hợp lệ; phiếu in đúng phiên bản |
| 4 | Hồ sơ thuế/ngân hàng/bảo hiểm, chi trả và quyết toán nghỉ | Đối soát được tính–duyệt–phát hành–đã trả, không nhầm net phải trả với đã chi; hồ sơ nghỉ hoàn thành các khoản còn lại |
| 5 | Hoàn thiện web/mobile theo vai trò, inbox, dashboard tác vụ, hồ sơ 360 | Mỗi vai trò đi hết luồng được công bố; không hiện nút không thể dùng; lỗi/rỗng được phân biệt |
| 6 | Mở rộng tổ chức, ATS, onboarding, đánh giá, đào tạo/phúc lợi | Chọn module theo khách hàng; có nguồn dữ liệu thống nhất và hợp đồng tích hợp |

Không cần làm AI hay toàn bộ succession trước khi sửa lương thật. Nhưng cũng không nên gọi bản hiện tại là suite HRM đầy đủ chỉ vì menu và số controller nhiều.

## 8. Các kịch bản nghiệm thu còn phải chạy

| Nhóm | Kịch bản và kết quả bắt buộc |
|---|---|
| Nguồn thật | Tạo NV không phải 1/2, lương khác 15 triệu, không OT; bảng lương phải đúng hồ sơ đó |
| Công | Nghỉ không lương/nghỉ có lương/đi làm nửa ca; không tính trùng công và phép |
| Ca qua đêm | 22:00 ngày cuối kỳ → 02:00 kỳ sau, nghỉ qua nửa đêm; phân phút và hệ số đúng từng đoạn |
| OT | Ngày thường/nghỉ tuần/lễ/ban đêm; giới hạn ngày/tháng/năm và hai đơn duyệt đồng thời |
| Lương | THANG/NGAY/GIO, vào/nghỉ giữa kỳ, tăng lương giữa kỳ, kỳ có ngày chuẩn khác 22 |
| Thuế | Dưới/ngay trên từng ngưỡng, thu nhập cao, 0/1/2 người phụ thuộc, thay đổi hiệu lực giữa năm, cư trú/không cư trú |
| Bảo hiểm | Đúng đối tượng, mức căn cứ/phụ cấp, trần từng loại, tháng không hưởng lương theo điều kiện, phần NLĐ/NSDLĐ |
| Phụ cấp/quyết định | Theo kỳ/ngày/giờ/tỷ lệ, miễn/chiụ thuế, đóng/không đóng BH, thưởng đã duyệt và bồi thường; không trừ lương vì kỷ luật |
| Cạnh tranh dữ liệu | Tính → sửa nguồn → chốt; kết quả cũ phải bị chặn hoặc tính lại theo quy trình |
| Phiên bản | Công bố V1 rồi V2, in từ từng kỳ; so chênh lệch và chống chi hai lần |
| Quyền | ADMIN/HR đúng phòng/HR ngoài phòng/trưởng phòng/nhân viên; API đọc/ghi/export/phiếu lương không vượt phạm vi |
| Nghỉ việc | Có tài sản/nợ/phép còn/lịch tương lai/hợp đồng; hoàn thành checklist, quyết toán, thu hồi đúng sau ngày cuối |
| Tái tuyển | Không cộng công/phép/thâm niên cho khoảng đã nghỉ; không hồi sinh JWT cũ hay hợp đồng cũ |
| UI | Đổi nhanh kỳ với response đảo thứ tự; mất mạng/403/409/500; biểu mẫu giữ dữ liệu; bàn phím/drawer và các viewport |
| Triển khai | Khởi tạo DB sạch, nâng cấp DB có dữ liệu, rollback/backup restore, cấu hình proxy, job chạy/lỗi/cảnh báo |

## 9. Kết quả kiểm tra thực hiện trong phiên này

| Kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Giải nén chọn nguồn | 212 file frontend, 693 file backend/tài liệu/script/test phục vụ rà soát | Bỏ node_modules, target, dist, cache, ảnh nhị phân và log khỏi tập đọc; không thực thi script hướng dẫn trong ZIP |
| Kiểm kê mapping | 80 controller / 319 annotation / 344 path có alias | Chưa gọi từng HTTP; mapping điều kiện/params có thể cùng URL |
| Angular compiler, template/type check | `ngc -p tsconfig.app.json --noEmit`: exit 0 | Dùng dependency Angular 20.3.34 sẵn có, đúng bản khai trong package; không thay npm install sạch |
| Build frontend production | Không hoàn tất | esbuild báo không đọc được thư mục cha do quyền môi trường; không kết luận là code build lỗi. Xem frontend-build.log |
| 4 nhóm test frontend được chọn | 6 test: 3 pass, 3 không chạy thành công vì esbuild không đọc được đường dẫn | Nhóm helper định dạng số qua; các harness overview/preferences/report-service bị chặn khi build. Không có bằng chứng ba chức năng đó sai nghiệp vụ từ kết quả này |
| Maven test offline | Không đến được bước chạy test | Đã vào compile 419 file, dừng ở truy cập dependency jspecify-1.0.1.jar. Không kết luận lỗi compiler nguồn; chưa có kết quả test backend trong phiên này |
| DB/end-to-end/UI render | Chưa thực hiện | Chưa chứng nhận dữ liệu thực, tích hợp SQL Server, hiệu năng, mọi quyền và bố cục render |

Log và inventory nằm cùng thư mục báo cáo. Các hạn chế kiểm tra không xóa bỏ các lỗi xác định trực tiếp như mock tính lương, hàm thuế, in theo employeeId hoặc trạng thái công bố.

## 10. Dấu vết bản được rà

- frontend.zip SHA-256: `B402B3D54860161E851AAAB79F51506EC7ACD92E0444E78D8DCD83175D6A89D4`.
- HRM_System.zip SHA-256: `F4BC8BC7F2D378FDE8F0447508D87FF9BA95A7410EF829D77DF3332FB2D44975`.
- Thư mục nguồn tách ra: `C:/NguyenXuanAnh/Git-Nhom-HRM/qa_review_20261107`.
- Liên kết bằng chứng trỏ đúng bản nguồn từ ZIP trong thư mục này, không trỏ vào các file dự án đang được sửa ở thư mục gốc.

**Đánh giá cuối:** phần hồ sơ, phân công và công/đơn từ đã có nền đáng kể; phần lương hiện vẫn mang hành vi demo ở nguồn tính. Chưa đạt chuỗi HRM vận hành đầy đủ và chưa đủ điều kiện tin cậy để tính/chi lương thật. Cần sửa đúng chuỗi nghiệp vụ trước, rồi bổ sung các module còn thiếu theo phạm vi sản phẩm đã cam kết.
