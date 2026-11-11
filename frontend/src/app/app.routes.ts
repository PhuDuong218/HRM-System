import {personnelPortalGuard} from './services/personnel-portal.guard';
import {pendingEditorGuard} from './services/pending-editor.service';
import {adminGuard} from './services/admin.guard';
import { Routes } from "@angular/router";
import { AdminLayout } from "./layout/admin-layout";
import { ConnectionPage } from "./pages/connection/connection";
import { PlaceholderPage } from "./pages/placeholder/placeholder";
import { authGuard, guestGuard } from "./services/auth.guard";
import { PayslipsPage } from './pages/payslips/payslips';
import { PayrollPage } from './pages/payroll/payroll';
export const routes: Routes = [
  { path: "login", canActivate: [guestGuard], loadComponent: () => import("./pages/login/login").then(m => m.LoginPage) },
  {
    path: "",
    component: AdminLayout,
    canActivate: [authGuard],
    canActivateChild:[authGuard, personnelPortalGuard],
    children: [
      {path:'ui-kit',canActivate:[adminGuard],loadComponent:()=>import('./pages/ui-kit/ui-kit').then(m=>m.UiKitPage)},
      { path: "", redirectTo: "overview", pathMatch: "full" },
      { path: "overview", loadComponent: () => import("./pages/overview/overview").then(m => m.OverviewPage) },
      { path: "employees", loadComponent: () => import("./pages/employees/employees").then(m => m.EmployeesPage) },
      {
        path: "assignments",
        loadComponent: () => import("./pages/personnel-workspace/personnel-workspace").then(m => m.PersonnelWorkspacePage),
        data: { workspace: "assignments" },
      },
      {
        path: "certificates",
        loadComponent: () => import("./pages/personnel-workspace/personnel-workspace").then(m => m.PersonnelWorkspacePage),
        data: { workspace: "certificates" },
      },
      {
        path: "leave-entitlements",
        loadComponent: () => import("./pages/personnel-workspace/personnel-workspace").then(m => m.PersonnelWorkspacePage),
        data: { workspace: "entitlements" },
      },
      {
        path: "audit",
        loadComponent: () => import("./pages/personnel-workspace/personnel-workspace").then(m => m.PersonnelWorkspacePage),
        data: { workspace: "audit" },
      },
      { path: "connection", component: ConnectionPage, canActivate:[adminGuard] },
      {
        path: "accounts",
        canActivate:[adminGuard],
        loadComponent: () => import("./pages/accounts/accounts").then(m => m.AccountsPage),
      },
      { path: "departments", loadComponent: () => import("./pages/departments/departments").then(m => m.DepartmentsPage) },
      {
        path: "department-catalog",
        loadComponent: () => import("./pages/catalog/catalog").then(m => m.CatalogPage),
        data: { catalog: "departments" },
      },
      {
        path: "leave-types",
        loadComponent: () => import("./pages/catalog/catalog").then(m => m.CatalogPage),
        data: { catalog: "leaveTypes" },
      },
      {
        path: "positions",
        loadComponent: () => import("./pages/catalog/catalog").then(m => m.CatalogPage),
        data: { catalog: "positions" },
      },
      {
        path: "qualifications",
        loadComponent: () => import("./pages/catalog/catalog").then(m => m.CatalogPage),
        data: { catalog: "qualifications" },
      },
      {
        path: "contracts",
        loadComponent: () =>
          import("./pages/contracts/contracts").then((m) => m.ContractsPage),
      },
      {
        path: "contract-types",
        loadComponent: () =>
          import("./pages/contract-types/contract-types").then(
            (m) => m.ContractTypesPage,
          ),
      },
      { path: "work-shifts", loadComponent: () => import("./pages/work-shifts/work-shifts").then(m => m.WorkShiftsPage) },
      { path: "schedules", loadComponent: () => import("./pages/schedules/schedules").then(m => m.SchedulesPage) },
      { path: "attendance", loadComponent: () => import("./pages/attendance/attendance").then(m => m.AttendancePage) },
      { path: "attendance/summary", loadComponent: () => import("./pages/attendance-summary/attendance-summary").then(m => m.AttendanceSummaryPage) },
      {path:'department/reviews',redirectTo:'requests/head',pathMatch:'full'},
      ...['assignments','schedules','attendance','summary','overtime','reports'].map(area=>({path:'department/'+area,loadComponent:()=>import('./pages/department-workspace/department-workspace').then(m=>m.DepartmentWorkspacePage),data:{departmentArea:area}})),
      {path:'requests/head' ,loadComponent:()=>import('./pages/head-requests/head-requests').then(m=>m.HeadRequestsPage)},
      {
        path: "requests",
        loadComponent: () => import("./pages/personnel-workspace/personnel-workspace").then(m => m.PersonnelWorkspacePage),
        data: { workspace: "requests" },
      },
      { path: "overtime", loadComponent: () => import("./pages/overtime/overtime").then(m => m.OvertimePage) },
            {
        path: "allowances",
        loadComponent: () =>
          import("./pages/allowances/allowances").then((m) => m.AllowancesPage),
      },
      {
        path: "decisions",
        loadComponent: () =>
          import("./pages/decisions/decisions").then((m) => m.DecisionsPage),
      },
      { 
        path: 'payroll', 
        component: PayrollPage,
        data: { title: 'Tiền lương' },
      },
      { 
        path: "payslips", 
        component: PayslipsPage,
        data: { title: "Phiếu lương" },
      },
      {
        path: "reports",
        canActivate: [adminGuard],
        loadComponent: () => import("./pages/reports/reports").then(m => m.ReportsPage),
      },
      {
        path: "forbidden",
        component: PlaceholderPage,
        data: {
          title: "Không có quyền truy cập",
          message:
            "Tài khoản của bạn không có quyền truy cập chức năng này.",
        },
      },
      {
        path: "**",
        component: PlaceholderPage,
        data: {
          title: "404 — Không tìm thấy trang",
          message: "Đường dẫn bạn nhập không tồn tại.",
        },
      },
    ],
  },
];
for(const route of routes){for(const child of route.children??[]){if(child.component||child.loadComponent){child.canDeactivate=[...(child.canDeactivate??[]),pendingEditorGuard];child.runGuardsAndResolvers='always';}}}
