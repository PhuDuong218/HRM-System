# Rà soát phần việc của Xuân Anh trong HRM Center

**Cập nhật sau rà soát:** Đã triển khai phần trưởng phòng, bổ sung audit riêng cho thao tác này và sửa mốc ngày điều chuyển/nghỉ việc liên quan. Xem [hướng dẫn triển khai](C:/NguyenXuanAnh/Git-Nhom-HRM/HUONG_DAN_TRUONG_PHONG.md). Nội dung dưới đây giữ nguyên như bản đánh giá ban đầu, không phải tất cả lỗi vẫn còn nguyên sau triển khai.

Ngày đối chiếu: 23/10/2026, Asia/Saigon. Workspace: `C:/NguyenXuanAnh/Git-Nhom-HRM`. Nhánh: `XuanAnh`; HEAD khi rà soát: `d113da4`. Báo cáo đánh giá cả những thay đổi chưa commit đang có trên máy.

## Kết luận

Bạn đi đúng hướng kiến trúc và đã xây phần lớn bộ khung dữ liệu lõi. Tuy nhiên, phần việc được giao chưa đạt điều kiện bàn giao: còn lỗi khoảng ngày, thiếu migration, thiếu nghiệp vụ đơn từ và audit chung, chưa có kiểm thử nghiệp vụ. Tiến độ phù hợp với cuối đợt dữ liệu lõi và bắt đầu đợt nghiệp vụ đầu vào; chưa qua đầy đủ điều kiện nghiệm thu đợt dữ liệu lõi.

Có entity/API cho 8 trong 12 bảng thuộc phạm vi của bạn. Đây là số lượng bảng đã được triển khai ở một mức độ nào đó, không phải tỷ lệ hoàn thành công việc: phần duyệt đơn, hạn mức thực tế, audit và tích hợp còn đáng kể.

## Nguồn đã đọc và cách sử dụng

- [Phân công](D:/Downloads/Phan_cong.docx): xác định ownership, công việc, lộ trình và tiêu chuẩn bàn giao.
- [Giải thích CSDL](D:/Downloads/Giai_thich_CSDL_HRM_Center.docx): ý nghĩa 33 bảng và quan hệ.
- [SQL khởi tạo](D:/Downloads/create_database.sql): đối chiếu cột, CHECK, FK, index, trigger, quy ước thời gian.
- [Đặc tả](C:/Users/GPA/Downloads/PROJECT_SPEC.md): yêu cầu hệ thống, ba vai trò, luồng nghiệp vụ và các điểm chưa chốt.
- Mã nguồn backend, frontend, cấu hình không bí mật, Git status/history và test trong workspace; danh mục cấu trúc của SQL Server local được đọc bằng truy vấn SELECT.

Các câu lệnh Git, lời nhắc dừng ở giai đoạn đặc tả và hướng dẫn triển khai nằm trong tài liệu là nội dung được đánh giá, không phải yêu cầu mới của người dùng. Không thực thi các câu lệnh đó để đổi nhánh, merge hay thay database.

Đoạn mô tả hiện trạng trong PROJECT_SPEC đã cũ so với repo: tài liệu nói chỉ có health/Role và chưa có CRUD/Angular, nhưng repo hiện có CRUD và Angular; repo lại chưa có endpoint health mà tài liệu nói đã có. Phải lấy code và kiểm tra thực tế làm căn cứ tiến độ.

## Bối cảnh cần giữ cho các lượt làm việc tiếp theo

Người dùng là Xuân Anh, phụ trách backend các module 2, 3, 4, 5, 9, audit dùng chung và tích hợp. Không yêu cầu người dùng nhắc lại tên, phân công hoặc luồng đã có trong bốn tài liệu.

Web ADMIN/HR dùng Angular/TypeScript/Bootstrap; Mobile EMPLOYEE dự kiến Android Java; hai client dùng cùng Spring Boot REST API và SQL Server. Backend kiểm tra quyền và sở hữu dữ liệu. EMPLOYEE chỉ xem dữ liệu của chính mình; HR không quản trị tài khoản hay tự nâng quyền.

Luồng xuyên suốt: tạo hồ sơ → phân công phòng ban/chức vụ và trình độ → hợp đồng/điều kiện lương → lịch dự kiến → công thực tế được xác nhận, nghỉ được duyệt, OT được duyệt và xác nhận thực tế → phụ cấp/quyết định hợp lệ → tính nháp → kiểm tra/chốt → công bố phiếu → nhân viên tự xem → báo cáo.

Lịch không phải công; OT chưa duyệt không vào lương; không xóa nhân viên nghỉ việc; giữ hợp đồng cũ. Bảng lương đã chốt bất biến, điều chỉnh bằng phiên bản mới và snapshot. Không tự coi công thức lương, thuế, bảo hiểm, hệ số OT hay chính sách hủy đơn là đã được chốt.

Khoảng `effective_*` và `period_*` dùng `[start,end)`; riêng `contracts.end_date` là ngày cuối bao gồm. Audit dùng UTC; ngày/giờ công theo giờ trung tâm. Các ngày nghỉ được đề nghị và ngày được duyệt phải phân biệt.

Ownership nhóm: Xuân Anh nhân sự/trình độ/đơn/audit; Dương hợp đồng/phụ cấp/quyết định; Nhi ca/lịch/công/OT; Phương lương/phiếu; Việt xác thực/phân quyền/báo cáo.

## Đã làm đến đâu

| Phần việc | Bằng chứng hiện tại | Đánh giá |
| --- | --- | --- |
| Nhân viên | Entity/repository/service/controller/DTO; tạo kèm phân công; xem/sửa; tìm tên/mã, trạng thái, phòng ban/chức vụ; phân trang | Có triển khai, cần sửa và test nghiệp vụ |
| Phòng ban | Thêm/xem/sửa, ngừng sử dụng, phân trang | Có triển khai, còn lệch chuẩn xử lý mã và cập nhật |
| Chức vụ | Thêm/xem/sửa, kiểm tra mã trùng, ngừng sử dụng, phân trang | Có triển khai, chưa có test nghiệp vụ |
| Lịch sử phân công | Xem lịch sử, điều chuyển trong transaction, khóa Employee, đóng bản cũ rồi tạo bản mới | Cách tiếp cận đúng; có lỗi ngày kết thúc |
| Danh mục trình độ | Thêm/xem/sửa, kiểm tra mã, ngừng sử dụng, phân trang | Có triển khai, chưa có test nghiệp vụ |
| Trình độ nhân viên | Nhiều bằng cấp, chọn trình độ chính, khóa cha, flush bỏ primary cũ, xóa mềm, phân trang | Có triển khai; DB local đã có cột bổ sung nhưng thiếu migration trong repo |
| Loại nghỉ | CRUD theo hướng ngừng sử dụng, isPaid, mã bất biến, phân trang | Có triển khai; giữ isPaid bất biến là cách bảo vệ ý nghĩa lịch sử hợp lý |
| Hạn mức phép | BigDecimal, cấp/sửa/xem, khóa Employee khi cấp/sửa, chống chồng kỳ cùng nhân viên/loại nghỉ, lọc và phân trang | Có nền tảng; chưa tính phép đã dùng/còn lại và chưa gắn duyệt đơn |
| EmployeeRequest | Không có entity/repository/service/controller tương ứng | Chưa triển khai |
| LeaveRequestDetail | Không có lớp tương ứng | Chưa triển khai |
| ResignationRequestDetail | Không có lớp tương ứng | Chưa triển khai |
| Audit dùng chung | Chỉ có ContractAuditService riêng cho contracts | Chưa hoàn thành trách nhiệm audit của Xuân Anh |
| API/documentation/tích hợp | Có handler lỗi và PagingUtils; có README; chưa thấy OpenAPI/Postman hay kịch bản kiểm thử nghiệp vụ | Đang làm, chưa đủ bàn giao |
| Frontend | Layout, menu, placeholder và trang kiểm tra kết nối; EmployeesPage chưa gọi API | Bộ khung, chưa phải chức năng nhân sự đã tích hợp |

Những điểm đang làm đúng cần giữ: tách controller/service/repository/DTO; nghiệp vụ ở service; không cascade REMOVE EmployeeAssignment; ngừng sử dụng danh mục thay xóa; giữ lịch sử phân công; dùng transaction và khóa Employee cho thao tác liên quan; bỏ primary cũ và flush trước khi đặt primary mới; dùng BigDecimal cho hạn mức và điều kiện chồng kỳ nửa mở.

## Vấn đề đã xác định

### 1. ZIP được theo dõi trong Git chứa cấu hình local có mật khẩu

Mức ưu tiên: cao. File [HRM_System.zip](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System.zip) có `src/main/resources/application-local.properties`, bản sao trong build và thư mục `target`. Kiểm tra ZIP hiện tại và ZIP ở HEAD đều thấy file local và giá trị trường `spring.datasource.password` không rỗng. Không chép các giá trị đó vào báo cáo.

`.gitignore` bỏ qua file local trên đĩa không loại được nội dung trong ZIP đã được Git theo dõi. Cần bỏ bản ZIP đóng gói có cấu hình local khỏi nguồn chia sẻ, tạo lại gói sạch nếu cần và kiểm tra lịch sử. Nếu mật khẩu trong ZIP là mật khẩu đang sử dụng và commit đã được chia sẻ, phải đổi mật khẩu đó. Xóa file ở commit mới không tự xóa bản cũ khỏi lịch sử. Trong lần rà soát này chưa kiểm tra việc phát tán trên remote.

### 2. Điều chuyển bị sai một ngày

Mức ưu tiên: cao. [EmployeeAssignmentService.java dòng 183](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/EmployeeAssignmentService.java:183) đặt `effectiveTo = transferDate.minusDays(1)`.

SQL dòng 5 quy định `[start,end)` và dòng 159 yêu cầu `effective_to > effective_from`. Khi chuyển ngày 23/10, phân công cũ phải kết thúc tại mốc 23/10, phân công mới bắt đầu 23/10. Đặt kết thúc 22/10 sẽ mất hiệu lực ngày 22/10. Nếu phân công cũ bắt đầu 22/10 và chuyển 23/10, code đặt hai mốc cũ cùng bằng 22/10 và CHECK từ chối. CHECK này cũng tồn tại trên database local đã đọc.

Cách sửa: đóng bản cũ bằng chính `transferDate`, giữ bước flush bản cũ trước insert bản mới. Kiểm thử trường hợp chuyển ngày kế tiếp, chuyển sau nhiều ngày, rollback và hai yêu cầu đồng thời.

### 3. Nghỉ việc chưa xử lý đúng ngày làm việc cuối và ngày có hiệu lực

Mức ưu tiên: cao. [EmployeeService.java dòng 277](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/EmployeeService.java:277) luôn lấy ngày hiện tại; dòng 289 đóng phân công tại chính ngày đó; dòng 293 chuyển DA_NGHI ngay.

Seed SQL thể hiện ngày cuối làm việc 31/08 thì phân công kết thúc ở mốc 01/09. Nếu `terminationDate` là ngày làm việc cuối cùng, đóng tại ngày đó loại mất ngày cuối; cần mốc `terminationDate.plusDays(1)`. Nếu nhân viên bắt đầu phân công và nghỉ trong cùng ngày, cách hiện tại còn vi phạm CHECK `effective_to > effective_from`.

API hiện không nhận ngày nghỉ được phê duyệt, không liên kết đơn và không có cơ chế áp dụng khi đến hạn. Nhân viên seed DANG_LAM chưa có phân công (ví dụ các hồ sơ quản trị mẫu) còn bị chặn nghỉ bởi điều kiện phải có đúng một phân công mở. Cần tách thao tác kết thúc làm việc rõ ràng, xử lý trường hợp không có phân công hợp lệ theo quy tắc dữ liệu, giữ hồ sơ và phối hợp Việt để vô hiệu quyền truy cập khi đến hạn. Không tự chuyển trạng thái ngay khi duyệt đơn có ngày cuối làm việc trong tương lai.

### 4. Cấu trúc DB local chưa tái tạo được từ tài liệu và repo

Mức ưu tiên: cao đối với bàn giao. [EmployeeQualification.java dòng 47](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/model/EmployeeQualification.java:47) và repository dùng `is_deleted`, `deleted_at`, `deleted_by`; SQL đã gửi không tạo ba cột này và repo không có script SQL/migration được theo dõi.

Database local thực tế là `hrm_test`, đã có `is_deleted bit NOT NULL DEFAULT 0`, `deleted_at datetime2`, `deleted_by bigint`. Vì vậy không kết luận API trình độ hiện lỗi thiếu cột trên máy bạn. Lỗi là một máy tạo DB mới từ file SQL sẽ không khớp code và không tái tạo được thay đổi này. Cần migration có đánh số, backfill/default và ràng buộc thích hợp; không chạy lại script khởi tạo trên DB có dữ liệu.

SQL dùng tên HRMCenter; README/example dùng hrm_db; local chạy hrm_test. Đổi tên DB theo môi trường không tự nó là lỗi, nhưng hướng dẫn bàn giao phải nói rõ. Database local có 33 bảng nghiệp vụ cộng `sysdiagrams`, không có bằng chứng bạn tự thêm bảng nghiệp vụ thứ 34.

### 5. API nghiệp vụ hiện chưa được bảo vệ

Mức ưu tiên: cao trước khi tích hợp người dùng thật. [pom.xml](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/pom.xml) chưa có Spring Security/JWT; chưa có SecurityConfig hay CurrentUser. Controller nhân viên, trình độ và hạn mức nhận ID mà chưa kiểm tra quyền hoặc sở hữu.

Đây chủ yếu là phần của Việt và là hạng mục tích hợp của bạn, không phải lý do bắt bạn viết lại module của Việt. Tuy nhiên chưa thể đánh dấu BR-01/02/09 hoặc phân quyền các API của bạn là hoàn thành. Cần chia endpoint quản lý và cá nhân theo hợp đồng đã thống nhất; `/me` suy ra employeeId từ phiên, không tin ID trong body. Khi nghỉ việc phải kiểm tra trạng thái tài khoản/nhân viên và chính sách thu hồi token chung.

### 6. Chưa có audit dùng chung cho nghiệp vụ nhân sự

[EmployeeService.java](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/EmployeeService.java), [EmployeeAssignmentService.java](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/EmployeeAssignmentService.java), trình độ và hạn mức chỉ cập nhật bản ghi; chưa ghi `audit_logs` cho những thao tác này. Các cột created_at/updated_at không thay thế lịch sử trước/sau. created_by/updated_by và deleted_by cũng chưa được gắn tài khoản hiện hành.

[ContractAuditService.java](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/ContractAuditService.java:25) đã có transaction bắt buộc nhưng chỉ hỗ trợ contracts và luôn ghi actor null. Cần interface audit chung nhận action/đối tượng/lý do/before/after/correlationId, dùng actor từ CurrentUser; audit cùng transaction để cùng rollback. Actor null dành cho tác vụ hệ thống, không thay người dùng thật.

### 7. Thời điểm audit đang trộn giờ local và UTC

SQL quy định UTC cho audit. Nhiều service dùng `LocalDateTime.now()`; riêng LeaveEntitlementService dùng `LocalDateTime.now(ZoneOffset.UTC)`. ContractAuditService cũng dùng giờ local. Trên máy UTC+7, cùng một cột sẽ lưu hai quy ước cách nhau bảy giờ mà không có offset.

Cần một cơ chế thời gian chung, UTC cho audit và múi giờ trung tâm cho ngày nghiệp vụ; không đổi mọi ngày/giờ công sang UTC một cách máy móc. Nên tiêm Clock để kiểm thử ranh giới ngày.

### 8. Thiếu kiểm tra ngày sinh trước ngày vào làm tại service

[EmployeeRequestDTO.java dòng 22](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/dto/EmployeeRequestDTO.java:22) và DTO cập nhật chỉ có `@Past`. Một ngày sinh ở quá khứ nhưng sau ngày vào làm vẫn qua validation; DB từ chối bằng `CK_employees_dates` và handler trả lỗi xung đột chung. Khi cập nhật phải so với hireDate đã lưu; khi tạo so với hireDate trong request. Nên trả lỗi 400 cụ thể trước khi save.

### 9. Nhân viên vào làm tương lai bị coi là đang có phân công hiện tại

[EmployeeService.java dòng 185](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/EmployeeService.java:185) không chặn hireDate tương lai, tạo trạng thái DANG_LAM và phân công mở từ ngày đó. [EmployeeAssignmentRepository.java dòng 45](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/repository/EmployeeAssignmentRepository.java:45), truy vấn tìm kiếm và DTO chỉ xét `effectiveTo IS NULL`, không xét ngày bắt đầu.

Do đó hồ sơ chưa đến ngày vào làm có thể hiện như đã được phân công và được một số nghiệp vụ chấp nhận vì trạng thái DANG_LAM. Cần quy tắc thống nhất: tạm từ chối hireDate tương lai nếu chưa hỗ trợ, hoặc hỗ trợ hồ sơ tương lai và truy vấn hiệu lực theo ngày (`from <= asOf` và `to IS NULL OR asOf < to`). Việc service điều chuyển đang chủ động từ chối ngày tương lai là giới hạn đã viết rõ, chưa thể coi đó là tính năng đã hoàn thành theo ngày tương lai.

### 10. DTO hồ sơ chưa đủ thông tin để đọc và cập nhật đầy đủ

[EmployeeDTO.java](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/dto/EmployeeDTO.java) thiếu terminationDate; các client/module khác không đọc được ngày nghỉ qua DTO này. identityNumber nhận khi tạo/sửa nhưng không được trả qua API chi tiết hiện tại. address có ở entity nhưng không có trong request/response.

Nên tách DTO danh sách, DTO quản lý chi tiết và DTO cá nhân với phạm vi phù hợp. Dữ liệu quản lý chi tiết cần đủ để điền lại form; PUT hiện gán các trường tùy chọn null sẽ xóa giá trị cũ, nên client không có thông tin identityNumber có thể vô tình làm mất dữ liệu đó khi gửi form đầy đủ. Phải thống nhất PUT thay thế hay PATCH cập nhật từng phần.

### 11. Phòng ban chưa nhất quán với các danh mục khác

[DepartmentService.java dòng 58](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/DepartmentService.java:58) không trim mã/tên và không kiểm tra mã trùng trước như PositionService. UNIQUE của DB vẫn chặn trùng, nên không kết luận duplicate đã lọt database; hiện người dùng nhận lỗi ràng buộc chung thay vì lỗi mã cụ thể, mã có khoảng trắng đầu vẫn có thể được lưu.

PUT yêu cầu departmentCode theo DTO nhưng service bỏ qua mã khác được gửi lên và trả thành công, thay vì từ chối thay mã như chức vụ/trình độ. description null bị giữ nguyên, khác PositionService dùng null để xóa mô tả. Cần chuẩn hóa hành vi và transaction cấp service khi bổ sung audit.

### 12. Quy tắc hạn mức đang chặt hơn SQL và chưa kiểm tra số phép đã sử dụng

[LeaveEntitlementRequestDTO.java dòng 29](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/dto/LeaveEntitlementRequestDTO.java:29) cấm 0 trong khi SQL cho `entitled_hours >= 0`. Đây là khác biệt quy tắc, chưa có căn cứ trong tài liệu để kết luận trung tâm cấm hạn mức 0. Cần thống nhất, đặc biệt khi cần đưa hạn mức về 0.

Service hiện cho sửa kỳ và tổng giờ; chưa có dữ liệu đơn để ngăn giảm xuống dưới số giờ đã duyệt/đã dùng hoặc làm mất kỳ bao phủ các đơn đã dùng hạn mức. Phần này là thiếu sót cần hoàn thiện cùng workflow đơn, không được gọi CRUD hạn mức hiện tại là quản lý phép đầy đủ. Số giờ nghỉ phải dựa trên lịch/ca và chính sách, không đơn thuần lấy chênh lệch hai timestamp bao gồm nghỉ trưa/ngày nghỉ.

### 13. Lỗi xung đột phiên bản chưa có phản hồi nghiệp vụ phù hợp

Các entity có @Version nhưng [GlobalExceptionHandler.java dòng 151](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/exception/GlobalExceptionHandler.java:151) bắt RuntimeException chung. Không có handler riêng cho optimistic locking; khi có xung đột cập nhật, lỗi này có thể bị trả 500 thay vì 409 và lời nhắc tải lại dữ liệu. Cần test hai cập nhật đồng thời. Khả năng cập nhật rowversion qua JPA cũng cần test ghi thực tế; contextLoads không kiểm tra điều này.

### 14. Hợp đồng phân trang và phản hồi chưa thống nhất giữa module

Các controller của bạn trả `Page<DTO>` trực tiếp, còn ContractController trả `PageResponse` với `items/page/size/totalElements/totalPages`. Vì vậy frontend dùng chung không thể mặc định một cấu trúc cho mọi danh sách. [PageResponse.java](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/dto/PageResponse.java) đã có, nên thống nhất và chuyển đổi ở biên API là công việc tích hợp phù hợp.

Thông báo DELETE nhân viên/phòng ban hiện nói "Đã xóa" dù thực tế nghỉ việc/ngừng sử dụng. Đổi thông báo cho đúng nghiệp vụ; thống nhất status/body thành công và tài liệu API. Imports trùng, code comment cũ và println debug là việc dọn cuối, không phải lỗi Java làm build thất bại.

### 15. Trang kết nối frontend đang gọi sai hợp đồng của backend hiện tại

[proxy.conf.json dòng 3](C:/NguyenXuanAnh/Git-Nhom-HRM/frontend/proxy.conf.json:3) trỏ 8080, backend cấu hình 2005. [HealthService](C:/NguyenXuanAnh/Git-Nhom-HRM/frontend/src/app/services/health.service.ts:10) gọi `/api/health` nhưng backend chỉ có [HelloController](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/controller/HelloController.java:9) với `/hello`. Các endpoint nghiệp vụ hiện còn ở `/employees`, `/departments`, v.v. chưa có tiền tố `/api`.

Chỉ sửa cổng không đủ. Cần thống nhất path/proxy và health thật; không đổi đường dẫn hàng loạt mà quên các thành viên/client. EmployeesPage class hiện rỗng và template ghi chờ API, nên frontend chưa tích hợp danh sách/CRUD, login, guard hay nghiệp vụ đơn. Đây là trạng thái đang phát triển đã thể hiện rõ, không phải frontend báo sai dữ liệu.

### 16. Bộ kiểm thử chưa kiểm tra phần việc được giao

[EmployeeQualificationServiceTest.java](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/test/java/com/hrm/hrm/service/EmployeeQualificationServiceTest.java) rỗng. [QualificationLevelServiceTest.java](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/test/java/com/hrm/hrm/service/QualificationLevelServiceTest.java) chỉ assert 2 + 3 = 5. [HrmApplicationTests.java](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/test/java/com/hrm/hrm/HrmApplicationTests.java) chỉ contextLoads.

Build xanh hiện không xác minh: CRUD hợp lệ/sai/trùng/không tìm thấy, khoảng hiệu lực, nghỉ việc, hai primary đồng thời, soft delete, audit hay quyền truy cập. Không thấy Postman/OpenAPI/kịch bản request-response nghiệp vụ tương ứng trong repo. Đây là lý do không thể tính các phần có lớp Java là đã bàn giao hoàn chỉnh.

### 17. Lỗi tích hợp thuộc module Dương cần chuyển đúng chủ sở hữu

[ContractService.java dòng 131](C:/NguyenXuanAnh/Git-Nhom-HRM/HRM_System/src/main/java/com/hrm/service/ContractService.java:131) sửa endDate khi chấm dứt, trong khi `tr_contracts_keep_history` cấm đổi end_date. Trigger có cả trong SQL gửi và DB local. Nếu ngày chấm dứt khác endDate cũ hoặc endDate cũ null, save sẽ bị trigger từ chối. Không phải mọi lời gọi đều lỗi: nếu endDate vốn đã bằng ngày đó thì điều kiện đổi ngày không xảy ra.

Cần báo Dương thiết kế thao tác kết thúc phù hợp hợp đồng bất biến và cơ chế điều chỉnh có căn cứ; không tự gỡ trigger. ContractAuditService dùng actor null và giờ local cũng cần phối hợp khi thay audit chung. Chưa coi các vấn đề này là phần code do Xuân Anh viết.

## Kiểm tra thực tế đã thực hiện

- Đọc đủ nội dung thân bài và các bảng của hai DOCX, đặc tả Markdown và SQL; đối chiếu mã nguồn liên quan.
- Chạy Maven `clean test` offline bằng Maven 3.9.16 có sẵn và Java 23.0.1; javac biên dịch 74 file nguồn với `release 21`, biên dịch 3 file test.
- Kết quả: BUILD SUCCESS, 2 test chạy, 0 thất bại, 0 lỗi. Spring context và kết nối SQL Server local thành công. Chưa chạy trên runtime JDK 21, nên không gắn nhãn đã xác minh JDK 21 runtime.
- Đọc metadata bằng SELECT trên database local `hrm_test`: 33 bảng nghiệp vụ + sysdiagrams; xác nhận CHECK ngày, index một phân công mở, index một primary và ba cột xóa mềm bổ sung.
- Kiểm tra danh sách file trong ZIP ở working tree và HEAD; không in mật khẩu ra báo cáo.
- Không chạy kịch bản ghi dữ liệu nghiệp vụ, không chạy script tạo DB/migration, không kiểm thử Web trên trình duyệt và không cài dependencies frontend. Vì vậy lỗi ngày, lệch API và thiếu migration ở trên dựa trên code/ràng buộc đã đối chiếu; chưa phải kết quả chạy end-to-end các thao tác ghi.
- Không sửa code nghiệp vụ, đổi nhánh, commit, push hay merge trong lượt rà soát. Chỉ thêm báo cáo này; build/test sinh lại target.

## Thứ tự làm tiếp

### Bước 1 Khóa các lỗi nền trước khi thêm module

Xử lý cấu hình nằm trong ZIP; bổ sung SQL gốc và migration ba cột trình độ; sửa mốc kết thúc điều chuyển và nghỉ việc; thống nhất UTC/ngày nghiệp vụ; thêm kiểm tra ngày sinh/hireDate. Làm rõ DTO hồ sơ và cấu trúc phân trang. Không thay DB bằng chạy lại script khởi tạo.

Tiêu chí: máy sạch có thể tái tạo schema; điều chuyển sau một ngày thành công, lịch sử liền mạch; nghỉ trong ngày vào làm không hỏng CHECK; ngày sinh sai bị từ chối rõ ràng; không có local secret trong gói chia sẻ.

### Bước 2 Có kiểm thử lõi có ý nghĩa

Viết test tạo nhân viên và phân công trong một transaction; mã trùng; FK không tồn tại/ngừng hoạt động; chuyển phòng ban; rollback; lịch sử sau nghỉ; đặt primary mới và soft delete; cấp/sửa hạn mức chồng kỳ. Chạy test tích hợp trên DB test riêng để kiểm tra trigger, filtered index và rowversion, không chỉ mock repository.

### Bước 3 Tạo audit dùng chung và điểm nối CurrentUser

Phối hợp Việt về actor, quyền và scope; mở rộng hoặc thay interface audit mà không làm hỏng ContractService. Thêm audit cho sửa nhân sự, điều chuyển, nghỉ việc, hạn mức, thay primary/xóa mềm và sau đó duyệt đơn. Ghi log cùng transaction và dùng UTC.

### Bước 4 Hoàn thiện workflow đơn nghỉ phép và nghỉ việc

Tạo EmployeeRequest + enum requestType/status; LeaveRequestDetail và ResignationRequestDetail dùng requestId làm PK/FK; DTO theo loại; repository/service/controller. Không đặt tên lẫn với EmployeeRequestDTO hiện tại vốn là DTO tạo hồ sơ nhân viên.

Tạo header và đúng một subtype trong cùng transaction; server gán người gửi từ CurrentUser, trạng thái CHO_DUYET, submittedAt; không nhận người duyệt/trạng thái tùy ý từ client. Danh sách/chi tiết cá nhân luôn scope theo chủ sở hữu. Phê duyệt chỉ đi từ CHO_DUYET sang DA_DUYET/TU_CHOI, lưu actor/time/reason, không tự duyệt, không xử lý hai lần.

Khi duyệt nghỉ việc: cập nhật approvedLastWorkingDate ở subtype trong lúc header vẫn CHO_DUYET, flush subtype trước rồi mới đổi trạng thái header để phù hợp trigger; sau khi xử lý không sửa subtype. Áp dụng nghỉ việc đúng ngày hiệu lực, giữ lịch sử và liên kết hợp đồng/công/lương.

Khi duyệt nghỉ phép: kiểm tra kỳ/hạn mức, khoảng trùng và thời lượng theo lịch/chính sách; khóa phạm vi phù hợp để hai đơn đồng thời không vượt số giờ. Không tự mặc định mọi loại nghỉ là có lương. Chỉ cung cấp đơn được duyệt dưới dạng DTO đọc cho Nhi và Phương.

Schema hiện có đúng ba trạng thái CHO_DUYET/DA_DUYET/TU_CHOI. Tài liệu phân công nói hủy khi phù hợp, còn đặc tả chưa chốt hủy/rút đơn. Chưa thêm HUY/DA_HUY hoặc hard-delete để tạo chức năng hủy trái schema; tách phần đó thành quyết định nghiệp vụ còn mở.

### Bước 5 Tích hợp và bàn giao

Thống nhất với Việt các endpoint management/me và test 401/403/scope; cung cấp employeeId, trạng thái, hireDate/terminationDate và leave DTO cho Nhi/Phương. Thống nhất phân trang/lỗi/date/time/tiền. Sau backend ổn định mới nối Angular: cổng/path health → danh sách nhân viên thật → tìm kiếm/phân trang → form → danh mục/trình độ → đơn từ theo quyền.

Viết Postman/OpenAPI hoặc ví dụ request-response; cập nhật README và đặc tả hiện trạng. Chạy build/test sạch và demo nghiệp vụ; commit thay đổi theo module lên nhánh XuanAnh, PR để review/merge theo quy trình nhóm. Working tree tại thời điểm rà soát còn nhiều sửa đổi chưa commit, nên chưa tính các sửa đó là đã bàn giao qua PR.

## Mốc tiếp tục

Tác vụ hiện tại của người dùng là đánh giá và hướng dẫn, chưa yêu cầu sửa toàn bộ code. Bước triển khai hợp lý đầu tiên sau báo cáo là nhóm lỗi nền ở bước 1, bắt đầu từ khoảng ngày và migration; sau đó kiểm thử lõi, audit và workflow đơn. Không chuyển sang làm lương, lịch/công hoặc JWT thay các chủ module khác chỉ vì các phần đó chưa có trong checkout này.
