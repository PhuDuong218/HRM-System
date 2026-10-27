package com.hrm.hrm.service;

import com.hrm.service.AssignmentGuardService;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Two real SQL Server transactions; no fixture rows or permanent schema changes. */
@SpringBootTest
class AssignmentLockConcurrencyTest {
    @Autowired AssignmentGuardService guard;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;

    @Test void competingAssignmentTransactionsAreSerialized() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT DB_NAME()",String.class).toLowerCase().contains("test"));
        CountDownLatch firstLocked=new CountDownLatch(1),releaseFirst=new CountDownLatch(1),
                secondStarted=new CountDownLatch(1),secondLocked=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var first=workers.submit(()->new TransactionTemplate(manager).execute(status->{
                guard.lockTimeline(); firstLocked.countDown();
                try { if(!releaseFirst.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout test lock"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                status.setRollbackOnly(); return null;
            }));
            try {
                if (!firstLocked.await(5,TimeUnit.SECONDS)) {
                    first.get(1,TimeUnit.SECONDS); // Surface worker errors instead of hiding them behind a latch timeout.
                    fail("First transaction did not acquire the assignment lock");
                }
                var second=workers.submit(()->new TransactionTemplate(manager).execute(status->{
                    secondStarted.countDown(); guard.lockTimeline(); secondLocked.countDown(); status.setRollbackOnly(); return null;
                }));
                assertTrue(secondStarted.await(5,TimeUnit.SECONDS));
                assertFalse(secondLocked.await(250,TimeUnit.MILLISECONDS),"Second transaction must wait for the first");
                releaseFirst.countDown();
                first.get(5,TimeUnit.SECONDS); second.get(5,TimeUnit.SECONDS);
                assertEquals(0,secondLocked.getCount());
            } finally { releaseFirst.countDown(); }
        }
    }
}
