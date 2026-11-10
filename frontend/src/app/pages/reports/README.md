# Báo cáo

## Mã nguồn và giao diện hiện tại

- [reports.html](reports.html): giao diện Angular, bộ chọn cách xem quỹ lương, bảng tháng/quý/năm và chi tiết các kỳ cấu thành.
- [reports.css](reports.css): bố cục, bảng, trạng thái chọn, hiển thị trên điện thoại và giao diện tối.
- [reports.ts](reports.ts): gọi API, trạng thái các bộ chọn, nhóm các batch thật, chọn ô, phân trang chi tiết và giữ lựa chọn khi tải lại tự động.
- [report-format.ts](../../services/report-format.ts): đọc, định dạng và cộng tiền thập phân chính xác.

Phần phác thảo độc lập đã được chuyển vào component báo cáo. Không còn mảng nhân viên, mức lương hoặc hệ số tiền minh họa; không dùng script sửa DOM hay API của môi trường phác thảo.

Bảng quỹ lương hiện hỗ trợ xem theo khoản lương hoặc kỳ lương, chọn tháng/quý/năm bắt đầu kỳ và chỉ tiêu khi xem từng kỳ. Bấm ô để lọc danh sách các batch cấu thành; Xem tất cả kỳ trở về danh sách đầy đủ. Chỉ tổng các kỳ thật API trả về. Ô không thuộc kỳ của hàng hiển thị dấu gạch; số tiền bằng không vẫn là 0. Không cộng các dòng tổng thu nhập, phụ cấp, thưởng, khấu trừ, thực nhận thành một tổng chồng lặp.

Nhóm thời gian dùng ngày bắt đầu kỳ và được ghi rõ trên nhãn, giữ nguyên ngày kết thúc cùng quy tắc nguồn. Không chia tiền một batch theo ngày, không tự phân bổ cho nhân viên hoặc phòng ban khi API chưa có dữ liệu tương ứng. Số tiền được cộng bằng số nguyên có tỷ lệ thập phân, không đổi qua Number.

Ngày kết thúc kỳ lương dùng `periodEnd`, lấy nguyên giá trị `payroll_periods.period_end` giống API quản lý kỳ. Báo cáo theo quy ước bao gồm ngày cuối của luồng chốt lương: chọn kỳ khi `period_start >= fromDate` và `period_end <= toDate`, không cộng một ngày vào bộ lọc. Tổng hợp, chi tiết nhân viên, dòng lương và Excel dùng cùng phạm vi này. Phân công có `effective_to` loại trừ ngày kết thúc, nên nhân viên chỉ thuộc phòng trong toàn kỳ khi `effective_to > period_end` hoặc chưa có ngày kết thúc. OT và kỳ phép giữ nguyên quy tắc riêng của chức năng nguồn; báo cáo không sửa hay tính lại đầu vào lương.

Xuất Excel vẫn dùng API hiện có và bộ lọc báo cáo đã áp dụng, gồm số tổng và chi tiết batch. Các lựa chọn trình bày ma trận hiện không đổi cấu trúc tệp Excel. Các mục còn lại ở thiết kế bên dưới chưa được triển khai chỉ vì phần phác thảo đã được ghép vào component.

### Kết nối API và đối chiếu chi tiết

Cả bốn tab dùng `ReportService` và interceptor xác thực chung. Bộ lọc chỉ áp dụng khi bấm Xem báo cáo; phân trang, mở chi tiết và xuất Excel dùng lại bộ lọc đã áp dụng. Khi đổi tab, đóng chi tiết hoặc tải bộ lọc mới, yêu cầu cũ bị hủy. Lỗi so sánh kỳ trước không làm mất biến động nhân sự của kỳ hiện tại. Có trạng thái đang tải, rỗng, lỗi và nút thử lại cho chi tiết và danh mục phòng ban.

| API đọc | Thao tác trên giao diện | Tham số chính |
|---|---|---|
| `/api/reports/employees`, `/contracts`, `/work`, `/payroll` | Tổng hợp bốn tab | Khoảng ngày, ngày đánh giá, ngưỡng hợp đồng tùy tab |
| `/api/reports/monitor/departments` | Danh mục phòng ban | Không |
| `/api/reports/monitor/employees`, `/work`, `/payroll` | Tổng hợp khi lọc phòng ban | `departmentId`, khoảng ngày theo tab |
| `/api/reports/monitor/employees/details` | Bấm một nhóm trong cơ cấu nhân sự | `groupBy`, `groupId` hoặc `groupCode`, `departmentId`, `page`, `size` |
| `/api/reports/monitor/movements` | Vào làm và nghỉ việc theo tháng | `fromDate`, `toDate`, `departmentId` |
| `/api/reports/monitor/contracts` | Hợp đồng sắp hết hạn/đã hết hạn | Bộ lọc hợp đồng, `kind`, `page`, `size` |
| `/api/reports/monitor/payroll/{batchId}/employees` | Bấm mã kỳ lương để xem nhân viên | Khoảng ngày đã áp dụng, `departmentId`, `page`, `size` |
| `/api/reports/monitor/payroll/{batchId}/employees/{detailId}/lines` | Bấm Các khoản lương của nhân viên | Khoảng ngày đã áp dụng, `departmentId` |

Chi tiết nhân sự dùng phân công hiện tại được xếp hạng bằng ngày hiệu lực và ID, cùng điều kiện với thống kê nhóm; nhóm chưa phân công dùng `groupId` bỏ trống. Trình độ dùng `is_primary` và `EXISTS`, nên nhiều hồ sơ bằng cấp không nhân bản nhân viên. Tên/mã nhân viên trong chi tiết lương lấy từ hồ sơ hiện tại; mã/tên khoản lương, chiều cộng/trừ và tiền lấy từ dòng snapshot đã lưu.

Chi tiết lương giữ nguyên `batchId` và phiên bản đã chọn, chỉ đọc bảng `DA_CHOT` có thông tin khóa. Không tự chuyển sang nháp hoặc bản chốt khác. Quyền vẫn là ADMIN + REPORT_VIEW; mọi API lương cần thêm PAYROLL_VIEW và trả `Cache-Control: no-store`. Các giá trị tiền, kể cả `amount` của dòng cấu thành, được đọc dưới dạng chuỗi để không mất chữ số. Bộ lọc phòng ban giữ đúng phạm vi hiện có của báo cáo: nhân viên có phân công phủ toàn kỳ; không bổ sung quy tắc phân bổ cho trường hợp chuyển phòng giữa kỳ.

Các API chi tiết được thêm trong [ReportMonitoringController](../../../../../HRM_System/src/main/java/com/hrm/controller/ReportMonitoringController.java) và [ReportDepartmentService](../../../../../HRM_System/src/main/java/com/hrm/service/ReportDepartmentService.java); không cần migration hay thư mục mới. Các chế độ nguồn bổ sung trong thiết kế đầy đủ bên dưới (quỹ phép, workspace OT, cảnh báo hợp đồng nguồn…) vẫn là phạm vi tiếp theo, chưa được coi là đã tích hợp.

Kiểm tra: chạy npm run test:reports và npm run build trong thư mục frontend.

<a id="thiet-ke-theo-nguon"></a>
## Thiết kế theo chức năng nguồn

Ngày rà soát bổ sung: 10/11/2026. Thiết kế này tiếp nối [bản rà soát nghiệp vụ](#ra-soat-nghiep-vu), theo yêu cầu ưu tiên chức năng gốc. Phạm vi thiết kế đầy đủ ở dưới; phần Mã nguồn và giao diện hiện tại ghi rõ phần đã tích hợp.

## Phạm vi và nguyên tắc

Giữ bốn tab Nhân sự, Hợp đồng, Công và thời gian, Quỹ lương. Mỗi chế độ xem có nguồn, tập bản ghi, mốc thời gian và bộ lọc riêng. Báo cáo chỉ tổng hợp và trình bày lại kết quả theo đúng quy tắc của nguồn. Thêm API đọc tổng hợp được phép, nhưng không đổi quy tắc duyệt, tính tiền, khóa kỳ, phân bổ giờ hoặc quyền thao tác nguồn để phục vụ giao diện báo cáo.

Từ “có nguồn” dưới đây nghĩa là mã nghiệp vụ và trường dữ liệu đã có; không có nghĩa API ma trận hoặc giao diện báo cáo tương ứng đã được triển khai. Dữ liệu thực tế và trạng thái migration trên SQL Server chưa được kiểm chứng trong đợt đọc mã này.

## Các phát hiện bổ sung có tác động đến thiết kế

1. Cảnh báo hợp đồng đã có hai danh sách riêng: hợp đồng sắp hết hạn và nhân viên đang làm chưa có hợp đồng hiệu lực. Nguồn cảnh báo dùng ngày hiện tại của đồng hồ nghiệp vụ, giới hạn số ngày từ 1 đến 365; không nhận ngày đánh giá lịch sử. Nguồn cảnh báo sắp hết hạn còn lọc nhân viên đang làm, khác truy vấn báo cáo đang có.
2. Bằng cấp chính, bằng cấp cao nhất còn hạn và tình trạng chứng chỉ là ba khái niệm khác nhau. Metadata nguồn đã có expiryDate, validityStatus, rankOrder và highest khi schema hỗ trợ. Không đồng nhất isPrimary với highest.
3. Màn hình OT gốc đã trả số phút đăng ký, đã duyệt, thực tế, hợp lệ và cần xử lý. Bộ lọc ngày của danh sách này dùng requested_start, không dùng ngày nộp hoặc chỉ cắt theo giờ thực tế. Phòng ban của dòng OT được lấy tại ngày bắt đầu OT đăng ký.
4. Quỹ phép đã có số tổng toàn bộ tập lọc và lịch sử từng quỹ, kèm danh sách đơn cấu thành và dấu hủy. Bộ lọc phòng ban ở đây theo phân công hiện tại; không đổi sang phòng tại ngày nghỉ trong báo cáo số dư mà vẫn gọi là khớp màn hình quỹ phép.
5. Màn hình tiền lương lấy phiên bản lớn nhất của kỳ, có thể là nháp điều chỉnh. Báo cáo đã chốt lấy bản chốt mới nhất. Phiếu lương chỉ được phát hành khi công bố. Cần hiển thị rõ kỳ và phiên bản để đối chiếu, không dùng một bộ lọc “mới nhất” mơ hồ cho cả ba chế độ.
6. Công bố không phải trạng thái CONG_BO của backend: nguồn giữ DA_CHOT và ghi published_at/published_by. Điều kiện công bố trong báo cáo phải theo trường nguồn này.
7. Các màn hình nguồn chưa đồng loạt hỗ trợ liên kết mở trực tiếp theo ID. Khi thêm hành động “Mở tại chức năng nguồn”, cần bổ sung nhận đúng ID tại giao diện đích; chỉ điều hướng sang đường dẫn chung sẽ không đảm bảo đang xem đúng bản ghi hoặc phiên bản.

## Nhân sự

### Cơ cấu hiện tại

Mục đích: đối chiếu được với danh sách nhân viên và phân công hiện tại.

- Bộ lọc: phòng ban, chức vụ, trạng thái nhân viên, từ khóa mã hoặc tên. Phân nhóm chọn phòng ban, chức vụ, trạng thái hoặc trình độ chính.
- Không đặt bộ lọc từ ngày đến ngày ở chế độ này, vì nguồn trạng thái và trình độ hiện tại không phải snapshot lịch sử.
- Số tổng: tổng hồ sơ trong bộ lọc, đang làm, đã nghỉ. Chọn một trạng thái thì các tổng phải thể hiện đúng tập đang lọc.
- Bảng tổng hợp: Nhóm, Số nhân viên, Tỷ lệ trong tập lọc. Bấm nhóm mở danh sách có Mã nhân viên, Họ tên, Phòng ban, Chức vụ, Trạng thái, Ngày vào làm.
- Nhóm không có phân công hoặc chưa có trình độ chính được tách rõ để đối chiếu số người, không lấy số bản ghi bằng cấp làm số nhân viên.
- Có thể bổ sung danh sách chứng chỉ sắp hết hạn/hết hạn bằng validityStatus của nguồn. Nếu metadata không có vì schema chưa hỗ trợ thì không đưa số 0 như thể đã kiểm tra.

Nguồn: [EmployeeController](../../../../../HRM_System/src/main/java/com/hrm/controller/EmployeeController.java), [EmployeeStatisticsService](../../../../../HRM_System/src/main/java/com/hrm/service/EmployeeStatisticsService.java#L24), [QualificationMetadata](../../../../../HRM_System/src/main/java/com/hrm/service/QualificationMetadata.java#L27).

### Biến động và lịch sử phân công

- Bộ lọc: khoảng ngày, nhân viên và loại phát sinh có dữ liệu nguồn chứng minh được.
- Đợt đầu giữ chỉ tiêu Vào làm và Nghỉ việc theo ngày hiện lưu trên hồ sơ, đúng khả năng nguồn hiện tại; không gọi là toàn bộ lịch sử tuyển dụng/nghỉ việc.
- Bảng tháng: Tháng, Vào làm, Nghỉ việc; mở danh sách hồ sơ tạo ra số đó.
- Lịch sử phân công: Mã nhân viên, Họ tên, Phòng ban, Chức vụ, Có hiệu lực từ, Đến trước ngày. Tái sử dụng ngày hiệu lực nguồn, không lấy ngày tạo bản ghi làm ngày điều chuyển.
- Không đếm mọi phân công mới là một lần điều chuyển vì nguồn cũng tạo phân công khi thêm nhân viên và tiếp nhận lại. Không tự đếm tiếp nhận lại nếu chưa có tập sự kiện đầy đủ và thống nhất.
- Với bảng nhiều kỳ, tổng số người duy nhất và tổng số lần phát sinh là hai chỉ tiêu khác nhau. Chưa cung cấp số nhân sự cuối từng tháng như snapshot lịch sử hoàn chỉnh.

Nguồn: [EmployeeAssignmentService](../../../../../HRM_System/src/main/java/com/hrm/service/EmployeeAssignmentService.java), [EmployeeTransferAuditService](../../../../../HRM_System/src/main/java/com/hrm/service/EmployeeTransferAuditService.java), [EmployeeRehireService](../../../../../HRM_System/src/main/java/com/hrm/service/EmployeeRehireService.java#L29).

## Hợp đồng

### Tổng hợp hợp đồng

- Bộ lọc cơ bản theo nguồn: nhân viên, trạng thái, từ khóa số hợp đồng. Bộ lọc loại hợp đồng có thể thêm ở API đọc tổng hợp vì contractTypeId đã có trong nguồn; không chỉ lọc trên một trang đã tải.
- Hai cách xem được ghi nhãn rõ: Trạng thái hiện tại và Ký trong kỳ. Ký trong kỳ dùng signed_date; không dùng start_date hoặc created_at thay thế.
- Bảng hồ sơ: Số hợp đồng, Mã nhân viên, Họ tên, Loại hợp đồng, Ngày ký, Bắt đầu, Kết thúc, Trạng thái, Lý do kết thúc.
- Ma trận ký trong kỳ: Loại hợp đồng hoặc nhân viên theo tháng/quý/năm; mỗi contract_id chỉ được đếm một lần theo ngày ký trong tập lọc. Điều kiện trạng thái hợp đồng bị hủy phải tách rõ khi chọn bộ lọc, không giả định “ký mới” luôn đồng nghĩa “đang hiệu lực”.
- Trạng thái DA_KET_THUC không đồng nghĩa mọi trường hợp đều là hết hạn tự nhiên. Phân loại theo reasonType mà nguồn trả về; dữ liệu thiếu lý do không tự gán sang HET_HAN.
- Khi nguồn đã đổi ngày kết thúc do chấm dứt sớm, báo cáo dùng ngày hiện tại của hồ sơ và lý do nguồn. Chưa cam kết tái hiện thời hạn gốc trước điều chỉnh nếu không có dữ liệu lịch sử tương ứng.

Nguồn: [ContractController](../../../../../HRM_System/src/main/java/com/hrm/controller/ContractController.java), [ContractResponse](../../../../../HRM_System/src/main/java/com/hrm/dto/ContractResponse.java), [ContractLifecycleService](../../../../../HRM_System/src/main/java/com/hrm/service/ContractLifecycleService.java).

### Cảnh báo hợp đồng

Giữ hai mục cùng chức năng nguồn:

| Mục | Phạm vi thời gian và dữ liệu | Các cột chính |
|---|---|---|
| Sắp hết hạn | Ngày nghiệp vụ hiện tại đến N ngày sau, N từ 1 đến 365; nhân viên đang làm và hợp đồng DA_KY theo nguồn | Mã nhân viên, Họ tên, Ngày hết hạn, Số ngày còn lại; mở contract_id tương ứng |
| Chưa có hợp đồng hiệu lực | Nhân viên DANG_LAM không có hợp đồng không bị hủy đang phủ ngày hiện tại, đúng query nguồn | Mã nhân viên, Họ tên, Kết thúc hợp đồng gần nhất, Số ngày gián đoạn, Bắt đầu hợp đồng kế tiếp |

Không ép hai mục cảnh báo vào bộ lọc quá khứ hoặc nhóm quý, vì nguồn hiện chưa có chế độ đó. Không thay ngưỡng nguồn từ 365 ngày sang giới hạn riêng của báo cáo. Muốn thêm phòng ban phải tổng hợp trên toàn tập phù hợp và áp dụng kiểm tra phạm vi; endpoint cảnh báo hiện chưa nhận departmentId.

Nguồn: [ContractWarningService](../../../../../HRM_System/src/main/java/com/hrm/service/ContractWarningService.java#L25), [ContractWarningRepository](../../../../../HRM_System/src/main/java/com/hrm/repository/ContractWarningRepository.java#L18).

## Công và thời gian

### Chấm công

- Bộ lọc: Từ ngày, Đến ngày, Nhân viên, Phòng ban, Cần đối soát hoặc Đã đủ dữ liệu. Giới hạn khoảng theo nguồn tổng hợp công hiện có; báo cáo nhiều năm không tự bỏ giới hạn này.
- Số tổng: số ca đi làm đã xác nhận, số bản ghi vắng đã xác nhận, giờ làm đã xác định, số bản ghi cần đối soát.
- Bảng: Mã nhân viên, Họ tên, Phòng ban theo dữ liệu ngày công nguồn, Số ca đi làm, Số bản ghi vắng, Giờ làm, Cần đối soát.
- Bấm nhân viên mở bảng ngày: Ngày, Số ca đi làm, Vắng, Giờ thực tế sau nghỉ, Trạng thái đối soát; sau đó mở attendance_id hoặc schedule_id nếu có quyền nguồn.
- Tính giờ bằng cùng AttendanceDayAllocation và dữ liệu nghỉ thực tế đã xác minh. Bản ghi không đủ căn cứ giữ trạng thái cần đối soát, không coi là vắng hoặc không có giờ làm.
- Một ca qua ngày có số ca gắn với work_date và số phút phân bổ theo các ngày thực tế. Nhóm báo cáo giữ cách nguồn phân bổ, không tự gán lại toàn bộ giờ vào ngày đầu hay ngày cuối.
- Không mặc định chia giờ cho 8 để thành công chuẩn; không thêm thống kê bất thường từ API đang tắt theo cấu hình.

Nguồn: [ConfirmedAttendancePeriodService](../../../../../HRM_System/src/main/java/com/hrm/service/ConfirmedAttendancePeriodService.java#L22), [AttendanceDayAllocation](../../../../../HRM_System/src/main/java/com/hrm/service/AttendanceDayAllocation.java), [AttendanceAnomalyController](../../../../../HRM_System/src/main/java/com/hrm/controller/AttendanceAnomalyController.java).

### Nghỉ phép

Trong mục Nghỉ phép có hai cách xem:

| Cách xem | Bộ lọc và thời gian | Bảng và số liệu |
|---|---|---|
| Đơn nghỉ | Nhân viên, loại nghỉ, trạng thái; khi thêm lọc ngày phải chọn rõ Ngày nộp hoặc Thời gian nghỉ | Mã đơn, Nhân viên, Loại nghỉ, Từ giờ, Đến giờ, Kết quả cấp 1, Trạng thái cuối, Giờ theo lịch, Dấu hủy |
| Quỹ phép | Năm/kỳ phép, phòng ban hiện tại, nhân viên, loại nghỉ; đúng nguồn quỹ phép | Nhân viên, Loại nghỉ, Bắt đầu kỳ, Đến trước ngày, Được cấp, Đã dùng, Còn lại |

Đơn duyệt cấp 1 chưa phải đã duyệt cuối. Các trạng thái SKIPPED và LEGACY xử lý như nguồn; không tự ép về đã duyệt cấp 1. Đơn rút/hủy được thể hiện theo dấu hủy kể cả trạng thái gốc còn DA_DUYET.

Giờ nghỉ theo lịch lấy g2_leave_allocations.charged_hours; giờ trừ quỹ chỉ gồm phân bổ có entitlement_id tương ứng. Không tự đồng nhất thời lượng đơn, giờ theo lịch và giờ trừ quỹ. Nguồn có trường hợp thai sản không cần phân bổ lịch; vẫn có đơn hợp lệ dù không có dòng giờ phân bổ.

Bấm quỹ mở các đơn cấu thành và lịch sử điều chỉnh như LeaveQuotaWorkspaceService.detail. Bộ lọc năm chọn kỳ phép giao năm, số đã dùng/còn lại là số dư hiện tại của các kỳ được chọn; không gắn nhãn lịch sử sử dụng riêng trong năm nếu kỳ giao nhiều năm. Không cộng số dư của các tháng để ra số dư năm.

Nguồn: [EmployeeRequestService](../../../../../HRM_System/src/main/java/com/hrm/service/EmployeeRequestService.java#L157), [RequestApprovalStages](../../../../../HRM_System/src/main/java/com/hrm/service/RequestApprovalStages.java), [LeaveQuotaWorkspaceService](../../../../../HRM_System/src/main/java/com/hrm/service/LeaveQuotaWorkspaceService.java#L41).

### Tăng ca

Chế độ mặc định Theo đợt OT khớp màn hình gốc:

- Bộ lọc: ngày bắt đầu OT đăng ký, nhân viên, phòng ban tại ngày bắt đầu đăng ký, trạng thái đơn, tình trạng giờ thực tế/xác nhận, đăng ký trước hoặc sau.
- Bảng: Mã nhân viên, Họ tên, Số đơn, Chờ duyệt, Đã duyệt, Giờ đăng ký đã duyệt, Giờ thực tế, Giờ hợp lệ, Cần xử lý, Cần đối soát.
- Lấy các giá trị requestedMinutes, approvedMinutes, actualMinutes, validMinutes và warning từ cùng logic của workspace. plannedBreakStatus chưa xác minh hoặc số phút không xác định phải giữ null/trạng thái thiếu dữ liệu như nguồn.
- Ma trận theo tháng ở chế độ này nhóm bằng tháng bắt đầu OT đăng ký; toàn bộ các số của đơn thuộc nhóm mà nguồn đã chọn. Không tự cắt actualMinutes về tháng đang lọc rồi vẫn gọi là cùng số với màn hình OT.
- Bấm nhân viên hoặc ô tổng mở danh sách mã đơn; mở đơn có giờ nghỉ dự kiến, giờ nghỉ thực tế, số phút theo ngày, phần vượt và lịch sử quyết định.

Chế độ Theo kỳ lương dùng periodId và các dòng validMinutes của OvertimePayrollInputService; không gọi số giờ thực tế đầy đủ là số giờ được tính lương. Mâu thuẫn period_end giữa các nguồn được ghi nhận riêng, không sửa giá trị kỳ hoặc cộng một ngày trong báo cáo để che sai khác.

Nguồn: [OvertimeWorkspaceService](../../../../../HRM_System/src/main/java/com/hrm/service/OvertimeWorkspaceService.java#L53), [OvertimeService.previewConfirmation](../../../../../HRM_System/src/main/java/com/hrm/service/OvertimeService.java#L266), [OvertimePayrollInputService](../../../../../HRM_System/src/main/java/com/hrm/service/OvertimePayrollInputService.java#L43).

## Quỹ lương

### Tổng hợp đã chốt

- Nguồn chính là các dòng tiền thuộc bảng lương đã chốt. Mỗi kỳ chỉ dùng một phiên bản chốt hợp lệ mới nhất, đồng thời trả periodId, batchId và versionNo để truy vết.
- Bộ lọc: kỳ hoặc tập kỳ, nhân viên, khoản lương; chọn hàng Nhân viên hoặc Khoản lương, chọn chỉ tiêu Tổng thu nhập, Khấu trừ, Thực nhận hoặc thành phần đã có trong nguồn.
- Bảng theo nhân viên: Mã nhân viên, Họ tên, các kỳ/tháng/quý/năm phù hợp, Tổng cộng. Bảng theo khoản: Mã khoản, Tên khoản, Chiều cộng/trừ, các cột kỳ và tổng.
- Tháng/quý/năm là nhóm các kỳ chuẩn đã có của phân hệ lương. Kỳ giao nhiều tháng hoặc không có ánh xạ thời gian rõ ràng cần được giữ thành kỳ riêng; không tự quy theo ngày chốt hoặc chia tiền theo số ngày.
- Chỉ tiêu công bố được xác định bằng published_at/published_by trên bản DA_CHOT. Bộ lọc Chỉ đã công bố phải chọn phiên bản phù hợp trong tập đã công bố; không lấy bản chốt mới hơn chưa công bố để đối chiếu với phiếu lương cũ.
- Bấm ô mở kỳ, phiên bản, payroll_detail_id và các dòng component_code_snapshot, component_name_snapshot, direction_snapshot, amount. Đọc số tiền từ dòng đã lưu, không tính lại hợp đồng, phụ cấp hay quyết định khi xem báo cáo.
- Tổng thu nhập là tổng ADD; Khấu trừ là tổng DEDUCT; Thực nhận là chênh lệch. Phụ cấp/thưởng là thành phần của tổng thu nhập nếu đã ghi nhận; không cộng thêm kết quả dự tính của các chức năng nguồn lần nữa.
- Chưa mở nhóm tiền theo phòng ban nếu chức năng lương chưa có quy tắc phân bổ tương ứng được xác định. Giữ tổng theo nhân viên/khoản/kỳ để đối chiếu chắc chắn; không tự gán phòng cuối kỳ.
- Phân biệt bảng lương đã chốt, đã công bố và đã thanh toán. Nguồn đang kiểm tra có hai trạng thái đầu, chưa có giao dịch thanh toán chứng minh số đã trả.

Nguồn: [PayrollBatchController](../../../../../HRM_System/src/main/java/com/hrm/controller/PayrollBatchController.java), [PayrollBatchService](../../../../../HRM_System/src/main/java/com/hrm/service/PayrollBatchService.java), [PayrollStatisticsRepository](../../../../../HRM_System/src/main/java/com/hrm/repository/PayrollStatisticsRepository.java).

### Đối chiếu một phiên bản

- Chọn kỳ, sau đó chọn rõ batch và phiên bản; có nhãn Nháp, Đã chốt, Đã công bố theo dữ liệu thực của nguồn.
- Trả đúng số Tổng Gross, Tổng khấu trừ, Net và các dòng nhân viên của batch đã chọn để so với màn hình tiền lương. Không thay sang bản chốt cũ trong chế độ này mà không đổi nhãn.
- Phiên bản nháp chỉ phục vụ đối chiếu; không được tự cộng vào báo cáo tổng hợp đã chốt.
- Phiếu lương có thể có nhiều bản công bố cho một kỳ; giữ từng phiên bản khi xem lịch sử nhưng không cộng tất cả phiên bản đó thành tiền của kỳ.
- Nguồn phụ cấp và quyết định có kết quả tính/duyệt riêng. Nếu cần xem, đặt hành động Mở nguồn bên dòng cấu thành hoặc một bảng đối chiếu được ghi nhãn rõ. Không gọi số dự tính là số đã vào lương, không đổi payrollEffect của quyết định để hợp với chỉ tiêu báo cáo.

Nguồn: [PayrollBatchController.getCurrentBatch](../../../../../HRM_System/src/main/java/com/hrm/controller/PayrollBatchController.java#L66), [PayslipService](../../../../../HRM_System/src/main/java/com/hrm/service/PayslipService.java#L86), [PayrollRepository](../../../../../HRM_System/src/main/java/com/hrm/repository/PayrollRepository.java), [DecisionPayrollService](../../../../../HRM_System/src/main/java/com/hrm/service/DecisionPayrollService.java), [AllowanceCalculationService](../../../../../HRM_System/src/main/java/com/hrm/service/AllowanceCalculationService.java).

## Cấu trúc màn hình được đề xuất

Chọn tab chính rồi chọn mục con phù hợp. Bộ lọc đổi theo mục; bấm Xem báo cáo mới áp dụng. Phần kết quả gồm vài số tổng quan, bảng dữ liệu chính và chi tiết mở từ nhóm hoặc ô. Biểu đồ là lựa chọn xem thêm. So sánh kỳ trước tắt mặc định và chỉ xuất hiện ở chỉ tiêu có thể so sánh đúng.

Không rải lại các đoạn giải thích cách tính trên màn hình. Dùng nhãn chính xác như Ngày bắt đầu OT, Đã công bố, Giờ được công nhận, Đến trước ngày của kỳ phép; dùng dấu trạng thái ngắn cho dữ liệu chưa xác minh. 0, chưa có dữ liệu, chưa chốt, chưa xác minh và lỗi tải là các trạng thái riêng.

Chi tiết mặc định chỉ đọc. Không đưa nút duyệt, chốt, sửa công hoặc điều chỉnh quỹ vào báo cáo. Điều hướng về chức năng gốc giữ đúng employeeId, requestId, contractId, periodId/batchId và bộ lọc để người dùng tự đối chiếu hoặc thực hiện thao tác với quyền nguồn.

## Công việc bổ sung thuộc phạm vi báo cáo

1. Định nghĩa chỉ tiêu với mã, đơn vị, nguồn, tập bản ghi, mốc ngày và cách tính tổng; tách các chỉ tiêu trùng tên nhưng khác nguồn.
2. Thêm API đọc tổng hợp/ma trận và chi tiết có phân trang; tái sử dụng hàm tính nguồn ở backend. Không lấy một trang danh sách rồi coi đó là toàn bộ tập báo cáo.
3. Dùng cùng bộ lọc đã áp dụng cho số tổng, bảng, biểu đồ và Excel; các tổng qua nhiều kỳ tính distinct đúng loại đối tượng khi cần.
4. Thêm khả năng mở đúng ID ở giao diện nguồn đối với những màn hình chưa nhận tham số liên kết. Không đổi hành vi mặc định của nguồn khi không có tham số từ báo cáo.
5. Giữ quyền báo cáo và quyền xem lương hiện tại. Endpoint nguồn có thể yêu cầu quyền quản lý khác quyền xem báo cáo; không cấp thêm quyền quản lý chỉ để đọc số tổng. Dùng adapter đọc có kiểm tra quyền báo cáo và cùng logic tính nguồn, hoặc chỉ hiện hành động mở nguồn khi tài khoản có quyền nguồn tương ứng.
6. Ghi riêng các vấn đề nghiệp vụ nguồn chưa thống nhất như period_end, phân bổ tiền theo phòng ban và độ đầy đủ lịch sử. Không biến chúng thành thay đổi tự động của báo cáo.

## Điều kiện nghiệm thu cho từng mục

| Mục | Cách đối chiếu bắt buộc |
|---|---|
| Cơ cấu nhân sự | Cùng phòng ban/trạng thái và cùng thời điểm với danh sách nguồn, đếm employee_id duy nhất |
| Cảnh báo hợp đồng | Cùng ngày nghiệp vụ, cùng N ngày, cùng tập nhân viên đang làm như ContractWarningService |
| Công | Khớp confirmed-summary về số ca, số phút và số cần đối soát, kể cả ca qua ngày |
| Đơn và quỹ phép | Cùng trạng thái cuối, cùng dấu hủy; số dùng/còn lại khớp quỹ và danh sách phân bổ |
| OT theo đợt | Cùng requested_start, bộ lọc xác nhận và validMinutes của workspace |
| OT theo kỳ lương | Khớp các dòng đầu vào kỳ tương ứng, không trộn với toàn bộ giờ thực tế |
| Tổng hợp lương | Một bản chốt được chọn mỗi kỳ, cộng từ dòng tiền; phiếu đã công bố đối chiếu đúng phiên bản |
| Đối chiếu phiên bản | Khớp batchId với màn hình nguồn, cả khi batch mới nhất là nháp |
| Excel | Cùng tập lọc và cách chọn nguồn với màn hình; tổng toàn bộ không phụ thuộc trang đang xem |

Phạm vi triển khai báo cáo gồm giao diện, API đọc tổng hợp, chi tiết và khả năng mở đúng bản ghi nguồn. Các sửa đổi ghi dữ liệu của phân hệ nguồn, migration dữ liệu cũ, tính lại lương và mở rộng mô hình sự kiện không nằm trong thiết kế tích hợp báo cáo này.


<a id="ra-soat-nghiep-vu"></a>
## Rà soát nghiệp vụ


Ngày rà soát: 10/11/2026. Phạm vi: mã nguồn đang có trên nhánh Viet, gồm thay đổi chưa commit. Đây là kết quả đọc mã giao diện, API, truy vấn và luồng nghiệp vụ; chưa đối chiếu số lượng bản ghi trên SQL Server và chưa chạy thay đổi dữ liệu nguồn.

Thiết kế chi tiết cập nhật theo nguyên tắc ưu tiên chức năng nguồn nằm trong [Thiết kế các mục báo cáo theo chức năng nguồn](#thiet-ke-theo-nguon). Các đề xuất mở rộng trong bản rà soát này là lựa chọn về sau; phạm vi tích hợp hiện tại lấy bản thiết kế chi tiết làm chuẩn.

## Kết luận thiết kế

Giữ bốn tab Nhân sự, Hợp đồng, Công và thời gian, Quỹ lương. Mỗi tab cần bộ lọc theo đúng nghiệp vụ và một bảng tổng hợp có thể mở chi tiết. Dùng lại cách tính của chức năng nguồn thay vì viết thêm một cách tính trong báo cáo. Quỹ lương ưu tiên bảng nhân viên hoặc khoản mục theo kỳ và theo nhóm tháng, quý, năm có căn cứ từ kỳ nguồn; chỉ mở nhóm tiền theo phòng ban khi nguồn đã có quy tắc gán tương ứng. Không chia số tiền của một kỳ thành từng ngày khi chưa có quy tắc phân bổ.

Nguyên tắc được người dùng xác nhận: ưu tiên thiết kế và quy tắc của các chức năng nguồn. Những điểm dưới đây trước hết là căn cứ để điều chỉnh báo cáo cho khớp nguồn, không phải yêu cầu thay đổi nghiệp vụ nguồn để phục vụ báo cáo. Khi hai chức năng nguồn mâu thuẫn, cần ghi nhận riêng và xác định quy ước của phân hệ sở hữu dữ liệu; không tự chọn một quy ước mới trong báo cáo. Các mở rộng lịch sử và kết nối tính lương thật là đầu việc riêng, không tự đưa vào phạm vi tích hợp báo cáo.

Các mẫu hóa đơn, khách hàng, sản phẩm trong văn bản yêu cầu được vận dụng như mẫu bảng tổng hợp theo thời gian. Nguồn dữ liệu đã kiểm tra ở HRM là nhân viên, phân công, hợp đồng, công, đơn nghỉ, OT và bảng lương. Báo cáo doanh thu hoặc lợi nhuận cần nguồn nghiệp vụ riêng; không suy ra từ quỹ lương.

## Chức năng nguồn và mức độ có thể sử dụng

| Chức năng nguồn | Dữ liệu và hành vi hiện có | Cách dùng trong báo cáo |
|---|---|---|
| Hồ sơ nhân viên | Ngày vào làm, trạng thái, ngày nghỉ hiện tại, bằng cấp chính | Cơ cấu hiện tại; thống kê phát sinh từ hồ sơ phải ghi rõ giới hạn lịch sử |
| Phân công và điều chuyển | Lịch sử phòng ban, chức vụ với effective_from và effective_to | Xác định phòng ban tại ngày phát sinh, không lấy phòng ban hiện tại cho mọi năm |
| Tiếp nhận lại | Tạo phân công mới, giữ ngày vào làm gốc, xóa termination_date hiện tại, ghi audit | Phải có sự kiện tuyển mới, tiếp nhận lại và nghỉ việc độc lập nếu muốn đầy đủ lịch sử |
| Hợp đồng | Ngày ký, thời hạn, loại, gia hạn, hủy, kết thúc, lý do kết thúc | Thống kê ký mới, hiệu lực, hết hạn tự nhiên, chấm dứt sớm thành các chỉ tiêu riêng |
| Chấm công và tổng hợp công theo kỳ | Bản ghi đã xác nhận, giờ thực tế sau trừ nghỉ, phân bổ qua ngày, dữ liệu cần đối soát | Tái sử dụng phần tính phút; thêm tổng hợp theo tháng, quý, phòng ban |
| Đơn nghỉ phép | Duyệt cấp 1 và duyệt cuối, rút/hủy, phân bổ giờ theo lịch | Chỉ cộng đơn duyệt cuối còn hiệu lực; giờ nghỉ theo phân bổ lịch |
| Quỹ phép | Được cấp, đã dùng, còn lại theo nhân viên và loại nghỉ | Bảng số dư theo kỳ phép; không cộng số dư từng tháng thành tổng năm |
| Tăng ca | Đăng ký, duyệt, giờ thực tế, giờ nghỉ chi tiết, phê duyệt phần vượt | Tách giờ thực tế và giờ được công nhận; dùng nguồn phút OT được công nhận cho chỉ tiêu đầu vào lương |
| Phụ cấp, khen thưởng và kỷ luật | Có nguồn tính theo kỳ và quyết định được duyệt | Phân biệt khoản được duyệt ở nguồn và khoản đã vào bảng lương; không cộng cả hai lần |
| Tiền lương | Kỳ lương, bản nháp, chốt, điều chỉnh phiên bản, công bố, dòng tiền và snapshot | Đọc một bản chốt mới nhất mỗi kỳ; số thực nhận trên bảng lương không chứng minh đã chuyển tiền |
| Xuất Excel | Đã có API, kiểm tra quyền và audit xuất | Mở rộng để dùng cùng tập số liệu và cấu trúc bảng với màn hình |

## Các điểm cần xử lý trước khi coi số liệu báo cáo là thống nhất

### Ngày kết thúc kỳ lương đang có hai cách hiểu

[PayrollReadinessGuard](../../../../../HRM_System/src/main/java/com/hrm/service/PayrollReadinessGuard.java#L31) dùng ngày period_end trong BETWEEN và tạo mốc cuối bằng end.plusDays(1), tức coi period_end là ngày cuối được tính. [DecisionPayrollService](../../../../../HRM_System/src/main/java/com/hrm/service/DecisionPayrollService.java#L43) cũng nhận apply_date bằng ngày cuối kỳ.

Trước đợt điều chỉnh báo cáo, [PayrollStatisticsRepository](../../../../../HRM_System/src/main/java/com/hrm/repository/PayrollStatisticsRepository.java#L18) gọi trực tiếp giá trị này là periodEndExclusive. API báo cáo hiện đã đổi thành `periodEnd` và lọc ngày cuối trực tiếp theo kỳ nguồn. [OvertimePayrollInputService](../../../../../HRM_System/src/main/java/com/hrm/service/OvertimePayrollInputService.java#L43) vẫn chỉ lấy ngày trước period_end; quy tắc riêng đó không bị thay đổi trong đợt sửa báo cáo.

Hướng đã áp dụng theo chức năng nguồn: lấy chức năng quản lý và chốt kỳ lương làm nơi xác định phạm vi ngày của báo cáo lương. Báo cáo đọc đúng ngày đã lưu, lọc `period_end <= toDate`, không đổi ngày dữ liệu hoặc tính lại tiền. Luồng chốt, phụ cấp, quyết định và khóa công dùng ngày cuối bao gồm; đầu vào OT dùng ngày cuối loại trừ. Mâu thuẫn giữa các chức năng nguồn này vẫn được ghi nhận riêng, không coi việc chỉnh báo cáo là đã giải quyết toàn hệ thống. Không cộng/trừ ngày hoặc cập nhật hàng loạt dữ liệu cũ. `period_end` trong `leave_entitlements` là kỳ phép và giữ quy tắc riêng của chức năng quỹ phép.

### Nhân sự hiện tại và lịch sử biến động khác nhau

[EmployeeStatisticsService](../../../../../HRM_System/src/main/java/com/hrm/service/EmployeeStatisticsService.java#L24) lấy cơ cấu theo ngày hiện tại; trạng thái và trình độ lấy giá trị hiện lưu. [ReportDepartmentService.movements](../../../../../HRM_System/src/main/java/com/hrm/service/ReportDepartmentService.java#L55) đọc hire_date và termination_date.

[EmployeeRehireService](../../../../../HRM_System/src/main/java/com/hrm/service/EmployeeRehireService.java#L56) giữ ngày vào làm gốc và xóa ngày nghỉ hiện tại khi tiếp nhận lại. Vì vậy truy vấn biến động hiện tại không thể đếm đầy đủ các lần tiếp nhận lại và nghỉ trước đó. Không quảng bá bảng nhân sự cuối từng tháng là lịch sử chính xác khi chỉ có nguồn này.

Giai đoạn đầu: tách Cơ cấu hiện tại khỏi Biến động trong kỳ. Để có lịch sử đầy đủ, thêm nguồn sự kiện hoặc các đợt làm việc; đối chiếu audit để bổ sung những lần có bằng chứng, không suy đoán dữ liệu thiếu. Nhóm chưa có phân công hoặc chưa có trình độ chính cần xuất hiện riêng để đối chiếu tổng.

### Tổng hợp hợp đồng đang loại trạng thái đã kết thúc

[ContractStatisticsRepository](../../../../../HRM_System/src/main/java/com/hrm/repository/ContractStatisticsRepository.java#L24) chỉ lấy DA_KY. [ContractLifecycleService.closeExpired](../../../../../HRM_System/src/main/java/com/hrm/service/ContractLifecycleService.java#L144) chuyển hợp đồng hết hạn sang DA_KET_THUC. Vì vậy số liệu quá khứ có thể thay đổi hoặc thiếu sau khi job đóng hợp đồng chạy.

Thiết kế mới phải xét khoảng hiệu lực và trạng thái/lịch sử kết thúc tương ứng với chỉ tiêu. Ký mới dùng signed_date; bắt đầu hiệu lực dùng start_date; hết hạn tự nhiên và chấm dứt sớm dùng lý do và lịch sử kết thúc. Không coi mọi end_date đã qua đều là hết hạn tự nhiên. Danh sách chi tiết và số tổng phải dùng cùng điều kiện.

### Công, nghỉ và OT đã có nguồn tính sát nghiệp vụ hơn

[ConfirmedAttendancePeriodService](../../../../../HRM_System/src/main/java/com/hrm/service/ConfirmedAttendancePeriodService.java#L142) dùng AttendanceDayAllocation để phân bổ phút sau giờ nghỉ; trả cả dữ liệu cần đối soát. Báo cáo hiện tại chủ yếu đếm bản ghi. Chỉ tiêu số ca và số giờ phải tách riêng; không mặc định một bản ghi hoặc tám giờ bằng một ngày công chuẩn.

[WorkStatisticsService](../../../../../HRM_System/src/main/java/com/hrm/service/WorkStatisticsService.java#L76) cộng giao khoảng thời gian bắt đầu/kết thúc đơn nghỉ. [EmployeeRequestService](../../../../../HRM_System/src/main/java/com/hrm/service/EmployeeRequestService.java#L167) đã tính và lưu g2_leave_allocations.charged_hours theo lịch khi duyệt. Báo cáo số giờ nghỉ theo lịch và giờ trừ quỹ cần dựa trên các phân bổ này. Đơn bị rút/hủy phải được loại dù trạng thái gốc còn DA_DUYET.

Với nghỉ thai sản không cần lịch, có thể có đơn hợp lệ mà không có giờ phân bổ; vẫn đếm đơn và hiển thị khoảng nghỉ, không tự suy ra giờ trừ quỹ. Khi chia một đơn qua nhiều tháng, cộng số giờ theo ngày/ca liên quan nhưng tổng số đơn toàn khoảng phải đếm distinct request_id.

[OvertimeRecognizedMinutes](../../../../../HRM_System/src/main/java/com/hrm/service/OvertimeRecognizedMinutes.java#L44) xác định phần giao được duyệt, phần vượt đã duyệt và giờ nghỉ chi tiết. Báo cáo hiện tại trừ nghỉ theo tỷ lệ thời lượng và chưa dùng nguồn này. Báo cáo phải tách OT thực tế khỏi OT được công nhận cho lương; không gọi cả hai là một chỉ tiêu.

### Tiền lương đã có mô hình phiên bản nhưng luồng tính mới chưa nối nguồn thật

[PayrollBatchService](../../../../../HRM_System/src/main/java/com/hrm/service/PayrollBatchService.java) có nháp, chốt, công bố và phiên bản điều chỉnh. Báo cáo hiện tại lấy bản chốt mới nhất mỗi kỳ, là nền tảng phù hợp.

Tuy nhiên, [PayrollExecutionService](../../../../../HRM_System/src/main/java/com/hrm/service/PayrollExecutionService.java#L63) chặn tính khi provider là dữ liệu mẫu. Hai implementation tìm thấy trong mã nguồn là MockSalaryContractProvider và MockAttendanceDataProvider, đều trả isSynthetic=true. PayrollInputPreparationService đã chuẩn bị nguồn hợp đồng, phụ cấp và quyết định nhưng chưa được nối vào luồng calculateBatch này. Kết luận này là từ mã nguồn, không xác nhận nguồn gốc từng bảng lương đã có trong database.

Thiết kế báo cáo vẫn đọc được các bảng lương đã chốt có sẵn, nhưng phải xử lý đúng trạng thái không có kỳ, chưa chốt và đã chốt bằng không. Nối nguồn tính thật là việc của phân hệ tiền lương, không được lén chạy tính/chốt khi người dùng xem báo cáo.

[PayrollCalculationEngine](../../../../../HRM_System/src/main/java/com/hrm/service/engine/PayrollCalculationEngine.java) đang gộp bảo hiểm và thuế vào mã KHAU_TRU; chưa tạo dòng THUONG từ DecisionPayrollService. Do đó chưa nên thiết kế các cột tách thuế, bảo hiểm hoặc đầy đủ tiền thưởng như thể dữ liệu đã đồng bộ. Muốn tách cần mã thành phần và snapshot ổn định; không đọc ngược chuỗi explanation để phân loại tiền.

### Quỹ lương theo phòng ban có thể thiếu nhân viên chuyển phòng

[ReportDepartmentService.payroll](../../../../../HRM_System/src/main/java/com/hrm/service/ReportDepartmentService.java#L129) chỉ nhận nhân viên có một phân công phủ toàn bộ kỳ. Chuyển phòng giữa kỳ có thể làm nhân viên không thuộc tổng của phòng nào, dù vẫn nằm trong tổng toàn đơn vị.

Hướng xử lý theo chức năng nguồn: ưu tiên bảng theo nhân viên và kỳ lương vì nguồn đã lưu được đến mức này. Không tự quy tiền về phòng cuối kỳ hoặc chia theo số ngày. Muốn mở tổng hợp tiền theo phòng ban phải có căn cứ gán phòng hoặc phân bổ từ nghiệp vụ lương; nếu nguồn chưa xác định thì coi đó là giới hạn của chế độ xem theo phòng, không âm thầm bỏ nhân viên hoặc tự tạo chính sách mới. Có thể hiển thị nhóm Chưa xác định khi dữ liệu thiếu căn cứ, nhưng không tự chuyển các trường hợp đã có quy tắc của nguồn vào nhóm này.

## Thiết kế từng tab

### Nhân sự

Mặc định Cơ cấu hiện tại: Tổng hồ sơ, đang làm, đã nghỉ; phân theo phòng ban, chức vụ, trạng thái, trình độ. Nhãn Tổng hồ sơ khác Số đang làm. Bộ lọc phòng ban và trạng thái áp dụng rõ ràng; không hiển thị bộ lọc khoảng ngày có vẻ làm thay đổi cơ cấu hiện tại.

Mục Biến động: từ ngày đến ngày hoặc tháng/quý/năm; các cột Tuyển mới, Tiếp nhận lại, Nghỉ việc, Điều chuyển vào, Điều chuyển ra khi có nguồn sự kiện đầy đủ. Bảng mặc định phòng ban theo kỳ, mở được danh sách sự kiện. Không cộng số nhân viên cuối các tháng thành tổng nhân viên năm.

### Hợp đồng

Tách hai chế độ: Tại ngày đánh giá và Phát sinh trong kỳ. Tại ngày đánh giá có số đang hiệu lực, sắp hết hạn trong N ngày, danh sách cần theo dõi. Phát sinh trong kỳ có ký mới, gia hạn, hết hạn tự nhiên, chấm dứt sớm. Bộ lọc loại hợp đồng, phòng ban, nhân viên; danh sách có số hợp đồng và loại, không chỉ tên nhân viên.

Bảng tháng/quý hiển thị chỉ tiêu hoặc phòng ban theo kỳ. Số hiệu lực là số tại mốc cuối kỳ; cột tổng tương ứng là số cuối khoảng, không phải cộng các mốc. Ký mới đếm theo ngày ký và mã hợp đồng riêng; liên kết gia hạn cần nguồn sự kiện có căn cứ.

### Công và nghỉ phép

Trong một tab có ba mục: Chấm công, Nghỉ phép và quỹ phép, Tăng ca. Bộ lọc thời gian, phòng ban, nhân viên; thêm loại nghỉ ở mục nghỉ. Có thể xem ngày, tháng, quý, năm; báo cáo nhiều năm phải tổng hợp phía backend thay vì kéo toàn bộ bản ghi ngày về trình duyệt.

Chấm công: Nhân viên, Phòng ban tại ngày phát sinh, Số ca đi làm, Số bản ghi vắng, Giờ làm thực tế, Số bản ghi cần đối soát. Có liên kết mở Tổng hợp công theo kỳ và bản ghi nguồn.

Nghỉ phép: số đơn còn hiệu lực, giờ nghỉ theo lịch, giờ trừ quỹ; số dư được cấp, đã dùng, còn lại theo kỳ phép và loại nghỉ. Số dư là trạng thái của kỳ phép, không phải số phát sinh để cộng các tháng. Số dư hiện tại không được gắn nhãn số dư lịch sử cuối tháng khi chưa có lịch sử điều chỉnh tương ứng.

OT: số đơn, giờ đăng ký đã duyệt, giờ thực tế, giờ được công nhận cho lương, phần chờ xử lý. Dữ liệu nghỉ chi tiết thiếu hoặc mâu thuẫn đi vào trạng thái cần đối soát; không coi là không có OT.

### Quỹ lương

Bộ lọc chính: năm hoặc tập kỳ lương, cột tháng/quý/năm, hàng nhân viên/phòng ban/khoản lương, chỉ tiêu trước khấu trừ/phụ cấp/thưởng đã ghi nhận/khấu trừ/thực nhận. Số liệu từ payroll_lines và snapshot của một bản chốt mới nhất mỗi kỳ. Chưa đưa chỉ tiêu chi tiền thực tế vì nguồn đã kiểm tra không có giao dịch thanh toán lương.

Bảng mẫu: Mã nhân viên, Họ tên, Quý 1, Quý 2, Quý 3, Quý 4, Tổng cộng. Bấm ô mở các kỳ, phiên bản bảng lương và dòng tiền cấu thành. Dòng tổng phải tính trên toàn bộ tập lọc, không phải chỉ trang đang xem.

Đề xuất ghi nhận tiền theo kỳ lương, không theo ngày người dùng nhấn chốt. Sau khi thống nhất period_end, nhóm một kỳ vào tháng của ngày cuối kỳ; nếu có kỳ giao nhiều tháng, giữ nguyên kỳ và không tự chia số tiền theo số ngày. Quy tắc này phải dùng giống nhau cho bảng tổng, chi tiết, biểu đồ và Excel.

## Quy tắc giao diện chung

- Chọn loại báo cáo trước, chỉ hiện bộ lọc thực sự tác động đến loại đang xem.
- Bảng tổng hợp là phần chính; vài số tổng quan ở trên và một biểu đồ tùy chọn, không lặp cùng dữ liệu thành nhiều khối.
- So sánh kỳ trước là tùy chọn tắt mặc định; khi bật có ngày và phạm vi rõ ràng. Dữ liệu đang tính lại không được mô tả là bản báo cáo lịch sử đã lưu.
- Không đưa lại các đoạn giải thích dài rải rác. Đơn vị, thời điểm đánh giá và tên chỉ tiêu phải đủ chính xác ngay trên nhãn; quy tắc chi tiết nằm trong tài liệu nghiệp vụ hoặc trợ giúp theo yêu cầu.
- 0 là có dữ liệu hợp lệ và kết quả bằng không. Chưa chốt, chưa có kỳ, thiếu nguồn hoặc không tải được là trạng thái riêng; không chuyển lỗi thành 0.
- Bộ lọc nhân viên phải tra cứu được người đã nghỉ có phát sinh trong kỳ, không chỉ nhân viên đang làm.
- Dùng employee_id để gộp lịch sử; mã nhân viên có thể thay đổi khi đổi chức vụ. Snapshot giữ mã/tên tại thời điểm chốt khi cần đối chiếu lịch sử.
- Giao diện và Excel hiển thị nguyên ngày kết thúc kỳ lương. Báo cáo lương lọc trực tiếp theo ngày này, không chuyển thành mốc endExclusive. Các truy vấn công dùng mốc cuối ngày riêng theo chức năng nguồn.

## API và nguồn dữ liệu cần bổ sung

Giữ quyền hiện tại: màn hình báo cáo dành ADMIN có REPORT_VIEW; quỹ lương thêm PAYROLL_VIEW. Nếu mở cho HR sau này, cần thay cả route, controller và bộ lọc phạm vi; không chỉ ẩn menu. Các API nguồn có kiểm tra quyền khác nhau, vì vậy tái sử dụng hàm tính chung không đồng nghĩa gọi nguyên endpoint quản lý từ mọi tài khoản báo cáo.

Đề xuất mở rộng API theo từng nghiệp vụ, cùng nhận period grouping và bộ lọc được phép; không dựng một API SQL tùy ý cho mọi bảng. Tách summary, series/matrix và detail có phân trang. Response có cùng định danh chỉ tiêu, đơn vị, cột kỳ và quy tắc tổng. Các tổng được tính ở backend bằng decimal; tiền gửi dạng chuỗi chính xác để trình duyệt không làm mất số.

Thay việc [ReportsPage.loadWorkTrend](reports.ts#L295) gọi báo cáo riêng cho từng tháng bằng một API trả chuỗi tháng/quý trong một lần. Một tập lọc chuẩn được dùng cho số tổng, bảng, chi tiết, biểu đồ và xuất Excel.

Xuất Excel nên có sheet Tổng hợp và Chi tiết, tên bộ lọc dễ đọc, đơn vị và thời gian lập. Giữ kiểm tra quyền và audit xuất đã có trong [ReportExportService](../../../../../HRM_System/src/main/java/com/hrm/service/ReportExportService.java#L25). Nếu tổng/chi tiết được đọc khác thời điểm, cần snapshot hoặc cơ chế nhất quán khi xuất; không cam kết hai lần tải luôn giống nhau khi dữ liệu nguồn đang thay đổi.

## Thứ tự thực hiện

1. Lập ánh xạ chỉ tiêu báo cáo với chức năng nguồn, giữ quy tắc và quyền của nguồn; sửa các truy vấn báo cáo đang lệch nguồn. Ghi nhận riêng các mâu thuẫn giữa chức năng nguồn về ngày cuối kỳ và cách gán phòng ban, không tự giải quyết bằng chính sách mới trong báo cáo.
2. Làm quỹ lương theo nhân viên/kỳ và bảng tổng hợp công từ các nguồn đã có. Nối nguồn tính lương thật là đầu việc riêng của phân hệ lương; báo cáo đọc dữ liệu đã chốt có sẵn.
3. Bổ sung tổng hợp theo phòng ban, tháng/quý/năm và chi tiết mở từ ô; dùng cùng response cho Excel.
4. Khi có nhu cầu mở rộng nghiệp vụ nguồn, bổ sung dữ liệu sự kiện tuyển lại/nghỉ/điều chuyển và lịch sử cần thiết trước khi mở báo cáo nhân sự lịch sử đầy đủ. Trước đó chỉ cung cấp các chỉ tiêu mà chức năng nguồn hiện tại chứng minh được.

## Các tình huống cần xác minh khi triển khai

- Hai phiên bản lương cùng kỳ, một bản nháp điều chỉnh và một bản chốt: chỉ chọn bản chốt hợp lệ mới nhất, không thay bằng bản nháp.
- Nhân viên chuyển phòng giữa tháng: tổng các nhóm phòng ban bằng tổng toàn đơn vị, kể cả nhóm chưa xác định, không bỏ và không nhân đôi tiền.
- Ngày cuối tháng có công/OT/quyết định: không mất hoặc tính sang hai kỳ vì period_end.
- Một ca và giờ nghỉ qua nửa đêm/cuối tháng; OT thực tế dài hơn phần được duyệt: giờ thực tế và giờ được công nhận tách đúng.
- Đơn nghỉ qua tháng/năm, nhiều ca, có giờ nghỉ giữa ca, có hủy sau duyệt: giờ theo lịch khớp nguồn phân bổ; đếm số đơn toàn khoảng không cộng trùng theo tháng.
- Hợp đồng đổi từ DA_KY sang DA_KET_THUC: báo cáo kỳ trước vẫn có hợp đồng từng hiệu lực; phân biệt hết hạn tự nhiên và kết thúc sớm.
- Nhân viên nghỉ rồi tiếp nhận lại; thay đổi mã nhân viên: không mất sự kiện có căn cứ và không tạo hai người trong tổng nhân viên duy nhất.
- Có số 0 hợp lệ, chưa chốt, thiếu dữ liệu và lỗi API: bốn trạng thái không bị nhập làm một.
- Bộ lọc, phân trang và Excel: tổng toàn bộ đúng với tập lọc, không phụ thuộc trang đang hiển thị; quyền được kiểm tra trên mọi đường xem và xuất.

Các kiểm tra tích hợp có ghi dữ liệu chỉ chạy trên database kiểm thử riêng. Đợt rà soát này chỉ đọc mã và lập thiết kế, không khởi động backend hay chạy migration.
