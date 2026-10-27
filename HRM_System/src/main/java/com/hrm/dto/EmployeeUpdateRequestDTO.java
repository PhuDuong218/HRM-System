
package com.hrm.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmployeeUpdateRequestDTO {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 200)
    private String fullName;

    @Past(message = "Ngày sinh phải trong quá khứ")
    private LocalDate birthDate;

    @Email(message = "Email không hợp lệ")
    @Size(max = 254)
    private String email;

    @Pattern(
            regexp = "^0[35789][0-9]{8}$",
            message = "Số điện thoại không hợp lệ"
    )
    private String phone;

    @Size(max = 50)
    private String identityNumber;

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

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber;
    }
}
