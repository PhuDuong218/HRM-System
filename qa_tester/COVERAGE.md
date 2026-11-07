# Phạm vi và bằng chứng

Web responsive, không phải mobile app. Công cụ: trình duyệt tích hợp Chromium; phiên bản binary chưa ghi nhận. Không kiểm Chrome + Edge độc lập, không giả định đã kiểm app nhân viên.

11 route Tier 1 × 7 độ rộng đo thực tế (1440,1280,1024,768,390,321,320) =77; 15 route khác ×1440/390=30; tổng107 lượt mở. Không thấy tràn ngang toàn trang trong các lượt đo. Chưa nghiệm thu CRUD/form ở mọi kích thước.

## Route

| Route | Kết quả | Giới hạn |
|---|---|---|
| `/login` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/overview` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/employees` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/assignments` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/certificates` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/leave-entitlements` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/audit` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/connection` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/accounts` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/departments` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/department-catalog` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/leave-types` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/positions` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/qualifications` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/contracts` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/contract-types` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/work-shifts` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/schedules` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/attendance` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/attendance/summary` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/requests` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/overtime` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/allowances` | MỞ WEB ĐÃ KIỂM | NOT-IMPLEMENTED (UI) |
| `/decisions` | MỞ WEB ĐÃ KIỂM | NOT-IMPLEMENTED (UI) |
| `/payroll` | MỞ WEB ĐÃ KIỂM | NOT-IMPLEMENTED (UI) |
| `/payslips` | MỞ WEB ĐÃ KIỂM | NOT-IMPLEMENTED (UI) |
| `/reports` | MỞ WEB ĐÃ KIỂM | Đọc/mở trang; không mặc định CRUD đạt |
| `/forbidden` | NOT-RUN | Đọc/mở trang; không mặc định CRUD đạt |
| `/**` | 404 đã thử 2 URL | Đọc/mở trang; không mặc định CRUD đạt |

## Endpoint runtime

Quét không JWT và JWT sai trên từng method/path. ID thay bằng 999999999 để không động hồ sơ có sẵn. HTTP nghiệp vụ chỉ ở các request có bằng chứng riêng; danh sách endpoint không thay thế kiểm quyền mọi vai trò.

| Method | URL | Controller | Xác thực | Nghiệp vụ |
|---|---|---|---|---|
| GET | `/qualification-levels` | QualificationLevelController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/employees/{id}` | EmployeeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/{id}/detail` | EmployeeDetailController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/auth/change-password` | AuthController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/salary-components` | SalaryComponentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employee-decisions/{id}/approve` | EmployeeDecisionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/reports/payroll/export` | ReportExportController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/contract-types` | ContractTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/leave-types/{id}` | LeaveTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/admin/accounts/{accountId}/reset-password` | AdminAccountController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/audit-logs` | AuditLogController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/leave-entitlements/{id}` | LeaveEntitlementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/me/payslips` | PayrollController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/contracts/{contractId}/salary-terms/{id}/change-rate` | ContractSalaryTermController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/leave-entitlements/{id}` | LeaveEntitlementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/me/schedules` | ScheduleQueryController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/payroll-batches/draft` | PayrollBatchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/auth/login` | AuthController | Public hoặc không thuộc bộ quét | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/{employeeId}/allowances/{id}` | EmployeeAllowanceController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employee-requests/resignations/execute-due` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/employees/{employeeId}/allowances/{id}` | EmployeeAllowanceController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payroll-batches` | PayrollBatchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/positions` | PositionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/positions/search` | PositionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employee-requests/{id}/withdraw` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/contracts/{id}/terminate` | ContractController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-requests/balances/{employeeId}` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/leave-types/{id}` | LeaveTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/allowance-types` | AllowanceTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/leave-types/{id}` | LeaveTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/management/shifts` | WorkShiftManagementWriteController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/departments/{departmentId}/head` | DepartmentHeadController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/api/admin/accounts/{id}` | AdminAccountManagementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-decisions` | EmployeeDecisionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/employees/{id}/transfer` | EmployeeAssignmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/api/management/shifts/{id}` | WorkShiftManagementWriteController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/admin/permissions` | AdminPermissionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/positions` | PositionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/api/admin/hr-scopes/{id}` | HrDepartmentScopeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/departments/{departmentId}/head/end` | DepartmentHeadController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/departments/{departmentId}/head` | DepartmentHeadController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/management/schedules/search` | ScheduleQueryController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/positions/{id}` | PositionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employees/{employeeId}/allowances/{id}/end-from` | EmployeeAllowanceController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-assignments/search` | PersonnelSearchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/leave-types` | LeaveTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/{id}/assignments` | EmployeeAssignmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payroll-periods` | PayrollPeriodController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/contract-types` | ContractTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payroll-batches/period/{periodId}` | PayrollBatchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees` | EmployeeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/admin/accounts/{id}` | AdminAccountManagementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/management/shifts/{id}` | WorkShiftReadOnlyController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/departments` | DepartmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/{employeeId}/allowances` | EmployeeAllowanceController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/api/admin/accounts/{id}` | AdminAccountManagementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/admin/accounts` | AdminAccountManagementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/api/admin/roles/{id}/permissions` | AdminRoleController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payroll-batches/{batchId}/details` | PayrollBatchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/reports/contracts` | ContractStatisticsController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employees/{employeeId}/allowances/{id}/change-from` | EmployeeAllowanceController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/api/admin/roles/{id}` | AdminRoleController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/positions/{id}` | PositionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/employees/{id}` | EmployeeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/contracts/{contractId}/salary-terms/{id}` | ContractSalaryTermController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/reports/employees/export` | ReportExportController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/contracts/{contractId}/salary-terms` | ContractSalaryTermController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/departments/search` | DepartmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/departments` | DepartmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/leave-entitlements/employee/{employeeId}` | LeaveEntitlementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/contracts/{contractId}/salary-terms/{id}` | ContractSalaryTermController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/reports/contracts/export` | ReportExportController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/g1/audit` | G1AuditController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/salary-components` | SalaryComponentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-assignments` | EmployeeAssignmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/reports/employees` | EmployeeStatisticsController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/management/shifts/capabilities` | WorkShiftManagementWriteController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/contracts/{id}/renew` | ContractController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/allowance-types` | AllowanceTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payslips/{payslipId}/html` | PayslipController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/management/shifts` | WorkShiftReadOnlyController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employees/{id}/rehire` | EmployeeRehireController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-requests/{id}` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/admin/roles/{id}` | AdminRoleController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employees/{employeeId}/qualifications` | EmployeeQualificationController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/contracts/{contractId}/salary-terms/{id}` | ContractSalaryTermController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/allowance-types/{id}` | AllowanceTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/api/admin/roles/{id}` | AdminRoleController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employee-requests/leave` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/admin/hr-scopes` | HrDepartmentScopeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/reports/work/export` | ReportExportController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employee-requests/{id}/approve` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payslips/{payslipId}` | PayslipController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employee-decisions/{id}/reject` | EmployeeDecisionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/admin/hr-scopes/{id}/revoke` | HrDepartmentScopeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payslips/my-payslips` | PayslipController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/payroll-periods/{id}` | PayrollPeriodController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/hello` | HelloController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-qualifications/search` | PersonnelSearchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/payroll-batches/{batchId}/adjust` | PayrollBatchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/departments/{departmentId}/head/candidates` | DepartmentHeadController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/contracts/{id}` | ContractController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/admin/roles` | AdminRoleController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/positions/{id}` | PositionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/contracts/{contractId}/salary-terms` | ContractSalaryTermController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/leave-entitlements` | LeaveEntitlementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/allowance-types/{id}` | AllowanceTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/admin/permissions` | AdminPermissionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/qualification-levels` | QualificationLevelController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/{employeeId}/decision-payroll-items` | DecisionPayrollController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/departments/{id}` | DepartmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-requests/mine` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payroll-periods/{id}` | PayrollPeriodController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/allowance-types/{id}` | AllowanceTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/positions` | PositionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/payroll-batches/{batchId}/lock` | PayrollBatchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/leave-types` | LeaveTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-requests/leave-policies/{leaveTypeId}` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/contracts` | ContractController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employee-requests/resignation` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/employee-requests/leave-policies/{leaveTypeId}` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PATCH | `/api/management/shifts/{id}/deactivate` | WorkShiftManagementWriteController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/auth/me` | AuthController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PATCH | `/employees/{id}/contact` | EmployeeDetailController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/salary-components/{id}` | SalaryComponentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employees` | EmployeeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/reports/work` | WorkStatisticsController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-requests` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/departments/{id}` | DepartmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/admin/accounts` | AdminAccountManagementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/payroll-batches/{batchId}/publish` | PayrollBatchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/management/attendance` | AttendanceManagementReadOnlyController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employee-requests/{id}/reject` | EmployeeRequestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/departments/{departmentId}/head/history` | DepartmentHeadController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/contract-types/{id}` | ContractTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/employee-qualifications/{id}/primary` | EmployeeQualificationController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/qualification-levels/{id}` | QualificationLevelController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payroll-integration-test/attendance/{employeeId}` | PayrollIntegrationTestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees` | EmployeeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/api/admin/permissions/{id}` | AdminPermissionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/salary-components/{id}` | SalaryComponentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employee-decisions` | EmployeeDecisionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/api/admin/permissions/{id}` | AdminPermissionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/me/salary` | PayrollController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/{employeeId}/salary-rates` | ContractSalaryTermController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/api/auth/logout` | AuthController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/employee-decisions/{id}` | EmployeeDecisionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/employee-qualifications/{id}` | EmployeeQualificationController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/salary-components/{id}` | SalaryComponentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/{employeeId}/qualifications` | EmployeeQualificationController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/employee-decisions/{id}` | EmployeeDecisionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/payroll-batches/{batchId}/calculate` | PayrollBatchController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/g2/audit` | PersonnelAuditController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/personnel/audit` | PersonnelAuditController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/admin/hr-scopes` | HrDepartmentScopeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/payroll-integration-test/salary-profile/{employeeId}` | PayrollIntegrationTestController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/contract-types/{id}` | ContractTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/departments` | DepartmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/reports/payroll` | PayrollStatisticsController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/payroll-periods` | PayrollPeriodController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employees/{id}/terminate` | EmployeeTerminationController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/search` | EmployeeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/{employeeId}/allowance-calculations` | AllowanceCalculationController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/qualification-levels/{id}` | QualificationLevelController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/contracts` | ContractController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/contract-types/{id}` | ContractTypeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PATCH | `/api/management/shifts/{id}/activate` | WorkShiftManagementWriteController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/health` | HealthController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/health` | HealthController | Public hoặc không thuộc bộ quét | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/admin/permissions/{id}` | AdminPermissionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/qualification-levels/{id}` | QualificationLevelController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| POST | `/employees/{employeeId}/allowances` | EmployeeAllowanceController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/api/admin/roles` | AdminRoleController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-decisions/{id}` | EmployeeDecisionController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employee-qualifications/{id}` | EmployeeQualificationController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/departments/{id}` | DepartmentController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/employees/{id}` | EmployeeController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| GET | `/leave-entitlements` | LeaveEntitlementController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| DELETE | `/employees/{employeeId}/allowances/{id}` | EmployeeAllowanceController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
| PUT | `/employee-qualifications/{id}` | EmployeeQualificationController | 354/356 nhóm scan; xem bản ghi chi tiết | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |
