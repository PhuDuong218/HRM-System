# Phạm vi đã chạy

Không cộng số lượt HTTP/Java/viewport vào 218 ca nghiệp vụ. Một ca tổng hợp còn điều kiện chưa kiểm chứng được giữ NOT-RUN; probe liên quan ghi riêng.

## HTTP endpoint × actor × bằng chứng

| Probe | Method | Endpoint | Actor | HTTP | Kết quả |
|---|---|---|---|---:|---|
| AUTH-01-health | GET | `/api/health` | anonymous | 200 | PASS |
| AUTH-login-admin | POST | `/api/auth/login` | anonymous | 200 | PASS |
| AUTH-login-hrA | POST | `/api/auth/login` | anonymous | 200 | PASS |
| AUTH-login-hrB | POST | `/api/auth/login` | anonymous | 200 | PASS |
| AUTH-login-employeeA | POST | `/api/auth/login` | anonymous | 200 | PASS |
| AUTH-login-employeeB | POST | `/api/auth/login` | anonymous | 200 | PASS |
| AUTH-login-retired | POST | `/api/auth/login` | anonymous | 401 | PASS |
| AUTH-02-wrong | POST | `/api/auth/login` | anonymous | 401 | PASS |
| AUTH-03-missing | POST | `/api/auth/login` | anonymous | 400 | PASS |
| AUTH-04-invalid | GET | `/api/auth/me` | anonymous | 401 | PASS |
| AUTH-05-expired | GET | `/api/auth/me` | anonymous | 401 | PASS |
| RBAC-own-details-admin | GET | `/employees/2552744/detail` | admin | 200 | PASS |
| RBAC-own-details-hrA | GET | `/employees/2552744/detail` | hrA | 200 | PASS |
| RBAC-own-details-employeeA | GET | `/employees/2552744/detail` | employeeA | 200 | PASS |
| RBAC-other-employee | GET | `/employees/2552745/detail` | employeeA | 403 | PASS |
| RBAC-hr-outside | GET | `/employees/2552745/detail` | hrA | 403 | PASS |
| RBAC-hr-search | GET | `/employees/search?keyword=QA_20261031_190406_4398&page=0&size=100` | hrA | 200 | PASS |
| RBAC-employee-payroll-batches | GET | `/payroll-batches` | employeeA | 200 | FAIL |
| RBAC-employee-payroll-periods | GET | `/payroll-periods` | employeeA | 200 | FAIL |
| RBAC-employee-audit-logs | GET | `/audit-logs` | employeeA | 403 | PASS |
| RBAC-employee-api-admin-hr-scopes | GET | `/api/admin/hr-scopes` | employeeA | 403 | PASS |
| RBAC-employee-employee-assignments | GET | `/employee-assignments` | employeeA | 403 | PASS |
| RBAC-employee-api-management-shifts | GET | `/api/management/shifts` | employeeA | 200 | FAIL |
| RBAC-employee-api-management-schedules | GET | `/api/management/schedules` | employeeA | 401 | FAIL |
| RBAC-employee-api-management-attendance | GET | `/api/management/attendance` | employeeA | 401 | FAIL |
| RBAC-employee-api-reports-employees | GET | `/api/reports/employees` | employeeA | 403 | PASS |
| RBAC-anonymous-0 | GET | `/employees/2552744/salary-rates` | anonymous | 401 | PASS |
| RBAC-anonymous-1 | GET | `/departments/2552744/head/history` | anonymous | 401 | PASS |
| RBAC-anonymous-2 | GET | `/api/reports/work/export` | anonymous | 401 | PASS |
| RBAC-anonymous-3 | GET | `/positions` | anonymous | 401 | PASS |
| RBAC-anonymous-4 | GET | `/departments/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-5 | GET | `/employee-requests/balances/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-6 | GET | `/employees/2552744/detail` | anonymous | 401 | PASS |
| RBAC-anonymous-7 | GET | `/leave-types` | anonymous | 401 | PASS |
| RBAC-anonymous-8 | GET | `/payslips/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-9 | GET | `/contract-types/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-10 | GET | `/employees/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-11 | GET | `/departments/2552744/head` | anonymous | 401 | PASS |
| RBAC-anonymous-12 | GET | `/g1/audit` | anonymous | 401 | PASS |
| RBAC-anonymous-13 | GET | `/api/management/work-time-details/shifts/2552744/breaks` | anonymous | 401 | PASS |
| RBAC-anonymous-14 | GET | `/employee-requests` | anonymous | 401 | PASS |
| RBAC-anonymous-15 | GET | `/leave-types/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-16 | GET | `/payslips/my-payslips` | anonymous | 401 | PASS |
| RBAC-anonymous-17 | GET | `/qualification-levels` | anonymous | 401 | PASS |
| RBAC-anonymous-18 | GET | `/positions` | anonymous | 401 | PASS |
| RBAC-anonymous-19 | GET | `/positions/search` | anonymous | 401 | PASS |
| RBAC-anonymous-20 | GET | `/api/reports/contracts/export` | anonymous | 401 | PASS |
| RBAC-anonymous-21 | GET | `/departments/search` | anonymous | 401 | PASS |
| RBAC-anonymous-22 | GET | `/departments` | anonymous | 401 | PASS |
| RBAC-anonymous-23 | GET | `/employee-assignments` | anonymous | 401 | PASS |
| RBAC-anonymous-24 | GET | `/employee-qualifications/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-25 | GET | `/payroll-batches/2552744/details` | anonymous | 401 | PASS |
| RBAC-anonymous-26 | GET | `/payroll-batches` | anonymous | 401 | PASS |
| RBAC-anonymous-27 | GET | `/api/management/work-time-details/schedules/2552744/breaks` | anonymous | 401 | PASS |
| RBAC-anonymous-28 | GET | `/payslips/2552744/html` | anonymous | 401 | PASS |
| RBAC-anonymous-29 | GET | `/api/auth/me` | anonymous | 401 | PASS |
| RBAC-anonymous-30 | GET | `/allowance-types/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-31 | GET | `/api/overtime/requests` | anonymous | 401 | PASS |
| RBAC-anonymous-32 | GET | `/qualification-levels/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-33 | GET | `/api/management/shifts` | anonymous | 401 | PASS |
| RBAC-anonymous-34 | GET | `/contracts/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-35 | GET | `/employee-requests/leave-policies/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-36 | GET | `/employees/search` | anonymous | 401 | PASS |
| RBAC-anonymous-37 | GET | `/g2/audit` | anonymous | 401 | PASS |
| RBAC-anonymous-38 | GET | `/personnel/audit` | anonymous | 401 | PASS |
| RBAC-anonymous-39 | GET | `/departments/2552744/head/candidates` | anonymous | 401 | PASS |
| RBAC-anonymous-40 | GET | `/api/attendance/self/history` | anonymous | 401 | PASS |
| RBAC-anonymous-41 | GET | `/employees/2552744/allowances` | anonymous | 401 | PASS |
| RBAC-anonymous-42 | GET | `/api/management/attendance` | anonymous | 401 | PASS |
| RBAC-anonymous-43 | GET | `/api/admin/hr-scopes` | anonymous | 401 | PASS |
| RBAC-anonymous-44 | GET | `/api/management/workflow-requests/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-45 | GET | `/api/reports/contracts` | anonymous | 401 | PASS |
| RBAC-anonymous-46 | GET | `/api/overtime/workflows/requests/2552744/intervals` | anonymous | 401 | PASS |
| RBAC-anonymous-47 | GET | `/contracts/2552744/salary-terms` | anonymous | 401 | PASS |
| RBAC-anonymous-48 | GET | `/payroll-integration-test/attendance/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-49 | GET | `/api/management/shifts/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-50 | GET | `/payroll-periods` | anonymous | 401 | PASS |
| RBAC-anonymous-51 | GET | `/employees` | anonymous | 401 | PASS |
| RBAC-anonymous-52 | GET | `/employee-requests/mine` | anonymous | 401 | PASS |
| RBAC-anonymous-53 | GET | `/payroll-periods/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-54 | GET | `/api/reports/employees/export` | anonymous | 401 | PASS |
| RBAC-anonymous-55 | GET | `/payroll-integration-test/salary-profile/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-56 | GET | `/api/overtime/requests/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-57 | GET | `/employees/2552744/qualifications` | anonymous | 401 | PASS |
| RBAC-anonymous-58 | GET | `/health` | anonymous | 401 | PASS |
| RBAC-anonymous-59 | GET | `/employees/2552744/assignments` | anonymous | 401 | PASS |
| RBAC-anonymous-60 | GET | `/payroll-batches/period/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-61 | GET | `/allowance-types` | anonymous | 401 | PASS |
| RBAC-anonymous-62 | GET | `/contract-types` | anonymous | 401 | PASS |
| RBAC-anonymous-63 | GET | `/leave-entitlements/employee/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-64 | GET | `/salary-components/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-65 | GET | `/api/me/payslips` | anonymous | 401 | PASS |
| RBAC-anonymous-66 | GET | `/departments` | anonymous | 401 | PASS |
| RBAC-anonymous-67 | GET | `/api/me/salary` | anonymous | 401 | PASS |
| RBAC-anonymous-68 | GET | `/api/management/schedules/search` | anonymous | 401 | PASS |
| RBAC-anonymous-69 | GET | `/employee-requests/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-70 | GET | `/employees/2552744/allowance-calculations` | anonymous | 401 | PASS |
| RBAC-anonymous-71 | GET | `/api/overtime/payroll-inputs` | anonymous | 401 | PASS |
| RBAC-anonymous-72 | GET | `/api/management/schedules/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-73 | GET | `/api/reports/work` | anonymous | 401 | PASS |
| RBAC-anonymous-74 | GET | `/api/reports/employees` | anonymous | 401 | PASS |
| RBAC-anonymous-75 | GET | `/salary-components` | anonymous | 401 | PASS |
| RBAC-anonymous-76 | GET | `/employees/2552744/decision-payroll-items` | anonymous | 401 | PASS |
| RBAC-anonymous-77 | GET | `/api/reports/payroll` | anonymous | 401 | PASS |
| RBAC-anonymous-78 | GET | `/contracts` | anonymous | 401 | PASS |
| RBAC-anonymous-79 | GET | `/audit-logs` | anonymous | 401 | PASS |
| RBAC-anonymous-80 | GET | `/api/management/workflow-requests` | anonymous | 401 | PASS |
| RBAC-anonymous-81 | GET | `/leave-entitlements` | anonymous | 401 | PASS |
| RBAC-anonymous-82 | GET | `/positions/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-83 | GET | `/employees/2552744/allowances/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-84 | GET | `/leave-entitlements/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-85 | GET | `/api/management/attendance/confirmed-summary` | anonymous | 401 | PASS |
| RBAC-anonymous-86 | GET | `/employee-decisions` | anonymous | 401 | PASS |
| RBAC-anonymous-87 | GET | `/api/management/schedules` | anonymous | 401 | PASS |
| RBAC-anonymous-88 | GET | `/contracts/2552744/salary-terms/2552744` | anonymous | 401 | PASS |
| RBAC-anonymous-89 | GET | `/api/me/schedules` | anonymous | 401 | PASS |
| RBAC-anonymous-90 | GET | `/api/reports/payroll/export` | anonymous | 401 | PASS |
| RBAC-anonymous-91 | GET | `/employees` | anonymous | 401 | PASS |
| RBAC-anonymous-92 | GET | `/employee-decisions/2552744` | anonymous | 401 | PASS |
| PAY-period-QA | POST | `/payroll-periods` | admin | 201 | PASS |
| PAY-employee-create-draft | POST | `/payroll-batches/draft` | employeeA | 201 | FAIL |
| PAY-other-details | GET | `/payroll-batches/40/details` | employeeA | 200 | FAIL |
| PAY-employee-lock-spoof | POST | `/payroll-batches/40/lock?actorAccountId=2588` | employeeA | 500 | FAIL |

## Toàn bộ mapping runtime: mức bằng chứng

Anonymous sweep xác nhận yêu cầu đăng nhập; không xác nhận quyền từng role hoặc nội dung nghiệp vụ. Route có biến path cần xem probe concrete tương ứng.

| Controller | Method | Mapping | Mức kiểm chứng |
|---|---|---|---|
| DetailedBreakQueryController | GET | `/api/management/work-time-details/schedules/{id}/breaks` | Có anonymous sweep; authenticated workflow NOT-RUN |
| PayrollPeriodController | POST | `/payroll-periods` | Có HTTP probe trực tiếp |
| AttendanceSelfClockController | POST | `/api/attendance/self/{scheduleId}/check-in` | NOT-RUN hoặc xem probe concrete |
| PayslipController | GET | `/payslips/{payslipId}/html` | Có anonymous sweep; authenticated workflow NOT-RUN |
| EmployeeQualificationController | PUT | `/employee-qualifications/{id}/primary` | NOT-RUN hoặc xem probe concrete |
| EmployeeRequestController | PUT | `/employee-requests/leave-policies/{leaveTypeId}` | NOT-RUN hoặc xem probe concrete |
| ContractSalaryTermController | DELETE | `/contracts/{contractId}/salary-terms/{id}` | NOT-RUN hoặc xem probe concrete |
| AuthController | GET | `/api/auth/me` | Có HTTP probe trực tiếp |
| OvertimeController | GET | `/api/overtime/requests` | Có HTTP probe trực tiếp |
| QualificationLevelController | GET | `/qualification-levels/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| EmployeeQualificationController | POST | `/employees/{employeeId}/qualifications` | NOT-RUN hoặc xem probe concrete |
| EmployeeDecisionController | DELETE | `/employee-decisions/{id}` | NOT-RUN hoặc xem probe concrete |
| EmployeeRehireController | POST | `/employees/{id}/rehire` | NOT-RUN hoặc xem probe concrete |
| DepartmentController | DELETE | `/departments/{id}` | NOT-RUN hoặc xem probe concrete |
| ContractController | GET | `/contracts/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| ScheduleChangeRequestController | POST | `/api/management/schedule-change-requests` | NOT-RUN hoặc xem probe concrete |
| EmployeeRequestController | POST | `/employee-requests/resignations/execute-due` | NOT-RUN hoặc xem probe concrete |
| EmployeeRequestController | POST | `/employee-requests/{id}/withdraw` | NOT-RUN hoặc xem probe concrete |
| EmployeeRequestController | POST | `/employee-requests/{id}/reject` | NOT-RUN hoặc xem probe concrete |
| EmployeeRequestController | GET | `/employee-requests/leave-policies/{leaveTypeId}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| ContractSalaryTermController | POST | `/contracts/{contractId}/salary-terms` | NOT-RUN hoặc xem probe concrete |
| EmployeeController | GET | `/employees/search` | Có HTTP probe trực tiếp |
| PersonnelAuditController | GET | `/g2/audit` | Có HTTP probe trực tiếp |
| PersonnelAuditController | GET | `/personnel/audit` | Có HTTP probe trực tiếp |
| AttendanceCorrectionRequestController | POST | `/api/management/attendance-correction-requests/{id}/decision` | NOT-RUN hoặc xem probe concrete |
| HrDepartmentScopeController | POST | `/api/admin/hr-scopes/{id}/revoke` | NOT-RUN hoặc xem probe concrete |
| OvertimeController | POST | `/api/overtime/requests/{id}/reject` | NOT-RUN hoặc xem probe concrete |
| DepartmentHeadController | GET | `/departments/{departmentId}/head/candidates` | Có anonymous sweep; authenticated workflow NOT-RUN |
| ContractTypeController | DELETE | `/contract-types/{id}` | NOT-RUN hoặc xem probe concrete |
| ContractSalaryTermController | POST | `/contracts/{contractId}/salary-terms/{id}/change-rate` | NOT-RUN hoặc xem probe concrete |
| ContractController | POST | `/contracts/{id}/terminate` | NOT-RUN hoặc xem probe concrete |
| AttendanceSelfHistoryController | GET | `/api/attendance/self/history` | Có HTTP probe trực tiếp |
| EmployeeQualificationController | DELETE | `/employee-qualifications/{id}` | NOT-RUN hoặc xem probe concrete |
| EmployeeAllowanceController | GET | `/employees/{employeeId}/allowances` | Có anonymous sweep; authenticated workflow NOT-RUN |
| LeaveTypeController | DELETE | `/leave-types/{id}` | NOT-RUN hoặc xem probe concrete |
| AttendanceManagementReadOnlyController | GET | `/api/management/attendance` | Có HTTP probe trực tiếp |
| HrDepartmentScopeController | GET | `/api/admin/hr-scopes` | Có HTTP probe trực tiếp |
| AllowanceTypeController | POST | `/allowance-types` | NOT-RUN hoặc xem probe concrete |
| WorkShiftController | PUT | `/api/management/shifts/{id}` | NOT-RUN hoặc xem probe concrete |
| WorkflowRequestQueryController | GET | `/api/management/workflow-requests/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| ContractTypeController | POST | `/contract-types` | NOT-RUN hoặc xem probe concrete |
| ContractStatisticsController | GET | `/api/reports/contracts` | Có HTTP probe trực tiếp |
| EmployeeController | DELETE | `/employees/{id}` | NOT-RUN hoặc xem probe concrete |
| PayrollPeriodController | PUT | `/payroll-periods/{id}` | NOT-RUN hoặc xem probe concrete |
| OvertimeController | POST | `/api/overtime/requests/{id}/approve` | NOT-RUN hoặc xem probe concrete |
| SalaryComponentController | POST | `/salary-components` | NOT-RUN hoặc xem probe concrete |
| AllowanceTypeController | DELETE | `/allowance-types/{id}` | NOT-RUN hoặc xem probe concrete |
| OvertimeWorkflowController | GET | `/api/overtime/workflows/requests/{requestId}/intervals` | Có anonymous sweep; authenticated workflow NOT-RUN |
| AuthController | POST | `/api/auth/login` | Có HTTP probe trực tiếp |
| QualificationLevelController | DELETE | `/qualification-levels/{id}` | NOT-RUN hoặc xem probe concrete |
| OvertimeController | PUT | `/api/overtime/requests/{id}` | NOT-RUN hoặc xem probe concrete |
| ContractSalaryTermController | GET | `/contracts/{contractId}/salary-terms` | Có anonymous sweep; authenticated workflow NOT-RUN |
| DepartmentController | POST | `/departments` | NOT-RUN hoặc xem probe concrete |
| PayrollIntegrationTestController | GET | `/payroll-integration-test/attendance/{employeeId}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| WorkShiftController | GET | `/api/management/shifts/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| ContractController | POST | `/contracts` | NOT-RUN hoặc xem probe concrete |
| AttendanceCorrectionRequestController | POST | `/api/management/attendance-correction-requests` | NOT-RUN hoặc xem probe concrete |
| PayrollPeriodController | GET | `/payroll-periods` | Có HTTP probe trực tiếp |
| EmployeeRequestController | GET | `/employee-requests/mine` | Có HTTP probe trực tiếp |
| EmployeeController | GET | `/employees` | Có HTTP probe trực tiếp |
| ReportExportController | GET | `/api/reports/employees/export` | Có HTTP probe trực tiếp |
| PayrollPeriodController | GET | `/payroll-periods/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| LeaveEntitlementController | POST | `/leave-entitlements` | NOT-RUN hoặc xem probe concrete |
| LeaveEntitlementController | GET | `/leave-entitlements/employee/{employeeId}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| OvertimeController | GET | `/api/overtime/requests/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| PayrollIntegrationTestController | GET | `/payroll-integration-test/salary-profile/{employeeId}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| EmployeeAllowanceController | POST | `/employees/{employeeId}/allowances/{id}/change-from` | NOT-RUN hoặc xem probe concrete |
| EmployeeTerminationController | POST | `/employees/{id}/terminate` | NOT-RUN hoặc xem probe concrete |
| WorkScheduleController | POST | `/api/management/schedules` | NOT-RUN hoặc xem probe concrete |
| EmployeeDecisionController | POST | `/employee-decisions` | NOT-RUN hoặc xem probe concrete |
| EmployeeQualificationController | GET | `/employees/{employeeId}/qualifications` | Có anonymous sweep; authenticated workflow NOT-RUN |
| EmployeeRequestController | POST | `/employee-requests/leave` | NOT-RUN hoặc xem probe concrete |
| AttendanceSelfClockController | POST | `/api/attendance/self/{scheduleId}/check-out` | NOT-RUN hoặc xem probe concrete |
| HealthController | GET | `/health` | Có HTTP probe trực tiếp |
| HealthController | GET | `/api/health` | Có HTTP probe trực tiếp |
| EmployeeAssignmentController | GET | `/employees/{id}/assignments` | Có anonymous sweep; authenticated workflow NOT-RUN |
| AdminAccountController | POST | `/api/admin/accounts/{accountId}/reset-password` | NOT-RUN hoặc xem probe concrete |
| SalaryComponentController | PUT | `/salary-components/{id}` | NOT-RUN hoặc xem probe concrete |
| PayrollBatchController | GET | `/payroll-batches/period/{periodId}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| AllowanceTypeController | GET | `/allowance-types` | Có HTTP probe trực tiếp |
| PayrollBatchController | POST | `/payroll-batches/draft` | Có HTTP probe trực tiếp |
| OvertimeWorkflowController | POST | `/api/overtime/workflows/requests/{requestId}/decision` | NOT-RUN hoặc xem probe concrete |
| ContractTypeController | GET | `/contract-types` | Có HTTP probe trực tiếp |
| OvertimePayrollInputController | GET | `/api/overtime/payroll-inputs` | Có HTTP probe trực tiếp |
| DepartmentHeadController | POST | `/departments/{departmentId}/head/end` | NOT-RUN hoặc xem probe concrete |
| EmployeeDecisionController | POST | `/employee-decisions/{id}/reject` | NOT-RUN hoặc xem probe concrete |
| ScheduleChangeRequestController | POST | `/api/management/schedule-change-requests/{id}/decision` | NOT-RUN hoặc xem probe concrete |
| EmployeeDecisionController | POST | `/employee-decisions/{id}/approve` | NOT-RUN hoặc xem probe concrete |
| SalaryComponentController | GET | `/salary-components/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| PayrollController | GET | `/api/me/payslips` | Có HTTP probe trực tiếp |
| DetailedWorkTimeController | POST | `/api/management/work-time-details/shifts` | NOT-RUN hoặc xem probe concrete |
| WorkScheduleController | PUT | `/api/management/schedules/{id}` | NOT-RUN hoặc xem probe concrete |
| ContractController | GET | `/contracts` | Có HTTP probe trực tiếp |
| DepartmentController | GET | `/departments` | Có HTTP probe trực tiếp |
| PayrollController | GET | `/api/me/salary` | Có HTTP probe trực tiếp |
| EmployeeRequestController | GET | `/employee-requests/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| DetailedWorkTimeController | PUT | `/api/management/work-time-details/schedules/{id}` | NOT-RUN hoặc xem probe concrete |
| PositionController | POST | `/positions` | NOT-RUN hoặc xem probe concrete |
| ScheduleQueryController | GET | `/api/management/schedules/search` | Có HTTP probe trực tiếp |
| OvertimeController | POST | `/api/overtime/requests/{id}/overflow-review` | NOT-RUN hoặc xem probe concrete |
| AllowanceCalculationController | GET | `/employees/{employeeId}/allowance-calculations` | Có anonymous sweep; authenticated workflow NOT-RUN |
| EmployeeAllowanceController | GET | `/employees/{employeeId}/allowances/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| EmployeeDetailController | PATCH | `/employees/{id}/contact` | NOT-RUN hoặc xem probe concrete |
| PayrollBatchController | POST | `/payroll-batches/{batchId}/publish` | NOT-RUN hoặc xem probe concrete |
| WorkStatisticsController | GET | `/api/reports/work` | Có HTTP probe trực tiếp |
| WorkScheduleController | GET | `/api/management/schedules/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| EmployeeAssignmentController | PUT | `/employees/{id}/transfer` | NOT-RUN hoặc xem probe concrete |
| EmployeeStatisticsController | GET | `/api/reports/employees` | Có HTTP probe trực tiếp |
| SalaryComponentController | GET | `/salary-components` | Có HTTP probe trực tiếp |
| DecisionPayrollController | GET | `/employees/{employeeId}/decision-payroll-items` | Có anonymous sweep; authenticated workflow NOT-RUN |
| LeaveEntitlementController | PUT | `/leave-entitlements/{id}` | NOT-RUN hoặc xem probe concrete |
| PositionController | PUT | `/positions/{id}` | NOT-RUN hoặc xem probe concrete |
| QualificationLevelController | POST | `/qualification-levels` | NOT-RUN hoặc xem probe concrete |
| EmployeeAllowanceController | PUT | `/employees/{employeeId}/allowances/{id}` | NOT-RUN hoặc xem probe concrete |
| OvertimeWorkflowController | POST | `/api/overtime/workflows/ot/{id}/overflow-request` | NOT-RUN hoặc xem probe concrete |
| PayrollBatchController | POST | `/payroll-batches/{batchId}/calculate` | NOT-RUN hoặc xem probe concrete |
| PayrollStatisticsController | GET | `/api/reports/payroll` | Có HTTP probe trực tiếp |
| LeaveTypeController | POST | `/leave-types` | NOT-RUN hoặc xem probe concrete |
| ContractSalaryTermController | PUT | `/contracts/{contractId}/salary-terms/{id}` | NOT-RUN hoặc xem probe concrete |
| WorkflowRequestQueryController | GET | `/api/management/workflow-requests` | Có HTTP probe trực tiếp |
| AuditLogController | GET | `/audit-logs` | Có HTTP probe trực tiếp |
| LeaveEntitlementController | GET | `/leave-entitlements` | Có HTTP probe trực tiếp |
| EmployeeController | POST | `/employees` | NOT-RUN hoặc xem probe concrete |
| WorkShiftController | DELETE | `/api/management/shifts/{id}` | NOT-RUN hoặc xem probe concrete |
| OvertimePreviewController | POST | `/api/overtime/requests/{id}/preview` | NOT-RUN hoặc xem probe concrete |
| EmployeeDecisionController | PUT | `/employee-decisions/{id}` | NOT-RUN hoặc xem probe concrete |
| LeaveEntitlementController | GET | `/leave-entitlements/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| AuthController | POST | `/api/auth/change-password` | NOT-RUN hoặc xem probe concrete |
| PositionController | GET | `/positions/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| ConfirmedAttendancePeriodController | GET | `/api/management/attendance/confirmed-summary` | Có HTTP probe trực tiếp |
| EmployeeDecisionController | GET | `/employee-decisions` | Có HTTP probe trực tiếp |
| WorkScheduleController | GET | `/api/management/schedules` | Có HTTP probe trực tiếp |
| OvertimeController | PUT | `/api/overtime/requests/{id}/actual` | NOT-RUN hoặc xem probe concrete |
| DetailedWorkTimeController | PUT | `/api/management/work-time-details/shifts/{id}` | NOT-RUN hoặc xem probe concrete |
| ReportExportController | GET | `/api/reports/payroll/export` | Có HTTP probe trực tiếp |
| ScheduleQueryController | GET | `/api/me/schedules` | Có HTTP probe trực tiếp |
| ContractSalaryTermController | GET | `/contracts/{contractId}/salary-terms/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| HelloController | GET | `/hello` | NOT-RUN hoặc xem probe concrete |
| DepartmentController | PUT | `/departments/{id}` | NOT-RUN hoặc xem probe concrete |
| EmployeeRequestController | POST | `/employee-requests/{id}/approve` | NOT-RUN hoặc xem probe concrete |
| OvertimeController | POST | `/api/overtime/requests/{id}/cancel` | NOT-RUN hoặc xem probe concrete |
| PayrollBatchController | POST | `/payroll-batches/{batchId}/lock` | NOT-RUN hoặc xem probe concrete |
| EmployeeController | GET | `/employees` | Có HTTP probe trực tiếp |
| EmployeeDecisionController | GET | `/employee-decisions/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| OvertimeController | POST | `/api/overtime/requests/{id}/reopen-confirmation` | NOT-RUN hoặc xem probe concrete |
| EmployeeController | PUT | `/employees/{id}` | NOT-RUN hoặc xem probe concrete |
| ContractSalaryTermController | GET | `/employees/{employeeId}/salary-rates` | Có anonymous sweep; authenticated workflow NOT-RUN |
| ContractTypeController | PUT | `/contract-types/{id}` | NOT-RUN hoặc xem probe concrete |
| DepartmentHeadController | PUT | `/departments/{departmentId}/head` | NOT-RUN hoặc xem probe concrete |
| ReportExportController | GET | `/api/reports/work/export` | Có HTTP probe trực tiếp |
| EmployeeRequestController | GET | `/employee-requests/balances/{employeeId}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| PositionController | GET | `/positions` | Có HTTP probe trực tiếp |
| DepartmentHeadController | GET | `/departments/{departmentId}/head/history` | Có anonymous sweep; authenticated workflow NOT-RUN |
| DepartmentController | GET | `/departments/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| ContractController | POST | `/contracts/{id}/renew` | NOT-RUN hoặc xem probe concrete |
| WorkShiftController | POST | `/api/management/shifts` | NOT-RUN hoặc xem probe concrete |
| LeaveTypeController | PUT | `/leave-types/{id}` | NOT-RUN hoặc xem probe concrete |
| PositionController | GET | `/positions` | Có HTTP probe trực tiếp |
| PositionController | GET | `/positions/search` | Có HTTP probe trực tiếp |
| EmployeeDetailController | GET | `/employees/{id}/detail` | Có anonymous sweep; authenticated workflow NOT-RUN |
| LeaveTypeController | GET | `/leave-types` | Có HTTP probe trực tiếp |
| PayslipController | GET | `/payslips/{payslipId}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| EmployeeRequestController | POST | `/employee-requests/resignation` | NOT-RUN hoặc xem probe concrete |
| SalaryComponentController | DELETE | `/salary-components/{id}` | NOT-RUN hoặc xem probe concrete |
| G1AuditController | GET | `/g1/audit` | Có HTTP probe trực tiếp |
| DetailedWorkTimeController | POST | `/api/management/work-time-details/schedules` | NOT-RUN hoặc xem probe concrete |
| ContractTypeController | GET | `/contract-types/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| AttendanceController | POST | `/api/attendance/{id}/unconfirm` | NOT-RUN hoặc xem probe concrete |
| DepartmentHeadController | GET | `/departments/{departmentId}/head` | Có anonymous sweep; authenticated workflow NOT-RUN |
| EmployeeController | GET | `/employees/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| OvertimeWorkflowController | POST | `/api/overtime/workflows/ot/{id}/cancel-request` | NOT-RUN hoặc xem probe concrete |
| EmployeeRequestController | GET | `/employee-requests` | Có HTTP probe trực tiếp |
| EmployeeQualificationController | GET | `/employee-qualifications/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| DetailedBreakQueryController | GET | `/api/management/work-time-details/shifts/{id}/breaks` | Có anonymous sweep; authenticated workflow NOT-RUN |
| LeaveTypeController | GET | `/leave-types/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| OvertimeController | POST | `/api/overtime/requests` | NOT-RUN hoặc xem probe concrete |
| AuthController | POST | `/api/auth/logout` | NOT-RUN hoặc xem probe concrete |
| AttendanceController | POST | `/api/attendance/manual` | NOT-RUN hoặc xem probe concrete |
| PayslipController | GET | `/payslips/my-payslips` | Có HTTP probe trực tiếp |
| EmployeeAllowanceController | POST | `/employees/{employeeId}/allowances` | NOT-RUN hoặc xem probe concrete |
| EmployeeQualificationController | PUT | `/employee-qualifications/{id}` | NOT-RUN hoặc xem probe concrete |
| EmployeeAllowanceController | DELETE | `/employees/{employeeId}/allowances/{id}` | NOT-RUN hoặc xem probe concrete |
| EmployeeAllowanceController | POST | `/employees/{employeeId}/allowances/{id}/end-from` | NOT-RUN hoặc xem probe concrete |
| QualificationLevelController | GET | `/qualification-levels` | Có HTTP probe trực tiếp |
| OvertimeController | POST | `/api/overtime/requests/{id}/confirm` | NOT-RUN hoặc xem probe concrete |
| DepartmentController | GET | `/departments/search` | Có HTTP probe trực tiếp |
| DepartmentController | GET | `/departments` | Có HTTP probe trực tiếp |
| ReportExportController | GET | `/api/reports/contracts/export` | Có HTTP probe trực tiếp |
| HrDepartmentScopeController | POST | `/api/admin/hr-scopes` | NOT-RUN hoặc xem probe concrete |
| EmployeeAssignmentController | GET | `/employee-assignments` | Có HTTP probe trực tiếp |
| AllowanceTypeController | GET | `/allowance-types/{id}` | Có anonymous sweep; authenticated workflow NOT-RUN |
| AttendanceController | PUT | `/api/attendance/{id}/correct` | NOT-RUN hoặc xem probe concrete |
| PayrollBatchController | POST | `/payroll-batches/{batchId}/adjust` | NOT-RUN hoặc xem probe concrete |
| PositionController | DELETE | `/positions/{id}` | NOT-RUN hoặc xem probe concrete |
| HrDepartmentScopeController | PUT | `/api/admin/hr-scopes/{id}` | NOT-RUN hoặc xem probe concrete |
| WorkShiftController | GET | `/api/management/shifts` | Có HTTP probe trực tiếp |
| PayrollBatchController | GET | `/payroll-batches/{batchId}/details` | Có anonymous sweep; authenticated workflow NOT-RUN |
| PayrollBatchController | GET | `/payroll-batches` | Có HTTP probe trực tiếp |
| AttendanceController | POST | `/api/attendance/{id}/confirm` | NOT-RUN hoặc xem probe concrete |
| AllowanceTypeController | PUT | `/allowance-types/{id}` | NOT-RUN hoặc xem probe concrete |
| QualificationLevelController | PUT | `/qualification-levels/{id}` | NOT-RUN hoặc xem probe concrete |

## Web responsive

26 route × CSS 1280,768,390,320 = 104 lượt. documentElement.scrollWidth <= innerWidth ở từng lượt. Không đảm bảo mọi nút/form đều không cắt chữ; không phải test app mobile. Có kiểm tra thao tác mở/đóng menu 320 px, chuyển VI/EN trang nhân viên. Bảng dữ liệu vẫn cuộn ngang bên trong theo thiết kế.

## Những phần chưa đủ bằng chứng

- CRUD/validation toàn bộ form, tất cả tổ hợp quyền ADMIN/HR/DEPARTMENT_HEAD/EMPLOYEE; HR ngoài scope chỉ kiểm một số endpoint.
- Token đổi mật khẩu/khóa giữa phiên, logout revocation tất cả tình huống, brute force/rate limit.
- Pipeline lương SQL đầy đủ, lock/publish/adjust và trigger tiền lương ở mọi loại bảng.
- Job chạy hai lần/nhiều instance, đổi ngày/múi giờ thực tế và race mỗi workflow. Có 2 test concurrency hiện có PASS không bao phủ tất cả race.
- Tải lớn, SLA p95/p99, export injection, accessibility và tất cả thông báo VI/EN.
- Bootstrap database sạch từ migration và restore drill. Không drop/reset DB test hiện tại để giả lập.

Xem test-cases.csv để theo dõi 218 tiêu chí chưa nghiệm thu đầy đủ.

## HTTP retest sau bản sửa được phép

| Probe | HTTP | Kết quả |
|---|---:|---|
| RBAC-employee-api-management-shifts | 200 | FAIL |
| RBAC-employee-api-management-schedules | 401 | FAIL |
| RBAC-employee-api-management-attendance | 401 | FAIL |
| PAY-employee-create-draft | 403 | PASS |
| PAY-admin-create-draft | 201 | PASS |
| PAY-other-details | 403 | PASS |
| PAY-employee-lock-spoof | 403 | PASS |
