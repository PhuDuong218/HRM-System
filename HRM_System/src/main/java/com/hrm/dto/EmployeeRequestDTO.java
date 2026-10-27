package com.hrm.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmployeeRequestDTO {

    @NotBlank(message = "Mã nhân viên không được để trống")
    @Size(max = 50, message = "Mã nhân viên không vượt quá 50 ký tự")
    private String employeeCode;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 200, message = "Họ và tên không vượt quá 200 ký tự")
    private String fullName;

    @Past(message = "Ngày sinh phải là một ngày trong quá khứ")
    private LocalDate birthDate;

    @Email(message = "Định dạng email không hợp lệ")
    @Size(max = 254)
    private String email;

    @Pattern(
    regexp = "^0[35789][0-9]{8}$",
    message = "Số điện thoại phải gồm 10 chữ số hợp lệ"
)
    private String phone;

    @NotNull(message = "Ngày bắt đầu làm việc không được để trống")
    private LocalDate hireDate;

    @Size(max = 50)
    private String identityNumber; // Căn cước công dân

    // --- CÁC TRƯỜNG BẮT BUỘC ĐỂ TẠO PHÂN CÔNG CÔNG TÁC ---
    
    @NotNull(message = "Bắt buộc phải chọn Phòng ban khi thêm nhân viên")
    @jakarta.validation.constraints.Positive(message = "ID phòng ban phải lớn hơn 0")
    private Long departmentId;

    @NotNull(message = "Bắt buộc phải chọn Chức vụ khi thêm nhân viên")
    @jakarta.validation.constraints.Positive(message = "ID chức vụ phải lớn hơn 0")
    private Long positionId;
@Size(max = 500, message = "Địa chỉ không vượt quá 500 ký tự")
private String address;
    // TODO: Sử dụng Generate Getters and Setters cho toàn bộ các biến trên
    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public String getAddress() {
    return address;
}

public void setAddress(String address) {
    this.address = address;
}

}
