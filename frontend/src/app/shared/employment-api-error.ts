import type { HttpErrorResponse } from '@angular/common/http';

const fallbackByStatus: Record<number, string> = {
  400: 'Dữ liệu không hợp lệ.',
  404: 'Không tìm thấy dữ liệu yêu cầu.',
  409: 'Dữ liệu xung đột hoặc chưa đủ điều kiện nghiệp vụ.',
  503: 'Chức năng tạm thời chưa sẵn sàng hoặc thiếu nguồn dữ liệu.',
};

export function employmentApiError(error: HttpErrorResponse, fallback: string): string {
  if (error.status === 0) return 'Không kết nối được backend. Hãy kiểm tra kết nối.';
  if (error.status === 401) return 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.';
  if (error.status === 403) return 'Tài khoản không có quyền thực hiện thao tác này.';

  const detail = error.error?.loi_chi_tiet ?? error.error?.detail ?? error.error?.message;
  if (typeof detail === 'string' && detail.trim()) return detail;
  if (detail && typeof detail === 'object') {
    const fields = Object.values(detail).filter(value => typeof value === 'string' && value.trim());
    if (fields.length) return fields.join(' · ');
  }
  return fallbackByStatus[error.status] ?? fallback;
}
