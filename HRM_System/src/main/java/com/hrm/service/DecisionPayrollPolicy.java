package com.hrm.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.hrm.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DecisionPayrollPolicy {
    private final String version;
    private List<RewardLevel> rewards = List.of();
    public DecisionPayrollPolicy(@Value("${hrm.decisions.payroll-policy-version:UNCONFIRMED}") String version) {
        this.version=version==null?"UNCONFIRMED":version.trim();
    }
    public String version(){return version;}
    public record RewardLevel(String code, String label, BigDecimal amount) {}

    @Value("${hrm.decisions.reward-levels:}")
    void configureRewards(String configured) {
        var levels = new ArrayList<RewardLevel>();
        if (!configured.isBlank()) {
            for (String entry : configured.split(",", -1)) {
                var parts = entry.trim().split("=", -1);
                if (parts.length != 2 || !parts[1].matches("[1-9][0-9]{0,11}")) {
                    throw new IllegalArgumentException("hrm.decisions.reward-levels requires CODE=whole positive VND");
                }
                String label = switch (parts[0]) {
                    case "CONTRIBUTION" -> "Đóng góp nổi bật";
                    case "EXCELLENCE" -> "Thành tích xuất sắc";
                    case "INITIATIVE" -> "Sáng kiến được công nhận";
                    default -> throw new IllegalArgumentException("Unknown reward level " + parts[0]);
                };
                if (levels.stream().anyMatch(level -> level.code().equals(parts[0]))) {
                    throw new IllegalArgumentException("Duplicate reward level " + parts[0]);
                }
                levels.add(new RewardLevel(parts[0], label, new BigDecimal(parts[1])));
            }
        }
        rewards = List.copyOf(levels);
    }

    public List<RewardLevel> rewards() { return rewards; }

    public BigDecimal rewardAmount(String code) {
        return rewards.stream().filter(level -> level.code().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
                        "Hãy chọn hạng thưởng đã được cấu hình trong quy chế.")).amount();
    }
    public void requireConfirmed() {
        if(version.isBlank() || "UNCONFIRMED".equalsIgnoreCase(version))
            throw new BusinessException(HttpStatus.CONFLICT,
                "Chưa xác nhận chính sách cộng trừ quyết định với nhóm lương");
    }
}
