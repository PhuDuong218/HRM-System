package com.hrm.duong;

import java.util.*;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.duong.TestData.*;

/** No SpringBootTest, no application properties, no SQL Server driver or user URL. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModuleRepositoryIntegrationTest {
    EmbeddedDatabase memory;LocalContainerEntityManagerFactoryBean factory;EntityManagerFactory emf;
    JdbcTemplate jdbc;TransactionTemplate tx;ContractRepository contracts;ContractSalaryTermRepository terms;
    EmployeeAllowanceRepository allowances;EmployeeDecisionRepository decisions;EmployeeRepository employees;

    @BeforeAll void startOnlyInMemoryDatabase() throws Exception {
        memory=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try(var connection=memory.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"),"Only a private in-memory database is permitted");
        }
        jdbc=new JdbcTemplate(memory);jdbc.execute("set mode MSSQLServer");jdbc.execute("create schema dbo");
        factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(memory);
        factory.setPackagesToScan("com.hrm.model");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.default_schema","dbo",
            "hibernate.dialect","org.hibernate.dialect.H2Dialect","hibernate.hbm2ddl.halt_on_error","true"));
        factory.afterPropertiesSet();emf=Objects.requireNonNull(factory.getObject());
        // SQL Server generates row_version. H2 only gets a fixed test default;
        // this suite deliberately makes no claims about rowversion concurrency.
        var versioned=jdbc.queryForList("select TABLE_NAME from INFORMATION_SCHEMA.COLUMNS where TABLE_SCHEMA='DBO' and COLUMN_NAME='ROW_VERSION'",String.class);
        for(var table:versioned)jdbc.execute("alter table dbo."+table+" alter column row_version set default X'0000000000000001'");
        // The merged security entities already create roles and accounts through JPA.
        jdbc.execute("create table dbo.payroll_periods(period_id bigint primary key,period_start date,period_end date)");
        jdbc.execute("create table dbo.payroll_batches(batch_id bigint primary key,period_id bigint,status varchar(20))");
        // Complete the minimal payroll entity only inside this private test database.
        jdbc.execute("alter table dbo.payroll_details add batch_id bigint");
        jdbc.execute("alter table dbo.payroll_details add employee_id bigint");
        tx=new TransactionTemplate(new JpaTransactionManager(emf));
        var repositories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        contracts=repositories.getRepository(ContractRepository.class);terms=repositories.getRepository(ContractSalaryTermRepository.class);
        allowances=repositories.getRepository(EmployeeAllowanceRepository.class);decisions=repositories.getRepository(EmployeeDecisionRepository.class);
        employees=repositories.getRepository(EmployeeRepository.class);
    }
    @AfterAll void stopPrivateDatabase(){if(factory!=null)factory.destroy();if(memory!=null)memory.shutdown();}
    @BeforeEach void fixturesOnlyInPrivateDatabase(){
        for(var table:List.of("accounts","roles","payroll_details","payroll_batches","payroll_periods"))jdbc.update("delete from dbo."+table);
        for(var table:List.of("employee_decisions","employee_allowances","contract_salary_terms","contracts","allowance_types","contract_types","employees"))jdbc.update("delete from dbo."+table);
        for(long id:List.of(1L,2L))jdbc.update("insert into dbo.employees(employee_id,employee_code,full_name,hire_date,employment_status,created_at) values (?,?,?,?,'DANG_LAM',CURRENT_TIMESTAMP)",id,"SYNTHETIC"+id,"Synthetic",TODAY.minusYears(1));
        jdbc.update("insert into dbo.contract_types(contract_type_id,type_code,type_name,is_active,created_at) values (2,'SYNTHETIC','Synthetic',true,CURRENT_TIMESTAMP)");
        jdbc.update("insert into dbo.allowance_types(allowance_type_id,type_code,type_name,is_active,created_at) values (2,'SYNTHETIC','Synthetic',true,CURRENT_TIMESTAMP)");
        jdbc.update("insert into dbo.contracts(contract_id,employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status,created_at) values (3,1,2,'SYNTHETIC',?,?,?,'DA_KY',CURRENT_TIMESTAMP)",TODAY.minusDays(30),TODAY,TODAY.plusDays(30));
        jdbc.update("insert into dbo.contract_salary_terms(salary_term_id,contract_id,effective_from,effective_to,base_salary,pay_basis,created_at) values (4,3,?,?,10000000.1250,'THANG',CURRENT_TIMESTAMP)",TODAY,TODAY.plusDays(9));
        jdbc.update("insert into dbo.employee_allowances(employee_allowance_id,employee_id,allowance_type_id,effective_from,effective_to,calculation_basis,amount,created_at) values (5,1,2,?,?,'THEO_NGAY_CONG',30000.1250,CURRENT_TIMESTAMP)",TODAY,TODAY.plusDays(9));
    }
    void decisionRow(long id,long employeeId,String status,String effect,java.time.LocalDate apply){
        jdbc.update("insert into dbo.employee_decisions(decision_id,employee_id,decision_number,decision_type,content,issued_date,issued_by,approval_status,payroll_effect,amount,apply_date,created_at) values (?,?,?,'KHEN_THUONG','Synthetic',?,10,?,?,?,?,CURRENT_TIMESTAMP)",
            id,employeeId,"SYNTHETIC"+id,TODAY.minusDays(30),status,effect,effect.equals("NONE")?null:money("100.1250"),apply);
    }
    @Test void contractOverlapIncludesLastDayButNotFollowingDay(){tx.execute(s->{assertEquals(1,contracts.countOverlapping(1L,TODAY.plusDays(30),null));assertEquals(0,contracts.countOverlapping(1L,TODAY.plusDays(31),null));return null;});}
    @Test void overlapDoesNotLeakAcrossEmployees(){tx.execute(s->{assertEquals(0,contracts.countOverlapping(2L,TODAY,null));return null;});}
    @Test void salaryOverlapExcludesCurrentRowWhenUpdating(){tx.execute(s->{assertEquals(1,terms.countOverlapping(3L,TODAY,TODAY.plusDays(9),null));assertEquals(0,terms.countOverlapping(3L,TODAY,TODAY.plusDays(9),4L));return null;});}
    @Test void adjacentSalaryDatesAreNotOverlapping(){tx.execute(s->{assertEquals(0,terms.countOverlapping(3L,TODAY.plusDays(10),null,null));return null;});}
    @Test void allowancesOverlapIncludesLastDayAndFiltersTypeAndEmployee(){tx.execute(s->{assertEquals(1,allowances.countOverlapping(1L,2L,TODAY.plusDays(9),null,null));assertEquals(0,allowances.countOverlapping(1L,2L,TODAY.plusDays(10),null,null));assertEquals(0,allowances.countOverlapping(2L,2L,TODAY,null,null));assertEquals(0,allowances.countOverlapping(1L,999L,TODAY,null,null));return null;});}
    @Test void allowanceOwnershipQueryRejectsOtherEmployee(){tx.execute(s->{assertTrue(allowances.findByEmployeeAllowanceIdAndEmployee_EmployeeId(5L,1L).isPresent());assertTrue(allowances.findByEmployeeAllowanceIdAndEmployee_EmployeeId(5L,2L).isEmpty());return null;});}
    @Test void decisionQuerySelectsOnlyApprovedMoneyForEmployeeAndApplyDate(){decisionRow(10,1,"DA_DUYET","ADD",TODAY);decisionRow(11,1,"CHO_DUYET","ADD",TODAY);decisionRow(12,1,"TU_CHOI","DEDUCT",TODAY);decisionRow(13,1,"DA_DUYET","NONE",null);decisionRow(14,2,"DA_DUYET","ADD",TODAY);decisionRow(15,1,"DA_DUYET","ADD",TODAY.plusDays(2));tx.execute(s->{var rows=decisions.findPayrollCandidates(1L,DecisionApprovalStatus.DA_DUYET,DecisionPayrollEffect.NONE,TODAY,TODAY.plusDays(1));assertEquals(List.of(10L),rows.stream().map(x->x.getDecisionId()).toList());return null;});}
    @Test void decisionPeriodBothBoundariesAreIncluded(){decisionRow(10,1,"DA_DUYET","ADD",TODAY);decisionRow(11,1,"DA_DUYET","DEDUCT",TODAY.plusDays(1));tx.execute(s->{assertEquals(2,decisions.findPayrollCandidates(1L,DecisionApprovalStatus.DA_DUYET,DecisionPayrollEffect.NONE,TODAY,TODAY.plusDays(1)).size());return null;});}
    @Test void decisionOwnerScalarAndLockQueriesWorkInTransaction(){decisionRow(10,1,"CHO_DUYET","NONE",null);tx.execute(s->{assertEquals(1L,decisions.findEmployeeIdById(10L).orElseThrow());assertEquals(10L,decisions.findByIdForUpdate(10L).orElseThrow().getDecisionId());return null;});}
    @Test void uniqueDecisionNumberIsEnforcedByInMemoryEntitySchema(){decisionRow(10,1,"CHO_DUYET","NONE",null);assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("insert into dbo.employee_decisions(employee_id,decision_number,decision_type,content,issued_date,issued_by,approval_status,payroll_effect,created_at) values (1,'SYNTHETIC10','KY_LUAT','Duplicate',?,10,'CHO_DUYET','NONE',CURRENT_TIMESTAMP)",TODAY));}
    @Test void failedAuditRollsBackActualServiceInsertInPrivateDatabase(){
        var actors=mock(DecisionActorProvider.class);when(actors.requireManager()).thenReturn(new DecisionActor(20L,200L));
        var audit=mock(DecisionAuditService.class);doThrow(new IllegalStateException("synthetic audit failure")).when(audit).record(anyLong(),anyLong(),anyString(),anyString(),any(),any());
        var service=new EmployeeDecisionService(decisions,employees,actors,audit,mock(PayrollHistoryGuard.class),new DecisionPayrollPolicy("TEST-ONLY-v1"),CLOCK);
        var error=assertThrows(IllegalStateException.class,()->tx.execute(s->service.create(decisionRequest(DecisionPayrollEffect.NONE))));
        assertEquals("synthetic audit failure",error.getMessage());
        assertEquals(0,jdbc.queryForObject("select count(*) from dbo.employee_decisions",Integer.class));
    }
    @Test void realActorQueryRejectsInactiveAndEmployeeRole(){
        jdbc.update("insert into dbo.roles(role_id,role_code,role_name) values (1,'ADMIN','Admin'),(2,'EMPLOYEE','Employee')");
        jdbc.update("""
            insert into dbo.accounts(account_id,employee_id,role_id,is_active,username,password_hash,token_version,created_at)
            values (10,1,1,true,'synthetic-admin','test-only-unused-hash',0,CURRENT_TIMESTAMP),
                   (11,2,1,false,'synthetic-inactive','test-only-unused-hash',0,CURRENT_TIMESTAMP),
                   (12,2,2,true,'synthetic-employee','test-only-unused-hash',0,CURRENT_TIMESTAMP)
            """);
        var env=new org.springframework.mock.env.MockEnvironment();env.setActiveProfiles("local");
        assertEquals(1L,new LocalDecisionActorProvider(jdbc,env,true,10L).requireManager().employeeId());
        status(403,()->new LocalDecisionActorProvider(jdbc,env,true,11L).requireManager());
        status(403,()->new LocalDecisionActorProvider(jdbc,env,true,12L).requireManager());
    }
    @Test void realHistoryGuardProtectsClosedPeriodOnlyForThatEmployee(){
        jdbc.update("insert into dbo.payroll_periods values (8,?,?)",TODAY,TODAY.plusDays(9));
        jdbc.update("insert into dbo.payroll_batches values (9,8,'DA_CHOT')");
        jdbc.update("insert into dbo.payroll_details values (10,9,1)");
        var guard=new PayrollHistoryGuard(countBigBridge());
        status(409,()->guard.assertMutable(1L,TODAY.plusDays(9),null));
        assertDoesNotThrow(()->guard.assertMutable(1L,TODAY.plusDays(10),null));
        assertDoesNotThrow(()->guard.assertMutable(2L,TODAY,null));
    }
    @Test void actualD14ServiceReadsPeriodAndApprovedSourcesTogether(){
        jdbc.update("insert into dbo.payroll_periods values (8,?,?)",TODAY,TODAY.plusDays(9));
        decisionRow(10,1,"DA_DUYET","ADD",TODAY);decisionRow(11,1,"CHO_DUYET","ADD",TODAY);
        var service=new DecisionPayrollService(employees,decisions,new AllowancePeriodReader(jdbc),new DecisionPayrollPolicy("TEST-ONLY-v1"),countBigBridge());
        tx.execute(s->{var result=service.getForPeriod(1L,8L);assertEquals(1,result.items().size());assertEquals(money("100.1250"),result.totalAdd());return null;});
    }
    // H2 has COUNT but not SQL Server COUNT_BIG. Only this dialect spelling is
    // adapted, in test code; the service's joins/date predicates/parameters run unchanged.
    JdbcTemplate countBigBridge(){return new JdbcTemplate(memory){
        @Override public <T> T queryForObject(String sql,Class<T> type,Object... args){
            return super.queryForObject(sql.replace("count_big(","count("),type,args);
        }
    };}
}
