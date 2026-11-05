import type { EmployeeForm } from '../../services/employee.service';

/** Mirrors employee DTO limits; optional fields remain optional. */
export function employeeFormErrors(form: EmployeeForm, mode: string, today: string): Record<string, string> {
    const errors: Record<string, string> = {};
    const text = (value: unknown) => typeof value === 'string' ? value.trim() : '';
    const day = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) &&
        !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
    if (mode === 'create') {
        if (!text(form.employeeCode) || text(form.employeeCode).length > 50) errors['employeeCode'] = 'Mã nhân viên là bắt buộc, tối đa 50 ký tự.';
        if (!form.hireDate || !day(form.hireDate) || form.hireDate > today) errors['hireDate'] = 'Chọn ngày vào làm hợp lệ, không ở tương lai.';
        if (!form.departmentId || form.departmentId <= 0) errors['departmentId'] = 'Chọn phòng ban hoạt động.';
        if (!form.positionId || form.positionId <= 0) errors['positionId'] = 'Chọn chức vụ hoạt động.';
    }
    if (mode !== 'contact') {
        if (!text(form.fullName) || text(form.fullName).length > 200) errors['fullName'] = 'Nhập họ tên hợp lệ, tối đa 200 ký tự.';
        if (form.birthDate && (!day(form.birthDate) || form.birthDate >= today || (!!form.hireDate && form.birthDate >= form.hireDate))) errors['birthDate'] = 'Ngày sinh phải trong quá khứ và trước ngày vào làm.';
        if (form.gender && !['MALE','FEMALE','OTHER','UNDISCLOSED'].includes(form.gender)) errors['gender'] = 'Chọn giới tính trong danh sách hoặc để trống.';
    }
    if (text(form.email) && (text(form.email).length > 254 || !/^[^\s@]+@[^\s@]+$/.test(text(form.email)))) errors['email'] = 'Email chưa đúng định dạng.';
    if (text(form.phone) && !/^0[35789][0-9]{8}$/.test(text(form.phone))) errors['phone'] = 'Điện thoại gồm 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09.';
    if (text(form.identityNumber).length > 50) errors['identityNumber'] = 'CCCD không vượt quá 50 ký tự.';
    if (text(form.address).length > 500) errors['address'] = 'Địa chỉ không vượt quá 500 ký tự.';
    if (mode !== 'create' && (!text(form.reason) || text(form.reason).length > 1000)) errors['changeReason'] = 'Nhập lý do thay đổi, tối đa 1000 ký tự.';
    return errors;
}
