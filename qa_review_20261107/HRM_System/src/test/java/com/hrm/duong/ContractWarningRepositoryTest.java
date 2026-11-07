package com.hrm.duong;

import java.time.LocalDate;
import java.util.*;
import com.hrm.repository.ContractWarningRepository;
import com.hrm.service.ContractWarningService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

/** Runs the production projection/NOT EXISTS/OFFSET SQL unchanged against private H2 MSSQL mode. */
class ContractWarningRepositoryTest {
    EmbeddedDatabase memory;
    JdbcTemplate jdbc;
    ContractWarningService service;

    @BeforeEach void setup() {
        memory=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc=new JdbcTemplate(memory);
        jdbc.execute("set mode MSSQLServer");
        jdbc.execute("create schema dbo");
        jdbc.execute("create table dbo.employees(employee_id bigint primary key,employee_code varchar(50),full_name nvarchar(200),employment_status varchar(20))");
        jdbc.execute("create table dbo.contracts(contract_id bigint primary key,employee_id bigint not null,start_date date not null,end_date date,status varchar(20))");
        jdbc.execute("create index IX_contracts_employee_dates on dbo.contracts(employee_id,start_date,end_date)");
        service=new ContractWarningService(new ContractWarningRepository(new NamedParameterJdbcTemplate(jdbc)),CLOCK);
    }

    @AfterEach void cleanup() { memory.shutdown(); }

    private void employee(long id,String status) {
        jdbc.update("insert into dbo.employees values(?,?,?,?)",id,"SYNTHETIC"+id,"Synthetic "+id,status);
    }

    private void contract(long id,long employee,LocalDate start,LocalDate end,String status) {
        jdbc.update("insert into dbo.contracts values(?,?,?,?,?)",id,employee,start,end,status);
    }

    @Test void bothPagesCanBeEmpty() {
        var expiring=service.expiring(30,0,10);
        assertTrue(expiring.items().isEmpty());assertEquals(0,expiring.totalPages());
        var without=service.withoutActive(0,10);
        assertTrue(without.items().isEmpty());assertEquals(0,without.totalElements());
    }

    @Test void expiringIncludesTodayAndDayNButExcludesPastAndDayNPlusOne() {
        employee(1,"DANG_LAM");
        contract(1,1,TODAY.minusDays(60),TODAY.minusDays(1),"DA_KY");
        contract(2,1,TODAY.minusDays(60),TODAY,"DA_KY");
        contract(3,1,TODAY.minusDays(60),TODAY.plusDays(30),"DA_KY");
        contract(4,1,TODAY.minusDays(60),TODAY.plusDays(31),"DA_KY");
        contract(5,1,TODAY.minusDays(60),null,"DA_KY");
        var rows=service.expiring(30,0,10);
        assertEquals(List.of(2L,3L),rows.items().stream().map(r->r.contractId()).toList());
        assertEquals(List.of(0L,30L),rows.items().stream().map(r->r.daysLeft()).toList());
        assertEquals(2,rows.totalElements());
    }

    @Test void expiringFiltersFormerEmployeesAndEndedContractsAndKeepsStableTieOrder() {
        employee(1,"DANG_LAM");employee(2,"DA_NGHI");
        contract(8,1,TODAY.minusDays(10),TODAY.plusDays(5),"DA_KY");
        contract(7,1,TODAY.minusDays(10),TODAY.plusDays(5),"DA_KY");
        contract(9,1,TODAY.minusDays(10),TODAY.plusDays(5),"DA_KET_THUC");
        contract(10,2,TODAY.minusDays(10),TODAY.plusDays(5),"DA_KY");
        var first=service.expiring(30,0,1);var second=service.expiring(30,1,1);
        assertEquals(7,first.items().getFirst().contractId());assertEquals(8,second.items().getFirst().contractId());
        assertEquals(2,first.totalElements());assertEquals(2,first.totalPages());
        assertTrue(service.expiring(30,2,1).items().isEmpty());
    }

    @Test void signedFutureContractEndingInsideWindowMatchesTheSpecifiedExpiringCriteria() {
        employee(1,"DANG_LAM");
        contract(1,1,TODAY.plusDays(1),TODAY.plusDays(7),"DA_KY");
        assertEquals(1,service.expiring(30,0,10).totalElements());
    }

    @Test void datesCoveringTodayCountAsActiveIncludingBoundariesAndNullEnd() {
        for(long id=1;id<=4;id++)employee(id,"DANG_LAM");
        contract(1,1,TODAY.minusDays(30),TODAY,"DA_KY");
        contract(2,2,TODAY,null,"DA_KY");
        contract(3,3,TODAY,TODAY.plusDays(30),"DA_KY");
        // Coverage is date-based, even if a legacy status is inconsistent; no status-only inference.
        contract(4,4,TODAY.minusDays(10),TODAY.plusDays(10),"DA_KET_THUC");
        assertTrue(service.withoutActive(0,10).items().isEmpty());
    }

    @Test void expiredDays29And30And31AreDistinctAndFormerEmployeesAreExcluded() {
        for(long id=1;id<=3;id++){
            employee(id,"DANG_LAM");
            contract(id,id,TODAY.minusDays(100),TODAY.minusDays(28+id),"DA_KET_THUC");
        }
        employee(4,"DA_NGHI");
        contract(4,4,TODAY.minusDays(100),TODAY.minusDays(50),"DA_KET_THUC");
        var rows=service.withoutActive(0,10);
        assertEquals(3,rows.totalElements());
        assertEquals(List.of(29L,30L,31L),rows.items().stream().map(r->r.overdueDays()).toList());
        assertEquals(List.of(false,false,true),rows.items().stream().map(r->r.overdueOver30()).toList());
    }

    @Test void neverContractAndFutureOnlyHaveNoInventedOverdueDate() {
        employee(1,"DANG_LAM");employee(2,"DANG_LAM");
        contract(1,2,TODAY.plusDays(8),null,"DA_KY");
        contract(2,2,TODAY.plusDays(3),TODAY.plusDays(5),"DA_KY");
        var rows=service.withoutActive(0,10).items();
        assertNull(rows.getFirst().lastContractEndDate());assertNull(rows.getFirst().overdueDays());
        assertFalse(rows.getFirst().overdueOver30());assertNull(rows.getFirst().nextContractStart());
        assertNull(rows.getLast().overdueDays());assertEquals(TODAY.plusDays(3),rows.getLast().nextContractStart());
    }

    @Test void latestPastEndAndEarliestFutureStartAreIndependentAndDoNotDuplicateEmployee() {
        employee(1,"DANG_LAM");
        contract(1,1,TODAY.minusDays(100),TODAY.minusDays(40),"DA_KET_THUC");
        contract(2,1,TODAY.minusDays(39),TODAY.minusDays(3),"DA_KY");
        contract(3,1,TODAY.plusDays(4),TODAY.plusDays(30),"DA_KY");
        contract(4,1,TODAY.plusDays(2),TODAY.plusDays(3),"DA_KY");
        var page=service.withoutActive(0,10);var row=page.items().getFirst();
        assertEquals(1,page.totalElements());assertEquals(1,page.items().size());
        assertEquals(TODAY.minusDays(3),row.lastContractEndDate());assertEquals(3L,row.overdueDays());
        assertEquals(TODAY.plusDays(2),row.nextContractStart());
    }

    @Test void anActiveContractOverridesAnyOldOrFutureContractsForTheSameEmployee() {
        employee(1,"DANG_LAM");
        contract(1,1,TODAY.minusDays(80),TODAY.minusDays(40),"DA_KET_THUC");
        contract(2,1,TODAY.minusDays(5),TODAY.plusDays(5),"DA_KY");
        contract(3,1,TODAY.plusDays(10),null,"DA_KY");
        assertEquals(0,service.withoutActive(0,10).totalElements());
    }

    @Test void employeePaginationDoesNotFetchAllResultsAndPreservesCountOnEmptyLaterPage() {
        for(long id=1;id<=3;id++)employee(id,"DANG_LAM");
        var page=service.withoutActive(1,2);
        assertEquals(3,page.totalElements());assertEquals(2,page.totalPages());
        assertEquals(List.of(3L),page.items().stream().map(r->r.employeeId()).toList());
        assertEquals(3,service.withoutActive(2,2).totalElements());
        assertTrue(service.withoutActive(2,2).items().isEmpty());
    }
}
