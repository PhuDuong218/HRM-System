import {adminGuard} from './services/admin.guard';
import { Routes } from "@angular/router";
import { AdminLayout } from "./layout/admin-layout";
import { OverviewPage } from "./pages/overview/overview";
import { ConnectionPage } from "./pages/connection/connection";
import { PlaceholderPage } from "./pages/placeholder/placeholder";
import { LoginPage } from "./pages/login/login";
import { authGuard } from "./services/auth.guard";
export const routes: Routes = [
  { path: "login", component: LoginPage },
  {
    path: "",
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: "", redirectTo: "overview", pathMatch: "full" },
      { path: "overview", component: OverviewPage },
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
        component: PlaceholderPage,
        data: { title: "Tài khoản và phân quyền" },
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
      {
        path: "requests",
        loadComponent: () => import("./pages/personnel-workspace/personnel-workspace").then(m => m.PersonnelWorkspacePage),
        data: { workspace: "requests" },
      },
      { path: "overtime", loadComponent: () => import("./pages/overtime/overtime").then(m => m.OvertimePage) },
      {
        path: "allowances",
        component: PlaceholderPage,
        data: { title: "Phụ cấp" },
      },
      {
        path: "decisions",
        component: PlaceholderPage,
        data: { title: "Khen thưởng / kỷ luật" },
      },
      {
        path: "payroll",
        component: PlaceholderPage,
        data: { title: "Tiền lương" },
      },
      {
        path: "payslips",
        component: PlaceholderPage,
        data: { title: "Phiếu lương" },
      },
      {
        path: "reports",
        component: PlaceholderPage,
        data: { title: "Báo cáo" },
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
