import { Routes } from "@angular/router";
import { PersonnelWorkspacePage } from "./pages/personnel-workspace/personnel-workspace";
import { CatalogPage } from "./pages/catalog/catalog";
import { AdminLayout } from "./layout/admin-layout";
import { EmployeesPage } from "./pages/employees/employees";
import { ConnectionPage } from "./pages/connection/connection";
import { PlaceholderPage } from "./pages/placeholder/placeholder";
import { DepartmentsPage } from "./pages/departments/departments";
import { authGuard } from "./services/auth.guard";
export const routes: Routes = [
  { path: "login", loadComponent: () => import("./pages/login/login").then(m => m.LoginPage) },
  {
    path: "",
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: "", redirectTo: "overview", pathMatch: "full" },
      { path: "overview", loadComponent: () => import("./pages/overview/overview").then(m => m.OverviewPage) },
      { path: "employees", component: EmployeesPage },
      {
        path: "assignments",
        component: PersonnelWorkspacePage,
        data: { workspace: "assignments" },
      },
      {
        path: "certificates",
        component: PersonnelWorkspacePage,
        data: { workspace: "certificates" },
      },
      {
        path: "leave-entitlements",
        component: PersonnelWorkspacePage,
        data: { workspace: "entitlements" },
      },
      {
        path: "audit",
        component: PersonnelWorkspacePage,
        data: { workspace: "audit" },
      },
      { path: "connection", component: ConnectionPage },
      {
        path: "accounts",
        loadComponent: () =>
          import("./pages/accounts/accounts").then((m) => m.AccountsPage),
      },
      { path: "departments", component: DepartmentsPage },
      {
        path: "department-catalog",
        component: CatalogPage,
        data: { catalog: "departments" },
      },
      {
        path: "leave-types",
        component: CatalogPage,
        data: { catalog: "leaveTypes" },
      },
      {
        path: "positions",
        component: CatalogPage,
        data: { catalog: "positions" },
      },
      {
        path: "qualifications",
        component: CatalogPage,
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
          import("./pages/contract-types/contract-types").then((m) => m.ContractTypesPage),
      },
      {
        path: "schedules",
        component: PlaceholderPage,
        data: { title: "Lịch làm việc" },
      },
      {
        path: "attendance",
        component: PlaceholderPage,
        data: { title: "Chấm công" },
      },
      {
        path: "requests",
        component: PersonnelWorkspacePage,
        data: { workspace: "requests" },
      },
      {
        path: "overtime",
        component: PlaceholderPage,
        data: { title: "Tăng ca" },
      },
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
        loadComponent: () =>
          import("./pages/reports/reports").then((m) => m.ReportsPage),
      },
      {
        path: "forbidden",
        component: PlaceholderPage,
        data: {
          title: "Không có quyền truy cập",
          message:
            "Đây là trang thông báo mẫu; sẽ nối với cơ chế phân quyền thực tế.",
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
