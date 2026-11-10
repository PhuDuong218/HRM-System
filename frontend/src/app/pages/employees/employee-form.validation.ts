import type { EmployeeForm } from '../../services/employee.service';

/** UI completeness rules plus employee DTO limits. */
export function employeeFormErrors(form: EmployeeForm, mode: string, today: string, contactEditable=true): Record<string, string> {
    const errors: Record<string, string> = {};
    const text = (value: unknown) => typeof value === 'string' ? value.trim() : '';
    const day = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) &&
        !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
    if (mode === 'create') {
        if (!form.hireDate || !day(form.hireDate) || form.hireDate > today) errors['hireDate'] = 'Chọn ngày vào làm hợp lệ, không ở tương lai.';
        if (!form.departmentId || form.departmentId <= 0) errors['departmentId'] = 'Chọn phòng ban hoạt động.';
        if (!form.positionId || form.positionId <= 0) errors['positionId'] = 'Chọn chức vụ hoạt động.';
    }
    if (mode !== 'contact') {
        if (!text(form.fullName) || text(form.fullName).length > 200) errors['fullName'] = 'Nhập họ tên hợp lệ, tối đa 200 ký tự.';
        if (form.birthDate && (!day(form.birthDate) || form.birthDate >= today || (!!form.hireDate && form.birthDate >= form.hireDate))) errors['birthDate'] = 'Ngày sinh phải trong quá khứ và trước ngày vào làm.';
        if (!form.gender || !['MALE','FEMALE','OTHER','UNDISCLOSED'].includes(form.gender)) errors['gender'] = 'Vui lòng chọn giới tính.';
    }
    if (text(form.email) && (text(form.email).length > 254 || !/^[^\s@]+@[^\s@]+$/.test(text(form.email)))) errors['email'] = 'Email chưa đúng định dạng.';
    if (contactEditable&&!text(form.email)) errors['email']='Vui lòng nhập email.';
    if(form.privateFieldsEditable){
      if(!text(form.identityNumber))errors['identityNumber']='Vui lòng nhập CCCD.';
      if(!text(form.address))errors['address']='Vui lòng nhập địa chỉ nhà.';
    }
    if (text(form.phone) && !/^0[35789][0-9]{8}$/.test(text(form.phone))) errors['phone'] = 'Điện thoại gồm 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09.';
    if (text(form.identityNumber) && !/^(?:[0-9]{9}|[0-9]{12})$/.test(text(form.identityNumber))) errors['identityNumber'] = 'CCCD/CMND phải gồm 9 hoặc 12 chữ số';
    if (text(form.address).length > 500) errors['address'] = 'Địa chỉ không vượt quá 500 ký tự.';
    if (mode !== 'create' && (!text(form.reason) || text(form.reason).length > 1000)) errors['changeReason'] = 'Nhập lý do thay đổi, tối đa 1000 ký tự.';
    return errors;
}
