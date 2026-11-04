package com.hrm.service;

import com.hrm.enums.*;
import com.hrm.exception.BusinessException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/** Shared rules for writes, approvals and payroll source validation. */
public final class DecisionRules {
    private DecisionRules() {}
    public static void validate(DecisionType type, DecisionPayrollEffect effect,
            DisciplineForm form, LocalDate violationDate, Integer delayMonths) {
        if (type == null || effect == null) fail("Thiếu loại quyết định hoặc ảnh hưởng lương");
        if (type == DecisionType.KY_LUAT) {
            if (effect != DecisionPayrollEffect.NONE) fail("Kỷ luật chỉ được payrollEffect=NONE");
            if (form == null || violationDate == null) fail("Kỷ luật bắt buộc hình thức và ngày vi phạm");
            if (form == DisciplineForm.KEO_DAI_NANG_LUONG) {
                if (delayMonths == null || delayMonths < 1 || delayMonths > 6) fail("Kéo dài nâng lương phải từ 1 đến 6 tháng");
            } else if (delayMonths != null) fail("Chỉ kéo dài nâng lương được nhập số tháng");
        } else {
            if (form != null || violationDate != null || delayMonths != null) fail("Chỉ quyết định kỷ luật được nhập thông tin kỷ luật");
            if (type == DecisionType.KHEN_THUONG && effect != DecisionPayrollEffect.NONE && effect != DecisionPayrollEffect.ADD) fail("Khen thưởng chỉ được NONE hoặc ADD");
            if (type == DecisionType.BOI_THUONG && effect != DecisionPayrollEffect.DEDUCT) fail("Bồi thường bắt buộc DEDUCT");
        }
    }
    private static void fail(String message) { throw new BusinessException(HttpStatus.BAD_REQUEST, message); }
}
