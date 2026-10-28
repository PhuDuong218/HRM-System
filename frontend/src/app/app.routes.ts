import { Routes } from '@angular/router';
import { AdminLayout } from './layout/admin-layout';
import { OverviewPage } from './pages/overview/overview';
import { EmployeesPage } from './pages/employees/employees';
import { ConnectionPage } from './pages/connection/connection';
import { PlaceholderPage } from './pages/placeholder/placeholder';
import { DepartmentsPage } from './pages/departments/departments';
export const routes: Routes = [
  { path: 'login', component: PlaceholderPage, data: { title: 'Đăng nhập', message: 'Chờ đối chiếu API và chính sách phiên của backend để tích hợp đăng nhập thật.' } },
  { path: '', component: AdminLayout, children: [
      { path: '', redirectTo: 'overview', pathMatch: 'full' },
      { path: 'overview', component: OverviewPage },
      { path: 'employees', component: EmployeesPage },
      { path: 'connection', component: ConnectionPage },
      { path: 'accounts', component: PlaceholderPage, data: { title: 'Tài khoản và phân quyền' } },
      { path: 'departments', component: DepartmentsPage },
      { path: 'positions', component: PlaceholderPage, data: { title: 'Chức vụ' } },
      { path: 'qualifications', component: PlaceholderPage, data: { title: 'Trình độ' } },
      { path: 'contracts', component: PlaceholderPage, data: { title: 'Hợp đồng' } },
      { path: 'schedules', component: PlaceholderPage, data: { title: 'Lịch làm việc' } },
      { path: 'attendance', component: PlaceholderPage, data: { title: 'Chấm công' } },
      { path: 'requests', component: PlaceholderPage, data: { title: 'Đơn nghỉ phép / nghỉ việc' } },
      { path: 'overtime', component: PlaceholderPage, data: { title: 'Tăng ca' } },
      { path: 'allowances', component: PlaceholderPage, data: { title: 'Phụ cấp' } },
      { path: 'decisions', component: PlaceholderPage, data: { title: 'Khen thưởng / kỷ luật' } },
      { path: 'payroll', component: PlaceholderPage, data: { title: 'Tiền lương' } },
      { path: 'payslips', component: PlaceholderPage, data: { title: 'Phiếu lương' } },
      { path: 'reports', component: PlaceholderPage, data: { title: 'Báo cáo' } },
      { path: 'forbidden', component: PlaceholderPage, data: { title: 'Không có quyền truy cập', message: 'Đây là trang thông báo mẫu; sẽ nối với cơ chế phân quyền thực tế.' } },
      { path: '**', component: PlaceholderPage, data: { title: '404 — Không tìm thấy trang', message: 'Đường dẫn bạn nhập không tồn tại.' } }
  ] }
];
