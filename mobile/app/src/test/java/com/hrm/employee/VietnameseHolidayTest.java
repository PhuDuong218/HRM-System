package com.hrm.employee;
import org.junit.Test;import java.time.LocalDate;import static org.junit.Assert.*;
public class VietnameseHolidayTest {
 @Test public void fixedHolidays(){assertEquals("Tết Dương lịch",VietnameseHoliday.name(LocalDate.of(2026,1,1)));assertEquals("Quốc khánh",VietnameseHoliday.name(LocalDate.of(2026,9,2)));}
 @Test public void lunarHolidaysUseVietnamTimezone(){assertEquals("Tết Âm lịch",VietnameseHoliday.name(LocalDate.of(2026,2,17)));assertEquals("Tết Âm lịch",VietnameseHoliday.name(LocalDate.of(2026,2,21)));assertEquals("",VietnameseHoliday.name(LocalDate.of(2026,2,22)));assertEquals("Giỗ Tổ Hùng Vương",VietnameseHoliday.name(LocalDate.of(2026,4,26)));}
}
