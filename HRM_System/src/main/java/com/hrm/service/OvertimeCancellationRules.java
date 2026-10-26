package com.hrm.service;

import java.time.LocalDateTime;
import java.util.Objects;

/** Read-only decision helper; never substitutes TU_CHOI for DA_HUY. */
public final class OvertimeCancellationRules {
    private OvertimeCancellationRules() {
    }

    public enum Decision {
        CANCEL_PENDING, REQUIRE_APPROVAL, NOT_CANCELLABLE
    }

    public static Decision evaluate(String status, boolean hasRecordedActual,
            LocalDateTime requestedStart, LocalDateTime now) {
        Objects.requireNonNull(status);
        Objects.requireNonNull(requestedStart);
        Objects.requireNonNull(now);
        if ("CHO_DUYET".equals(status) && !hasRecordedActual)
            return Decision.CANCEL_PENDING;
        if ("DA_DUYET".equals(status) && !hasRecordedActual && now.isBefore(requestedStart))
            return Decision.REQUIRE_APPROVAL;
        return Decision.NOT_CANCELLABLE;
    }
}
