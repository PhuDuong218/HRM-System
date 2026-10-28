package com.hrm.hrm.service;

import java.util.concurrent.*;
import com.hrm.service.PersonnelWorkflowSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.jpa.show-sql=false")
class PersonnelWorkflowLockConcurrencyTest {
    @Autowired PersonnelWorkflowSupport support;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Test void competingApprovalTransactionsWaitForEachOther() throws Exception {
        assertEquals("hrm_test",jdbc.queryForObject("SELECT DB_NAME()",String.class));
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);var acquired=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->new TransactionTemplate(manager).execute(s->{support.lock();locked.countDown();
                try{if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                s.setRollbackOnly();return null;}));
            try {
                assertTrue(locked.await(5,TimeUnit.SECONDS));
                var second=pool.submit(()->new TransactionTemplate(manager).execute(s->{started.countDown();support.lock();acquired.countDown();s.setRollbackOnly();return null;}));
                assertTrue(started.await(5,TimeUnit.SECONDS));assertFalse(acquired.await(250,TimeUnit.MILLISECONDS));
                release.countDown();first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);assertEquals(0,acquired.getCount());
            } finally {release.countDown();}
        }
    }
}
