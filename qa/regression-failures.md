# Phân loại bộ test đỏ

| Class | Số FAIL/ERROR | Chẩn đoán cần xử lý |
|---|---:|---|
| com.hrm.AttendanceServiceIntegrationTest | 1 | Fixture tham chiếu schedule ID 4 không tồn tại; không chứng minh code attendance lỗi. |
| com.hrm.AttendanceWorkflowIntegrationTest | 1 | Fixture ADMIN không khớp dữ liệu quyền/account của DB hiện tại. |
| com.hrm.duong.ModuleRepositoryIntegrationTest | 1 | H2 tạo payroll_periods đụng bảng đã tồn tại; cần sửa cô lập database/context. |
| com.hrm.hrm.service.G1EndpointAccessTest | 2 | standaloneSetup/mock services không chạy SecurityFilter/method-security; HTTP runtime anonymous sweep trả 401. |
| com.hrm.security.PermissionAuthorizationTests | 1 | Context thiếu PersonnelScopeService sau khi service thêm dependency bắt buộc; thêm mock vào context test. |
| com.hrm.service.WorkShiftDurationTest | 2 | Test legacy create/duration không khớp API break-aware và giới hạn mới; đối chiếu spec trước sửa. |
| com.hrm.service.WorkShiftPermissionTest | 1 | HR kỳ vọng được phép nhưng scope/permission fixture không khớp; cần kiểm tra context test. |
| com.hrm.service.WorkShiftValidationTest | 10 | Kỳ vọng IllegalArgumentException trong khi API hiện trả ResponseStatusException; xác nhận status/message thay vì chỉ class exception. |
| com.hrm.service.WorkShiftValidCasesTest | 4 | Legacy create bị 409 yêu cầu detailed break-aware API; phải cập nhật test đúng API còn được hỗ trợ. |

## Từng test và lỗi gốc

### com.hrm.AttendanceServiceIntegrationTest.kiemTraTaoCongVaChongTrung

FAIL: Khong tim thay lich kiem thu so 4 ==> expected: <1> but was: <0>

### com.hrm.AttendanceWorkflowIntegrationTest.kiemTraDieuChinhVaXacNhanCong

FAIL: Tai khoan kiem thu khong phai ADMIN hop le! ==> expected: <1> but was: <0>

### com.hrm.duong.ModuleRepositoryIntegrationTest.

ERROR: StatementCallback; bad SQL grammar [create table dbo.payroll_periods(period_id bigint primary key,period_start date,period_end date)]

### com.hrm.hrm.service.G1EndpointAccessTest.forbiddenEmployeeCannotReadOrWriteAnyG1CatalogOrAssignment

FAIL: Status expected:<403> but was:<200>

### com.hrm.hrm.service.G1EndpointAccessTest.anonymousCannotReadOrWriteAnyG1CatalogOrAssignment

FAIL: Status expected:<401> but was:<200>

### com.hrm.security.PermissionAuthorizationTests.

ERROR: Error creating bean with name 'employeeService': Unsatisfied dependency expressed through field 'personnelScope': No qualifying bean of type 'com.hrm.service.PersonnelScopeService' available: expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {@org.springframework.beans.factory.annotation.Autowired(required=true)}

### com.hrm.service.WorkShiftDurationTest.exactly24HoursPassesValidation

FAIL: 
Wanted but not invoked:
jdbcTemplate.queryForObject(
    startsWith("INSERT INTO dbo.work_shifts"),
    class java.lang.Long,
    <any>,
    <any>,
    <any>,
    <any>,
    <any>,
    <any>,
    <any>
);
-> at org.springframework.jdbc.core.JdbcTemplate.queryForObject(JdbcTemplate.java:912)
Actually, there were zero interactions with this mock.


### com.hrm.service.WorkShiftDurationTest.longerThan24HoursRejected

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftPermissionTest.hrAllowed

FAIL: Unexpected exception thrown: org.springframework.web.server.ResponseStatusException: 403 FORBIDDEN "Không có quyền quản lý danh mục ca"

### com.hrm.service.WorkShiftValidationTest.rejectsMoreThanOneDayOffset

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidationTest.rejectsBackwardSameDayShift

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidationTest.rejectsBreakLongerThanShift

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidationTest.rejectsBlankShiftCode

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidationTest.rejectsNegativeDayOffset

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidationTest.rejectsBreakLongerThanOvernightShift

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidationTest.rejectsMissingStartTime

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidationTest.rejectsNegativeBreakMinutes

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidationTest.rejectsMissingEndTime

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidationTest.rejectsZeroLengthSameDayShift

FAIL: Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <org.springframework.web.server.ResponseStatusException>

### com.hrm.service.WorkShiftValidCasesTest.normalShiftIsAccepted

ERROR: 409 CONFLICT "Use detailed break-aware work time API"

### com.hrm.service.WorkShiftValidCasesTest.updateOfExistingShiftPreservesRequestedFields

ERROR: 409 CONFLICT "Use detailed break-aware work time API"

### com.hrm.service.WorkShiftValidCasesTest.shiftWithNoBreakIsAccepted

ERROR: 409 CONFLICT "Use detailed break-aware work time API"

### com.hrm.service.WorkShiftValidCasesTest.overnightShiftIsAccepted

ERROR: 409 CONFLICT "Use detailed break-aware work time API"
