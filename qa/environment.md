# QA environment baseline

Recorded: 2026-10-31T18:41:07.210267+07:00

Branch: Duong

HEAD: ec42ca8d106903a69ba9cba7a3ffe015af53c2f4

Unstaged diff SHA-256: b0274e1b4a0e0831bb1faf36b2e8d97482195eee1bf588dcd105a853848e4701

Staged diff SHA-256: 1be0f49e6f5a8d2b7f15dd087c5a69fa02a22ea700ca6bc4379de113af7842a8

No branch changes, reset, stash, merge, commit or push authorized. ZIP equivalence unverified. Credentials and raw diffs are not exported. Untracked source is part of the tested working tree; its hashes are recorded separately.

## Status
```text
A  HRM_System/src/main/java/com/hrm/controller/AttendanceManagementReadOnlyController.java
 M HRM_System/src/main/java/com/hrm/controller/EmployeeController.java
 M HRM_System/src/main/java/com/hrm/controller/EmployeeDetailController.java
A  HRM_System/src/main/java/com/hrm/controller/WorkShiftManagementWriteController.java
A  HRM_System/src/main/java/com/hrm/controller/WorkShiftReadOnlyController.java
 M HRM_System/src/main/java/com/hrm/repository/DepartmentRepository.java
 M HRM_System/src/main/java/com/hrm/repository/EmployeeAssignmentRepository.java
 M HRM_System/src/main/java/com/hrm/repository/EmployeeRepository.java
 M HRM_System/src/main/java/com/hrm/repository/LeaveEntitlementRepository.java
A  HRM_System/src/main/java/com/hrm/service/AttendanceManagementReadOnlyQueryService.java
 M HRM_System/src/main/java/com/hrm/service/DepartmentService.java
 M HRM_System/src/main/java/com/hrm/service/EmployeeAssignmentService.java
 M HRM_System/src/main/java/com/hrm/service/EmployeeRequestService.java
 M HRM_System/src/main/java/com/hrm/service/EmployeeService.java
 M HRM_System/src/main/java/com/hrm/service/LeaveEntitlementService.java
A  HRM_System/src/main/java/com/hrm/service/WorkShiftManagementWriteService.java
M  HRM_System/src/main/java/com/hrm/service/WorkShiftPermissionService.java
 M frontend/KIEM_TRA.md
MM frontend/proxy.conf.json
M  frontend/src/app/app.routes.ts
 M frontend/src/app/layout/admin-layout.css
MM frontend/src/app/layout/admin-layout.html
 M frontend/src/app/layout/admin-layout.ts
MM frontend/src/app/navigation.ts
AM frontend/src/app/pages/attendance-summary/attendance-summary.html
AM frontend/src/app/pages/attendance-summary/attendance-summary.ts
AM frontend/src/app/pages/attendance/attendance.html
AM frontend/src/app/pages/attendance/attendance.ts
 M frontend/src/app/pages/catalog/catalog.html
 M frontend/src/app/pages/catalog/catalog.ts
 M frontend/src/app/pages/connection/connection.html
 M frontend/src/app/pages/connection/connection.ts
 M frontend/src/app/pages/contract-types/contract-types.html
 M frontend/src/app/pages/contract-types/contract-types.ts
 M frontend/src/app/pages/contracts/contracts.html
 M frontend/src/app/pages/contracts/contracts.ts
 M frontend/src/app/pages/departments/departments.html
 M frontend/src/app/pages/departments/departments.ts
 M frontend/src/app/pages/employees/employees.html
 M frontend/src/app/pages/employees/employees.ts
 M frontend/src/app/pages/login/login.html
 M frontend/src/app/pages/login/login.ts
AM frontend/src/app/pages/overtime/overtime.html
AM frontend/src/app/pages/overtime/overtime.ts
 M frontend/src/app/pages/overview/overview.html
 M frontend/src/app/pages/personnel-workspace/personnel-workspace.html
 M frontend/src/app/pages/personnel-workspace/personnel-workspace.ts
 M frontend/src/app/pages/placeholder/placeholder.html
 M frontend/src/app/pages/placeholder/placeholder.ts
AM frontend/src/app/pages/schedules/schedules.html
AM frontend/src/app/pages/schedules/schedules.ts
A  frontend/src/app/pages/work-module.css
AM frontend/src/app/pages/work-shifts/work-shifts.html
AM frontend/src/app/pages/work-shifts/work-shifts.ts
 M frontend/src/app/services/employee.service.ts
 M frontend/src/app/services/language.service.ts
A  frontend/src/app/services/work-module-api.service.ts
 M frontend/src/app/shared/pagination.ts
 M frontend/src/app/shared/record-workspace.ts
 M frontend/src/styles.css
?? HRM_System/src/main/java/com/hrm/config/PersonnelPolicyWebConfiguration.java
?? HRM_System/src/main/java/com/hrm/config/PersonnelPrivacyAdvice.java
?? HRM_System/src/main/java/com/hrm/service/PersonnelScopeService.java
?? HRM_System/src/test/java/com/hrm/security/PersonnelScopePrivacyIntegrationTest.java
?? frontend/src/app/services/admin.guard.ts
?? frontend/src/app/services/ui-translations.ts
?? frontend/src/app/shared/management-page.base.ts
?? frontend/src/app/shared/management-page.css
?? frontend/src/app/shared/page-header.ts
?? frontend/src/app/shared/translate.pipe.ts

```
## Diff stat
```text
 .../com/hrm/controller/EmployeeController.java     |   4 +-
 .../hrm/controller/EmployeeDetailController.java   |   3 +-
 .../com/hrm/repository/DepartmentRepository.java   |  10 ++
 .../repository/EmployeeAssignmentRepository.java   |   2 +
 .../com/hrm/repository/EmployeeRepository.java     |  49 ++++++
 .../hrm/repository/LeaveEntitlementRepository.java |  36 +++++
 .../java/com/hrm/service/DepartmentService.java    |  10 +-
 .../com/hrm/service/EmployeeAssignmentService.java |   6 +-
 .../com/hrm/service/EmployeeRequestService.java    |   3 +
 .../main/java/com/hrm/service/EmployeeService.java |   9 +-
 .../com/hrm/service/LeaveEntitlementService.java   |   3 +
 frontend/KIEM_TRA.md                               |  76 ++++++++-
 frontend/proxy.conf.json                           |  36 ++++-
 frontend/src/app/layout/admin-layout.css           |   7 +
 frontend/src/app/layout/admin-layout.html          |   4 +-
 frontend/src/app/layout/admin-layout.ts            |   8 +-
 frontend/src/app/navigation.ts                     |   6 +-
 .../attendance-summary/attendance-summary.html     |  43 +++--
 .../pages/attendance-summary/attendance-summary.ts |   3 +-
 frontend/src/app/pages/attendance/attendance.html  |  29 ++--
 frontend/src/app/pages/attendance/attendance.ts    |   3 +-
 frontend/src/app/pages/catalog/catalog.html        |  34 ++--
 frontend/src/app/pages/catalog/catalog.ts          |  15 +-
 frontend/src/app/pages/connection/connection.html  |   8 +-
 frontend/src/app/pages/connection/connection.ts    |   3 +-
 .../app/pages/contract-types/contract-types.html   |  59 +++----
 .../src/app/pages/contract-types/contract-types.ts |   3 +-
 frontend/src/app/pages/contracts/contracts.html    | 179 ++++++++++-----------
 frontend/src/app/pages/contracts/contracts.ts      |   3 +-
 .../src/app/pages/departments/departments.html     |  51 +++---
 frontend/src/app/pages/departments/departments.ts  |   9 +-
 frontend/src/app/pages/employees/employees.html    |  52 +++---
 frontend/src/app/pages/employees/employees.ts      |  21 ++-
 frontend/src/app/pages/login/login.html            |  38 ++---
 frontend/src/app/pages/login/login.ts              |   3 +-
 frontend/src/app/pages/overtime/overtime.html      |  48 +++---
 frontend/src/app/pages/overtime/overtime.ts        |   3 +-
 frontend/src/app/pages/overview/overview.html      |  16 +-
 .../personnel-workspace/personnel-workspace.html   | 130 ++++++++++-----
 .../personnel-workspace/personnel-workspace.ts     |  47 +++---
 .../src/app/pages/placeholder/placeholder.html     |   4 +-
 frontend/src/app/pages/placeholder/placeholder.ts  |   3 +-
 frontend/src/app/pages/schedules/schedules.html    |  48 +++---
 frontend/src/app/pages/schedules/schedules.ts      |   3 +-
 .../src/app/pages/work-shifts/work-shifts.html     | 115 +++++++------
 frontend/src/app/pages/work-shifts/work-shifts.ts  |   3 +-
 frontend/src/app/services/employee.service.ts      |   3 +-
 frontend/src/app/services/language.service.ts      |  14 +-
 frontend/src/app/shared/pagination.ts              |   3 +-
 frontend/src/app/shared/record-workspace.ts        |  11 +-
 frontend/src/styles.css                            |  10 ++
 51 files changed, 804 insertions(+), 485 deletions(-)

```
## Staged stat
```text
 .../AttendanceManagementReadOnlyController.java    |  40 ++++++
 .../WorkShiftManagementWriteController.java        |  65 +++++++++
 .../controller/WorkShiftReadOnlyController.java    |  46 +++++++
 .../AttendanceManagementReadOnlyQueryService.java  | 132 ++++++++++++++++++
 .../service/WorkShiftManagementWriteService.java   | 108 +++++++++++++++
 .../hrm/service/WorkShiftPermissionService.java    |  18 +++
 frontend/proxy.conf.json                           |   2 +-
 frontend/src/app/app.routes.ts                     |  52 +++----
 frontend/src/app/layout/admin-layout.html          | 122 ++++++++---------
 frontend/src/app/navigation.ts                     |   2 +
 .../attendance-summary/attendance-summary.html     |  62 +++++++++
 .../pages/attendance-summary/attendance-summary.ts |  19 +++
 frontend/src/app/pages/attendance/attendance.html  |  30 ++++
 frontend/src/app/pages/attendance/attendance.ts    |   4 +
 frontend/src/app/pages/overtime/overtime.html      |  67 +++++++++
 frontend/src/app/pages/overtime/overtime.ts        |  19 +++
 frontend/src/app/pages/schedules/schedules.html    |  67 +++++++++
 frontend/src/app/pages/schedules/schedules.ts      |  19 +++
 frontend/src/app/pages/work-module.css             |  28 ++++
 .../src/app/pages/work-shifts/work-shifts.html     | 151 +++++++++++++++++++++
 frontend/src/app/pages/work-shifts/work-shifts.ts  | 145 ++++++++++++++++++++
 .../src/app/services/work-module-api.service.ts    |  42 ++++++
 22 files changed, 1146 insertions(+), 94 deletions(-)

```
