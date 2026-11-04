package com.hrm.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class AttendanceCalendarPolicyTest {
 @Test void configuredCalendarExportsOfficialHolidayDates(){var policy=new AttendancePortalDeadlinePolicy(true,"2026-11-04","SATURDAY,SUNDAY");assertEquals(List.of("2026-11-04"),policy.calendarInfo().get("holidays"));}
 @Test void unconfiguredCalendarDoesNotInventHolidays(){var policy=new AttendancePortalDeadlinePolicy(false,"2026-11-04","SATURDAY,SUNDAY");assertEquals(false,policy.calendarInfo().get("configured"));assertEquals(List.of(),policy.calendarInfo().get("holidays"));}
}
