package com.hrm.service;

import com.hrm.model.Employee;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Check the client's snapshot under the employee lock; existing Hibernate @Version guards the SQL update too. */
public final class EmployeeWriteRules {
    private EmployeeWriteRules() {}
    public static String version(Employee employee) {
        return employee.getRowVersion()==null?null:HexFormat.of().formatHex(employee.getRowVersion());
    }
    public static void requireVersion(Employee employee,String expected) {
        if(expected==null||!expected.matches("[0-9a-fA-F]{16}"))throw bad("Phải gửi phiên bản hồ sơ đã đọc hợp lệ");
        if(!expected.equalsIgnoreCase(version(employee)))throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Hồ sơ đã được người khác thay đổi. Hãy tải lại để xem bản mới nhất.");
    }
    public static String reason(String value) {
        if(value==null||value.isBlank()||value.trim().length()>1000)throw bad("Lý do thay đổi là bắt buộc, tối đa 1000 ký tự");
        return value.trim();
    }
    public static void suppliedRequired(String value,String label) {
        if(value!=null&&value.isBlank())throw bad(label+" không được để trống");
    }
    public static void requiredIfPresent(boolean supplied,String value,String label) {
        if(supplied&&(value==null||value.isBlank()))throw bad(label+" không được để trống");
    }
    public static void identity(String value) {
        if(value!=null&&!value.trim().matches("[0-9]{9}|[0-9]{12}"))throw bad("CCCD/CMND phải gồm 9 hoặc 12 chữ số");
    }
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
