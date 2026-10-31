// Menu giao diện; quyền truy cập vẫn được kiểm tra tại backend.
export interface NavigationLink {
  path: string;
  label: string;
}

export interface NavigationItem {
  id: string;
  label: string;
  path?: string;
  children?: NavigationLink[];
}

export const navigation: NavigationItem[] = [
  { id: 'overview', path: 'overview', label: 'Tổng quan' },
  {
    id: 'personnel', label: 'Quản lý Nhân sự', children: [
      { path: 'employees', label: 'Nhân viên' },
      { path: 'contracts', label: 'Hợp đồng' },
      { path: 'decisions', label: 'Khen thưởng / Kỷ luật' }
    ]
  },
  {
    id: 'time', label: 'Thời gian & Chấm công', children: [
      { path: 'work-shifts', label: 'Danh mục ca' },
      { path: 'schedules', label: 'Lịch làm việc' },
      { path: 'attendance', label: 'Chấm công' },
      { path: 'attendance/summary', label: 'Tổng hợp công theo kỳ' },
      { path: 'overtime', label: 'Tăng ca' },
      { path: 'requests', label: 'Quản lý đơn từ' }
    ]
  },
  {
    id: 'pay', label: 'Lương & Phúc lợi', children: [
      { path: 'payroll', label: 'Tiền lương' },
      { path: 'allowances', label: 'Phụ cấp' },
      { path: 'payslips', label: 'Phiếu lương' }
    ]
  },
  {
    id: 'organization', label: 'Cơ cấu tổ chức', children: [
      { path: 'departments', label: 'Phòng ban' },
      { path: 'positions', label: 'Chức vụ' },
      { path: 'qualifications', label: 'Trình độ' }
    ]
  },
  { id: 'reports', path: 'reports', label: 'Báo cáo' },
  {
    id: 'system', label: 'Hệ thống', children: [
      { path: 'accounts', label: 'Tài khoản và phân quyền' },
      { path: 'connection', label: 'Kiểm tra kết nối' }
    ]
  }
];
