package com.hrm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmployeeContactPatchDTO {

    @Email(message = "Định dạng email không hợp lệ")
    @Size(max = 254, message = "Email không vượt quá 254 ký tự")
    private String email;

    @Pattern(
        regexp = "^(?:0[35789][0-9]{8})?$",
        message = "Số điện thoại phải gồm 10 chữ số hợp lệ hoặc để rỗng"
    )
    private String phone;

    @Size(max = 500, message = "Địa chỉ không vượt quá 500 ký tự")
    private String address;

    @Size(max = 50, message = "CCCD không vượt quá 50 ký tự")
    private String identityNumber;

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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber;
    }
}