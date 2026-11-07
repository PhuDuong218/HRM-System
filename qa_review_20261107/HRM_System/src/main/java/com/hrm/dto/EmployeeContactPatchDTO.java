package com.hrm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmployeeContactPatchDTO {
    private boolean emailProvided, identityProvided, addressProvided;
    public boolean hasEmail(){return emailProvided;}
    public boolean hasIdentityNumber(){return identityProvided;}
    public boolean hasAddress(){return addressProvided;}
    public void ignorePrivateChanges(){identityNumber=null;address=null;identityProvided=false;addressProvided=false;}
    @jakarta.validation.constraints.NotBlank(message="Phải gửi phiên bản hồ sơ đã đọc")
    @Pattern(regexp="[0-9a-fA-F]{16}",message="Phiên bản hồ sơ không hợp lệ")
    private String version;
    public String getVersion(){return version;}
    public void setVersion(String value){version=value;}
    @jakarta.validation.constraints.NotBlank(message="Lý do thay đổi không được để trống")
    @Size(max=1000) private String reason;
    public String getReason(){return reason;}
    public void setReason(String value){reason=value==null?null:value.trim();}

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
    @Pattern(regexp="[0-9]{9}|[0-9]{12}",message="CCCD/CMND phải gồm 9 hoặc 12 chữ số")
    private String identityNumber;

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
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
        addressProvided=true;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber == null ? null : identityNumber.trim();
        identityProvided=true;
    }
}
