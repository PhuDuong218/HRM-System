
package com.hrm.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmployeeUpdateRequestDTO {
    private boolean emailProvided, identityProvided, addressProvided,phoneProvided;
    public boolean hasPhone(){return phoneProvided;}
    public boolean hasEmail(){return emailProvided;}
    public boolean hasIdentityNumber(){return identityProvided;}
    public boolean hasAddress(){return addressProvided;}
    public void ignorePrivateChanges(){identityNumber=null;address=null;identityProvided=false;addressProvided=false;}
    @NotBlank(message = "Phải gửi phiên bản hồ sơ đã đọc")
    @Pattern(regexp="[0-9a-fA-F]{16}", message="Phiên bản hồ sơ không hợp lệ")
    private String version;
    public String getVersion(){return version;}
    public void setVersion(String value){version=value;}
    @NotBlank(message="Vui lòng chọn giới tính")
    @Pattern(regexp="MALE|FEMALE|OTHER|UNDISCLOSED",message="Giới tính chưa hợp lệ")
    private String gender;
    public String getGender(){return gender;}
    public void setGender(String value){gender=value;}
    @NotBlank(message="Lý do thay đổi không được để trống")
    @Size(max=1000) private String reason;
    public String getReason(){return reason;}
    public void setReason(String value){reason=value==null?null:value.trim();}

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
    @Pattern(regexp="[0-9]{9}|[0-9]{12}",message="CCCD/CMND phải gồm 9 hoặc 12 chữ số")
    private String identityNumber;

    @Size(max = 500, message = "Địa chỉ không vượt quá 500 ký tự")
    private String address;

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; addressProvided=true; }

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
        emailProvided=true;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
        phoneProvided=true;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber == null ? null : identityNumber.trim();
        identityProvided=true;
    }
}
