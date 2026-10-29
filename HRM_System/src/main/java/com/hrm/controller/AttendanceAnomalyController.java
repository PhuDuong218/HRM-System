package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AttendanceAnomalyQueryService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** No anonymous or broad all-employee endpoint. Feature disabled by default. */
@RestController
@RequestMapping("/api/attendance/anomalies")
@ConditionalOnProperty(name = "hrm.attendance.anomaly-api-enabled", havingValue = "true")
public class AttendanceAnomalyController {
    private final AttendanceAnomalyQueryService query;

    public AttendanceAnomalyController(AttendanceAnomalyQueryService query) {
        this.query = query;
    }

    @GetMapping
    public List<AttendanceAnomalyQueryService.AnomalyView> list(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam long employeeId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "50") int limit) {
        if (actor == null || actor.accountId() == null || actor.accountId() <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        return query.findForEmployee(actor.accountId(), employeeId, from, to, limit);
    }
}
