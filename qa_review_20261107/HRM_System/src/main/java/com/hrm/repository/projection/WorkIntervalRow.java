package com.hrm.repository.projection;

import java.time.LocalDateTime;

public interface WorkIntervalRow {

    LocalDateTime getStartsAt();

    LocalDateTime getEndsAt();
}
