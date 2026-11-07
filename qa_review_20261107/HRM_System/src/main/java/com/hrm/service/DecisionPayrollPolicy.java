package com.hrm.service;

import com.hrm.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DecisionPayrollPolicy {
    private final String version;
    public DecisionPayrollPolicy(@Value("${hrm.decisions.payroll-policy-version:UNCONFIRMED}") String version) {
        this.version=version==null?"UNCONFIRMED":version.trim();
    }
    public String version(){return version;}
    public void requireConfirmed() {
        if(version.isBlank() || "UNCONFIRMED".equalsIgnoreCase(version))
            throw new BusinessException(HttpStatus.CONFLICT,
                "Chưa xác nhận chính sách cộng trừ quyết định với nhóm lương");
    }
}