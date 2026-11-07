package com.hrm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name="hrm.contracts.expiry-job-enabled", havingValue="true", matchIfMissing=true)
public class ContractExpiryJob {
    private static final Logger log=LoggerFactory.getLogger(ContractExpiryJob.class);
    private final ContractLifecycleService lifecycle;
    public ContractExpiryJob(ContractLifecycleService lifecycle) { this.lifecycle=lifecycle; }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { run(); }

    @Scheduled(cron="0 5 0 * * *",zone="Asia/Bangkok")
    public void run() {
        while (true) {
            var ids=lifecycle.expiredIds();
            if (ids.isEmpty()) return;
            boolean failed=false;
            for (Long id:ids) {
                try { lifecycle.closeExpired(id); }
                catch (Exception ex) {
                    failed=true;
                    log.error("Không cập nhật được trạng thái hợp đồng ID={}",id,ex);
                }
            }
            // Không lặp vô hạn cùng bản lỗi. Xem log và xử lý trước lần chạy lại.
            if (failed) return;
        }
    }
}