package com.hrm.service;
import java.time.LocalDate;import org.junit.jupiter.api.Test;import com.hrm.enums.ContractTermKind;import static org.junit.jupiter.api.Assertions.*;
class ContractGeneratedFieldsTest {
 @Test void fixedTermUsesConfiguredMonthsAndInclusiveEnd(){assertEquals(LocalDate.of(2028,11,8),ContractGeneratedFields.endDate(ContractTermKind.XAC_DINH,24,LocalDate.of(2026,11,9),LocalDate.of(2026,11,10)));}
 @Test void monthEndAndLeapYearAreClamped(){assertEquals(LocalDate.of(2026,2,27),ContractGeneratedFields.endDate(ContractTermKind.XAC_DINH,1,LocalDate.of(2026,1,31),null));assertEquals(LocalDate.of(2028,2,28),ContractGeneratedFields.endDate(ContractTermKind.XAC_DINH,1,LocalDate.of(2028,1,31),null));}
 @Test void indefiniteClearsEndAndProbationKeepsOwnDate(){assertNull(ContractGeneratedFields.endDate(ContractTermKind.KHONG_XAC_DINH,null,LocalDate.of(2026,11,9),LocalDate.of(2027,1,1)));assertEquals(LocalDate.of(2026,11,15),ContractGeneratedFields.endDate(ContractTermKind.THU_VIEC,null,LocalDate.of(2026,11,9),LocalDate.of(2026,11,15)));}
 @Test void numbersUseTypeCodeAndDoNotTruncateLargeCounter(){assertEquals("XD24-000001",ContractGeneratedFields.number("xd24",1));assertEquals("KXD-1000000",ContractGeneratedFields.number("KXD",1000000));assertThrows(RuntimeException.class,()->ContractGeneratedFields.number("X;DROP",1));assertThrows(RuntimeException.class,()->ContractGeneratedFields.endDate(ContractTermKind.XAC_DINH,null,LocalDate.of(2026,11,9),null));}
}
