package com.hrm.service;
import org.junit.jupiter.api.Test;import java.math.BigDecimal;import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class LeaveWorkspacePolicyTest {
 @Test void adjustmentPreservesUsedHours(){assertEquals(new BigDecimal("80"),LeaveQuotaWorkspaceService.adjusted(new BigDecimal("96"),new BigDecimal("16"),new BigDecimal("-16")));assertThrows(RuntimeException.class,()->LeaveQuotaWorkspaceService.adjusted(new BigDecimal("96"),new BigDecimal("80"),new BigDecimal("-17")));assertThrows(RuntimeException.class,()->LeaveQuotaWorkspaceService.adjusted(new BigDecimal("96"),BigDecimal.ZERO,BigDecimal.ZERO));}
 @Test void maleMaternityHasFourteenCalendarDays(){var a=LocalDateTime.of(2026,11,1,8,0);assertDoesNotThrow(()->MaternityLeavePolicy.validateLimit("MALE",a,a.plusDays(14),14,6));assertThrows(RuntimeException.class,()->MaternityLeavePolicy.validateLimit("MALE",a,a.plusDays(14).plusMinutes(1),14,6));}
 @Test void femaleMaternityUsesCalendarMonths(){var a=LocalDateTime.of(2026,8,31,8,0);assertDoesNotThrow(()->MaternityLeavePolicy.validateLimit("FEMALE",a,a.plusMonths(6),14,6));assertThrows(RuntimeException.class,()->MaternityLeavePolicy.validateLimit("FEMALE",a,a.plusMonths(6).plusMinutes(1),14,6));assertThrows(RuntimeException.class,()->MaternityLeavePolicy.validateLimit(null,a,a.plusDays(1),14,6));}
}