package com.hrm.service;

import com.hrm.repository.WorkStatisticsRepository;
import com.hrm.repository.projection.WorkStatisticsRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkStatisticsSchemaTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void selectsConfirmedAttendanceCompatibleWithTheSchema(boolean hasInvalidation) {
        var repository = mock(WorkStatisticsRepository.class);
        var row = mock(WorkStatisticsRow.class);
        when(row.getConfirmedWorkDays()).thenReturn(5L);
        when(row.getConfirmedAbsentDays()).thenReturn(1L);
        when(repository.attendanceInvalidationColumnLength()).thenReturn(hasInvalidation ? 8 : null);
        when(repository.summarizeAttendance(any(), any())).thenReturn(Optional.of(row));
        when(repository.summarizeValidAttendance(any(), any())).thenReturn(Optional.of(row));
        when(repository.findApprovedLeaveIntervals(any(), any())).thenReturn(List.of());
        when(repository.findConfirmedOvertimeIntervals(any(), any())).thenReturn(List.of());
        LocalDate date = LocalDate.of(2026, 11, 2);
        var result = new WorkStatisticsService(repository).summarize(date, date);
        assertEquals(5, result.confirmedWorkDays());
        verify(repository, times(hasInvalidation ? 1 : 0)).summarizeValidAttendance(date, date);
        verify(repository, times(hasInvalidation ? 0 : 1)).summarizeAttendance(date, date);
    }
}
