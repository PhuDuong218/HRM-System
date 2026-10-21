
package com.hrm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hrm.dto.AttendanceDTOs.AttendanceInput;
import com.hrm.service.AttendanceService;

class AttendanceServiceTest {

    @Test
    void khongChoPhepNhapCongNgoaiLich() {

        JdbcTemplate db = mock(JdbcTemplate.class);
        AttendanceService service = new AttendanceService(db);

        AttendanceInput input = new AttendanceInput(
            3L,
            null,
            LocalDate.of(2026, 10, 22),
            LocalDateTime.of(2026, 10, 22, 14, 0),
            LocalDateTime.of(2026, 10, 22, 22, 0),
            45,
            "DI_LAM",
            "Kiem tra nhap cong ngoai lich"
        );

        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> service.createManual(input)
        );

        verifyNoInteractions(db);
    }
}
