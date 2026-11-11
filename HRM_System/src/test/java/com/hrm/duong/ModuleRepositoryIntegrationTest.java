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
import org.springframework.data.domain.PageRequest;
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
        // The merged security and payroll entities are all scanned above. Hibernate
        // creates their complete tables, including payroll detail ownership columns;
        // the old minimal-schema DDL would create the same tables/columns twice.
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
        jdbc.update("insert into dbo.contract_types(contract_type_id,type_code,type_name,is_active,term_kind,max_months,created_at) values (2,'SYNTHETIC','Synthetic',true,'XAC_DINH',36,CURRENT_TIMESTAMP)");
        jdbc.update("""
            insert into dbo.allowance_types
                (allowance_type_id,type_code,type_name,is_active,is_taxable,counts_for_social_insurance,created_at)
            values (2,'SYNTHETIC','Synthetic',true,true,false,CURRENT_TIMESTAMP)
            """);
        jdbc.update("insert into dbo.contracts(contract_id,employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status,created_at) values (3,1,2,'SYNTHETIC',?,?,?,'DA_KY',CURRENT_TIMESTAMP)",TODAY.minusDays(30),TODAY,TODAY.plusDays(30));
        jdbc.update("insert into dbo.contract_salary_terms(salary_term_id,contract_id,effective_from,effective_to,base_salary,pay_basis,created_at) values (4,3,?,?,10000000.1250,'THANG',CURRENT_TIMESTAMP)",TODAY,TODAY.plusDays(9));
        jdbc.update("""
            insert into dbo.employee_allowances
                (employee_allowance_id,employee_id,allowance_type_id,effective_from,effective_to,calculation_basis,amount,amount_type,created_at)
            values (5,1,2,?,?,'THEO_NGAY_CONG',30000.1250,'FIXED',CURRENT_TIMESTAMP)
            """,TODAY,TODAY.plusDays(9));
    }
    void decisionRow(long id,long employeeId,String status,String effect,java.time.LocalDate apply){
        jdbc.update("insert into dbo.employee_decisions(decision_id,employee_id,decision_number,decision_type,content,issued_date,issued_by,approval_status,payroll_effect,amount,apply_date,created_at) values (?,?,?,'KHEN_THUONG','Synthetic',?,10,?,?,?,?,CURRENT_TIMESTAMP)",
            id,employeeId,"SYNTHETIC"+id,TODAY.minusDays(30),status,effect,effect.equals("NONE")?null:money("100.1250"),apply);
    }
    @Test void personalQueriesKeepTwoEmployeesAndDecisionStatusesSeparate() {
        jdbc.update("""
            insert into dbo.contracts
                (contract_id,employee_id,contract_type_id,contract_number,signed_date,start_date,status,created_at)
            values (103,2,2,'SYNTHETIC-B',?,?,'DA_KY',CURRENT_TIMESTAMP)
            """,TODAY.minusDays(30),TODAY);
        jdbc.update("""
            insert into dbo.contract_salary_terms
                (salary_term_id,contract_id,effective_from,base_salary,pay_basis,created_at)
            values (104,103,?,20000000,'THANG',CURRENT_TIMESTAMP)
            """,TODAY);
        jdbc.update("""
            insert into dbo.employee_allowances
                (employee_allowance_id,employee_id,allowance_type_id,effective_from,calculation_basis,amount,amount_type,created_at)
            values (105,2,2,?,'THEO_KY',8,'PERCENT_BASE_SALARY',CURRENT_TIMESTAMP)
            """,TODAY);
        decisionRow(10,1,"DA_DUYET","ADD",TODAY);
        decisionRow(11,1,"CHO_DUYET","ADD",TODAY);
        decisionRow(12,1,"TU_CHOI","ADD",TODAY);
        decisionRow(13,2,"DA_DUYET","ADD",TODAY);
        var a = new com.hrm.security.AuthenticatedAccount(11L,1L,"employee-a","EMPLOYEE");
        var b = new com.hrm.security.AuthenticatedAccount(22L,2L,"employee-b","HR");
        var personal = new MyEmploymentService(contracts,terms,allowances,decisions);

        tx.executeWithoutResult(transaction -> {
            var aContracts = personal.contracts(a,0,1);
            assertEquals(1,aContracts.totalElements());
            assertEquals(3L,aContracts.items().getFirst().contractId());
            assertTrue(personal.contracts(a,1,1).items().isEmpty());
            assertEquals(4L,personal.contractDetail(a,3L).salaryTerms().getFirst().salaryTermId());
            status(404,()->personal.contractDetail(a,103L));
            assertEquals(103L,personal.contracts(b,0,1).items().getFirst().contractId());
            assertEquals(104L,personal.contractDetail(b,103L).salaryTerms().getFirst().salaryTermId());
            status(404,()->personal.contractDetail(b,3L));
            assertEquals(List.of(5L),personal.allowances(a).stream().map(MyAllowanceResponse::employeeAllowanceId).toList());
            assertEquals(List.of(105L),personal.allowances(b).stream().map(MyAllowanceResponse::employeeAllowanceId).toList());
            assertEquals(AllowanceAmountType.PERCENT_BASE_SALARY,personal.allowances(b).getFirst().amountType());
            assertEquals(List.of(10L),personal.decisions(a,0,10).items().stream().map(MyDecisionResponse::decisionId).toList());
            assertEquals(List.of(13L),personal.decisions(b,0,10).items().stream().map(MyDecisionResponse::decisionId).toList());
        });
    }

    @Test void payrollSelectionIncludesFormerEmployeeOnTheirLastDay() {
        jdbc.update("update dbo.employees set employment_status='DA_NGHI', termination_date=? where employee_id=1",
                TODAY.plusDays(1));
        jdbc.update("update dbo.employees set hire_date=? where employee_id=2",TODAY.plusDays(2));

        tx.executeWithoutResult(transaction -> {
            assertEquals(List.of(1L,2L),employees.findEmployedDuring(TODAY,TODAY.plusDays(2))
                    .stream().map(employee -> employee.getEmployeeId()).toList());
            assertEquals(List.of(1L),employees.findEmployedDuring(TODAY.plusDays(1),TODAY.plusDays(1))
                    .stream().map(employee -> employee.getEmployeeId()).toList());
            assertEquals(List.of(2L),employees.findEmployedDuring(TODAY.plusDays(2),TODAY.plusDays(2))
                    .stream().map(employee -> employee.getEmployeeId()).toList());
        });
    }

    @Test void payrollPeriodLookupRequiresExactAndUnambiguousDates() {
        var reader = new AllowancePeriodReader(jdbc);
        var end = TODAY.plusDays(9);
        jdbc.update("insert into dbo.payroll_periods(period_id,period_code,period_start,period_end,created_at) values (8,'PERIOD-A',?,?,CURRENT_TIMESTAMP)",
                TODAY,end);
        assertEquals(new AllowancePeriod(8L,TODAY,end),reader.getByDates(TODAY,end));
        status(404,()->reader.getByDates(TODAY.plusDays(1),end));
        jdbc.update("insert into dbo.payroll_periods(period_id,period_code,period_start,period_end,created_at) values (9,'PERIOD-B',?,?,CURRENT_TIMESTAMP)",
                TODAY,end);
        status(409,()->reader.getByDates(TODAY,end));
    }

    @Test void payrollPreparationKeepsValidEmployeeAfterAnotherEmployeeFails() {
        var end = TODAY.plusDays(9);
        jdbc.update("insert into dbo.payroll_periods(period_id,period_code,period_start,period_end,created_at) values (8,'INPUT-PREP',?,?,CURRENT_TIMESTAMP)",
                TODAY,end);
        jdbc.update("delete from dbo.contract_salary_terms where salary_term_id=4");
        jdbc.update("delete from dbo.contracts where contract_id=3");
        jdbc.update("update dbo.employees set employment_status='DA_NGHI', termination_date=? where employee_id=2",
                TODAY.plusDays(5));
        jdbc.update("""
            insert into dbo.contracts
                (contract_id,employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status,created_at)
            values (103,2,2,'INPUT-PREP-B',?,?,?,'DA_KET_THUC',CURRENT_TIMESTAMP)
            """,TODAY.minusDays(30),TODAY,TODAY.plusDays(5));
        jdbc.update("""
            insert into dbo.contract_salary_terms
                (salary_term_id,contract_id,effective_from,effective_to,base_salary,pay_basis,created_at)
            values (104,103,?,?,10000000,'THANG',CURRENT_TIMESTAMP),
                   (105,103,?,?,12000000,'THANG',CURRENT_TIMESTAMP)
            """,TODAY,TODAY.plusDays(2),TODAY.plusDays(3),TODAY.plusDays(5));
        jdbc.update("""
            insert into dbo.employee_allowances
                (employee_allowance_id,employee_id,allowance_type_id,effective_from,calculation_basis,amount,amount_type,created_at)
            values (106,2,2,?,'THEO_KY',500000,'FIXED',CURRENT_TIMESTAMP)
            """,TODAY);
        decisionRow(107,2,"DA_DUYET","ADD",TODAY);

        var periods = new AllowancePeriodReader(jdbc);
        var rates = new SalaryRateService(employees,contracts,terms);
        var allowanceReader = new AllowanceCalculationService(employees,allowances,periods,
                (id,from,to) -> { throw new AssertionError("Calendar allowance must not load work data"); },
                rates,"CALENDAR_DAY_PRORATION","TEST-ALLOWANCE");
        var decisionReader = new DecisionPayrollService(employees,decisions,periods,
                new DecisionPayrollPolicy("TEST-DECISIONS"),countBigBridge());
        var preparation = new PayrollInputPreparationService(employees,periods,rates,allowanceReader,
                decisionReader,tx.getTransactionManager());

        tx.executeWithoutResult(outer -> {
            var report = preparation.prepare(TODAY,end);
            assertFalse(outer.isRollbackOnly());
            assertEquals(1,report.issues().size());
            var issue = report.issues().getFirst();
            assertEquals(1L,issue.employeeId());
            assertEquals(PayrollInputPreparationService.Stage.SALARY,issue.stage());
            assertEquals(409,issue.status());
            assertEquals(1,report.employees().size());
            var valid = report.employees().getFirst();
            assertEquals(2L,valid.employeeId());
            assertEquals(TODAY.plusDays(5),valid.employedTo());
            assertEquals(List.of(104L,105L),valid.salarySegments().stream().map(SalaryRateSegment::salaryTermId).toList());
            assertEquals(money("300000"),valid.allowances().totalRounded());
            assertEquals(1,valid.decisions().items().size());
        });

        jdbc.update("update dbo.contract_salary_terms set effective_from=? where salary_term_id=105",
                TODAY.plusDays(2));
        var overlapReport = preparation.prepare(TODAY,end);
        assertTrue(overlapReport.employees().isEmpty());
        assertEquals(List.of(1L,2L),overlapReport.issues().stream()
                .map(PayrollInputPreparationService.EmployeeIssue::employeeId).toList());
        assertTrue(overlapReport.issues().getLast().message().contains("có 2"));
    }

    @Test void contractOverlapIncludesLastDayButNotFollowingDay(){tx.execute(s->{assertEquals(1,contracts.countOverlapping(1L,TODAY.plusDays(30),null));assertEquals(0,contracts.countOverlapping(1L,TODAY.plusDays(31),null));return null;});}

    @Test void cancelledContractRemainsInHistoryButIsExcludedFromEffectiveQueries() {
        var guard = new PayrollHistoryGuard(countBigBridge());
        var reader = new ContractService(contracts, mock(ContractTypeRepository.class), employees,
                mock(ContractAuditService.class), guard, new ContractTermPolicy(contracts,30,180,60,30,6));
        var lifecycle = new ContractLifecycleService(contracts, employees, reader,
                mock(BusinessAuditService.class), guard, CLOCK);
        tx.executeWithoutResult(s -> lifecycle.terminateForResignation(1L, TODAY.minusDays(1),
                TerminationReasonType.THOA_THUAN, "Synthetic resignation"));

        tx.executeWithoutResult(s -> {
            var history = reader.getOne(3L);
            assertEquals(ContractStatus.DA_HUY, history.status());
            assertEquals(TODAY, history.startDate());
            assertEquals(TODAY.plusDays(30), history.endDate());
            assertEquals(0, contracts.countOverlapping(1L,TODAY,TODAY));
            assertTrue(contracts.findIntersecting(1L,TODAY,TODAY).isEmpty());
        });
        var warning = new ContractWarningRepository(
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        for (var reportDate : List.of(TODAY.minusDays(1), TODAY, TODAY.plusDays(31))) {
            var row = warning.findWithoutActive(reportDate, 0, 10).stream()
                    .filter(result -> result.employeeId().equals(1L)).findFirst().orElseThrow();
            assertNull(row.nextContractStart());
            assertNull(row.lastContractEndDate());
        }
    }

    @Test void blockedAllowanceRollsBackEarlierResignationTermination() {
        var fixture = terminationFixture();
        var guard = new PayrollHistoryGuard(countBigBridge());
        var allowanceService = new EmployeeAllowanceService(allowances, mock(AllowanceTypeRepository.class),
                employees, fixture.audit(), guard, CLOCK);
        // Existing contract ends before the closed period; the open allowance reaches it.
        jdbc.update("update dbo.contracts set end_date=? where contract_id=3", TODAY.minusDays(2));
        jdbc.update("update dbo.employee_allowances set effective_from=?, effective_to=null where employee_allowance_id=5",
                TODAY.minusDays(20));
        jdbc.update("insert into dbo.payroll_periods(period_id,period_code,period_start,period_end,created_at) values (8,'RESIGN-CLOSED',?,?,CURRENT_TIMESTAMP)", TODAY,TODAY.plusDays(1));
        jdbc.update("insert into dbo.payroll_batches(batch_id,period_id,version_no,status,created_at) values (9,8,1,'DA_CHOT',CURRENT_TIMESTAMP)");
        jdbc.update("insert into dbo.payroll_details(payroll_detail_id,batch_id,employee_id,created_at) values (10,9,1,CURRENT_TIMESTAMP)");
        var lastDay = TODAY.minusDays(5);

        status(409, () -> tx.executeWithoutResult(s -> {
            fixture.lifecycle().terminateForResignation(1L, lastDay, TerminationReasonType.THOA_THUAN, "Synthetic resignation");
            assertEquals("DA_KET_THUC", jdbc.queryForObject("select status from dbo.contracts where contract_id=3",String.class));
            allowanceService.endAllFrom(1L, lastDay.plusDays(1), "Synthetic resignation");
        }));

        assertEquals("DA_KY", jdbc.queryForObject("select status from dbo.contracts where contract_id=3", String.class));
        assertEquals(TODAY.minusDays(2), jdbc.queryForObject("select end_date from dbo.contracts where contract_id=3",java.sql.Date.class).toLocalDate());
        assertNull(jdbc.queryForObject("select effective_to from dbo.employee_allowances where employee_allowance_id=5",java.sql.Date.class));
    }
    @Test void overlapDoesNotLeakAcrossEmployees(){tx.execute(s->{assertEquals(0,contracts.countOverlapping(2L,TODAY,null));return null;});}
    @Test void contractSearchFiltersBeforePaginationAndCount(){
        jdbc.update("insert into dbo.contracts(contract_id,employee_id,contract_type_id,contract_number,signed_date,start_date,end_date,status,created_at) values (4,2,2,'OTHER',?,?,?,'DA_KY',CURRENT_TIMESTAMP)",TODAY,TODAY,TODAY.plusDays(30));
        tx.execute(s->{
            var page=contracts.searchScoped(null,null,"%",null,null,List.of(1L),PageRequest.of(0,1));
            assertEquals(1,page.getTotalElements());assertEquals(1L,page.getContent().get(0).getEmployee().getEmployeeId());
            assertEquals(0,contracts.searchScoped(2L,null,"%",null,null,List.of(1L),PageRequest.of(0,1)).getTotalElements());
            assertEquals(0,contracts.searchScoped(null,null,"%",null,null,List.of(-1L),PageRequest.of(0,1)).getTotalElements());
            return null;
        });
    }
    @Test void contractKeywordMatchesContractNumberEmployeeAndTypeWithinOtherFilters() {
        jdbc.update("update dbo.employees set employee_code='EMP-QA', full_name=? where employee_id=1", "Nguyễn Minh Khôi");
        jdbc.update("update dbo.contract_types set type_code='FIXED-TERM', type_name=? where contract_type_id=2", "Xác định thời hạn");
        jdbc.update("""
                insert into dbo.contracts
                    (contract_id, employee_id, contract_type_id, contract_number, signed_date, start_date, status, created_at)
                values (4,2,2,'OTHER',?,?,'DA_KY',CURRENT_TIMESTAMP)
                """, TODAY, TODAY);

        var firstPage = PageRequest.of(0, 1);
        tx.executeWithoutResult(transaction -> {
            for (String pattern : List.of("%synthetic%", "%emp-qa%", "%minh khôi%", "%fixed-term%", "%thời hạn%")) {
                var scoped = contracts.searchScoped(null, ContractStatus.DA_KY, pattern, TODAY, TODAY,
                        List.of(1L), firstPage);
                assertEquals(1, scoped.getTotalElements(), pattern);
                assertEquals(3L, scoped.getContent().getFirst().getContractId(), pattern);
            }
            var all = contracts.search(null, null, "%thời hạn%", TODAY, TODAY, firstPage);
            assertEquals(2, all.getTotalElements());
            assertEquals(0, contracts.searchScoped(null, null, "%other%", null, null,
                    List.of(1L), firstPage).getTotalElements());
            assertEquals(0, contracts.search(null, ContractStatus.DA_KET_THUC, "%emp-qa%", TODAY, TODAY,
                    firstPage).getTotalElements());
            assertEquals(0, contracts.search(null, null, "%minh khôi%", TODAY.plusDays(31), null,
                    firstPage).getTotalElements());
        });
    }

    @Test void contractSearchIncludesDateBoundariesAndOpenEndedContractsBeforePagination() {
        jdbc.update("""
                insert into dbo.contracts
                    (contract_id, employee_id, contract_type_id, contract_number, signed_date, start_date, status, created_at)
                values (4,2,2,'OPEN',?,?,'DA_KY',CURRENT_TIMESTAMP)
                """, TODAY, TODAY.plusDays(30));
        var boundary = TODAY.plusDays(30);
        var firstPage = PageRequest.of(0, 1);
        tx.executeWithoutResult(status -> {
            var both = contracts.search(null, ContractStatus.DA_KY, "%", boundary, boundary, firstPage);
            assertEquals(2, both.getTotalElements());
            assertEquals(2, both.getTotalPages());
            var scoped = contracts.searchScoped(null, ContractStatus.DA_KY, "%", boundary, boundary,
                    List.of(1L), firstPage);
            assertEquals(1, scoped.getTotalElements());
            assertEquals(3L, scoped.getContent().getFirst().getContractId());
            var fromOnly = contracts.search(null, null, "%", boundary.plusDays(1), null, firstPage);
            assertEquals(1, fromOnly.getTotalElements());
            assertEquals(4L, fromOnly.getContent().getFirst().getContractId());
            var toOnly = contracts.search(null, null, "%", null, boundary.minusDays(1), firstPage);
            assertEquals(1, toOnly.getTotalElements());
            assertEquals(3L, toOnly.getContent().getFirst().getContractId());
        });
    }
    @Test void decisionSearchFiltersBeforePaginationAndCount(){
        decisionRow(10,1,"CHO_DUYET","NONE",null);decisionRow(11,2,"CHO_DUYET","NONE",null);
        tx.execute(s->{
            var page=decisions.searchScoped(null,null,List.of(1L),PageRequest.of(0,1));
            assertEquals(1,page.getTotalElements());assertEquals(1L,page.getContent().get(0).getEmployee().getEmployeeId());
            assertEquals(0,decisions.searchScoped(2L,null,List.of(1L),PageRequest.of(0,1)).getTotalElements());
            assertEquals(0,decisions.searchScoped(null,null,List.of(-1L),PageRequest.of(0,1)).getTotalElements());
            return null;
        });
    }
    @Test void salaryOverlapExcludesCurrentRowWhenUpdating(){tx.execute(s->{assertEquals(1,terms.countOverlapping(3L,TODAY,TODAY.plusDays(9),null));assertEquals(0,terms.countOverlapping(3L,TODAY,TODAY.plusDays(9),4L));return null;});}
    @Test void oneDayRangesIntersectOnlyOnTheirEffectiveDay() {
        jdbc.update("update dbo.contract_salary_terms set effective_to=effective_from where salary_term_id=4");
        jdbc.update("update dbo.employee_allowances set effective_to=effective_from where employee_allowance_id=5");
        tx.executeWithoutResult(s -> {
            var next = TODAY.plusDays(1);
            assertEquals(1, terms.countOverlapping(3L, TODAY, TODAY, null));
            assertEquals(0, terms.countOverlapping(3L, next, next, null));
            assertEquals(1, terms.findIntersecting(1L, TODAY, TODAY).size());
            assertTrue(terms.findIntersecting(1L, next, next).isEmpty());
            assertEquals(1, allowances.countOverlapping(1L, 2L, TODAY, TODAY, null));
            assertEquals(0, allowances.countOverlapping(1L, 2L, next, next, null));
            assertEquals(1, allowances.findIntersecting(1L, TODAY, TODAY).size());
            assertTrue(allowances.findIntersecting(1L, next, next).isEmpty());
        });
    }
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
        var service=new EmployeeDecisionService(decisions,employees,actors,audit,mock(PayrollHistoryGuard.class),new DecisionPayrollPolicy("TEST-ONLY-v1"),CLOCK,TestData.numbers());
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
        jdbc.update("insert into dbo.payroll_periods(period_id,period_code,period_start,period_end,created_at) values (8,'SYNTHETIC8',?,?,CURRENT_TIMESTAMP)",TODAY,TODAY.plusDays(9));
        jdbc.update("insert into dbo.payroll_batches(batch_id,period_id,version_no,status,created_at) values (9,8,1,'DA_CHOT',CURRENT_TIMESTAMP)");
        jdbc.update("insert into dbo.payroll_details(payroll_detail_id,batch_id,employee_id,created_at) values (10,9,1,CURRENT_TIMESTAMP)");
        var guard=new PayrollHistoryGuard(countBigBridge());
        status(409,()->guard.assertMutable(1L,TODAY.plusDays(9),null));
        assertDoesNotThrow(()->guard.assertMutable(1L,TODAY.plusDays(10),null));
        assertDoesNotThrow(()->guard.assertMutable(2L,TODAY,null));
    }
    @Test void actualD14ServiceReadsPeriodAndApprovedSourcesTogether(){
        jdbc.update("insert into dbo.payroll_periods(period_id,period_code,period_start,period_end,created_at) values (8,'SYNTHETIC8',?,?,CURRENT_TIMESTAMP)",TODAY,TODAY.plusDays(9));
        decisionRow(10,1,"DA_DUYET","ADD",TODAY);decisionRow(11,1,"CHO_DUYET","ADD",TODAY);
        var service=new DecisionPayrollService(employees,decisions,new AllowancePeriodReader(jdbc),new DecisionPayrollPolicy("TEST-ONLY-v1"),countBigBridge());
        tx.execute(s->{var result=service.getForPeriod(1L,8L);assertEquals(1,result.items().size());assertEquals(money("100.1250"),result.totalAdd());return null;});
    }
    private record TerminationFixture(ContractLifecycleService lifecycle,BusinessAuditService audit) {}

    private TerminationFixture terminationFixture() {
        var audit=mock(BusinessAuditService.class);
        var guard=new PayrollHistoryGuard(countBigBridge());
        var reader=new ContractService(contracts,mock(ContractTypeRepository.class),employees,
            mock(ContractAuditService.class),guard,new ContractTermPolicy(contracts,30,180,60,30,6));
        var lifecycle=new ContractLifecycleService(contracts,employees,reader,audit,guard,CLOCK);
        org.springframework.test.util.ReflectionTestUtils.setField(lifecycle,"em",SharedEntityManagerCreator.createSharedEntityManager(emf));
        jdbc.update("update dbo.contracts set start_date=? where contract_id=3",TODAY.minusDays(30));
        return new TerminationFixture(lifecycle,audit);
    }

    private ContractTerminateRequest retrospectiveTermination() {
        return new ContractTerminateRequest(TODAY.minusDays(5),"Synthetic retrospective test",TerminationReasonType.THOA_THUAN);
    }

    @Test void retrospectiveTerminationCommitsReasonAndKeepsSalaryAndAllowanceHistory() {
        var fixture=terminationFixture();
        jdbc.update("update dbo.contract_salary_terms set effective_to=null where salary_term_id=4");
        var result=tx.execute(s->fixture.lifecycle().terminate(3L,retrospectiveTermination()));
        assertEquals(ContractStatus.DA_KET_THUC,result.status());
        assertEquals(TerminationReasonType.THOA_THUAN,result.reasonType());
        assertEquals("THOA_THUAN",jdbc.queryForObject("select termination_reason_type from dbo.contracts where contract_id=3",String.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from dbo.contract_salary_terms where salary_term_id=4 and effective_to is null",Integer.class));
        assertEquals(TODAY.plusDays(9),jdbc.queryForObject("select effective_to from dbo.employee_allowances where employee_allowance_id=5",java.sql.Date.class).toLocalDate());
        var rates=new SalaryRateService(employees,contracts,terms);
        status(409,()->tx.execute(s->rates.getRateAt(1L,TODAY)));
    }

    @Test void closedPayrollInRemovedDaysBlocksActualTerminationAndLeavesDatabaseUnchanged() {
        var fixture=terminationFixture();
        jdbc.update("insert into dbo.payroll_periods(period_id,period_code,period_start,period_end,created_at) values (8,'TERM-CLOSED',?,?,CURRENT_TIMESTAMP)",TODAY.minusDays(4),TODAY.plusDays(1));
        jdbc.update("insert into dbo.payroll_batches(batch_id,period_id,version_no,status,created_at) values (9,8,1,'DA_CHOT',CURRENT_TIMESTAMP)");
        jdbc.update("insert into dbo.payroll_details(payroll_detail_id,batch_id,employee_id,created_at) values (10,9,1,CURRENT_TIMESTAMP)");
        status(409,()->tx.execute(s->fixture.lifecycle().terminate(3L,retrospectiveTermination())));
        assertEquals(TODAY.plusDays(30),jdbc.queryForObject("select end_date from dbo.contracts where contract_id=3",java.sql.Date.class).toLocalDate());
        assertEquals("DA_KY",jdbc.queryForObject("select status from dbo.contracts where contract_id=3",String.class));
        assertNull(jdbc.queryForObject("select termination_reason_type from dbo.contracts where contract_id=3",String.class));
    }

    @Test void closedPayrollEndingAtTerminationDateDoesNotBlockUnchangedHistory() {
        var fixture=terminationFixture();
        jdbc.update("insert into dbo.payroll_periods(period_id,period_code,period_start,period_end,created_at) values (8,'TERM-BEFORE',?,?,CURRENT_TIMESTAMP)",TODAY.minusDays(10),TODAY.minusDays(5));
        jdbc.update("insert into dbo.payroll_batches(batch_id,period_id,version_no,status,created_at) values (9,8,1,'DA_CHOT',CURRENT_TIMESTAMP)");
        jdbc.update("insert into dbo.payroll_details(payroll_detail_id,batch_id,employee_id,created_at) values (10,9,1,CURRENT_TIMESTAMP)");
        assertEquals(ContractStatus.DA_KET_THUC,tx.execute(s->fixture.lifecycle().terminate(3L,retrospectiveTermination())).status());
    }

    @Test void auditFailureRollsBackTerminationDateStatusAndReasonTogether() {
        var fixture=terminationFixture();
        doThrow(new IllegalStateException("synthetic audit failure")).when(fixture.audit())
            .record(eq("contracts"),eq(3L),eq("CONTRACT_TERMINATE"),anyString(),any(),any());
        assertThrows(IllegalStateException.class,()->tx.execute(s->fixture.lifecycle().terminate(3L,retrospectiveTermination())));
        assertEquals(TODAY.plusDays(30),jdbc.queryForObject("select end_date from dbo.contracts where contract_id=3",java.sql.Date.class).toLocalDate());
        assertEquals("DA_KY",jdbc.queryForObject("select status from dbo.contracts where contract_id=3",String.class));
        assertNull(jdbc.queryForObject("select termination_reason_type from dbo.contracts where contract_id=3",String.class));
    }

    // H2 has COUNT but not SQL Server COUNT_BIG. Only this dialect spelling is
    // adapted, in test code; the service's joins/date predicates/parameters run unchanged.
    JdbcTemplate countBigBridge(){return new JdbcTemplate(memory){
        @Override public <T> T queryForObject(String sql,Class<T> type,Object... args){
            return super.queryForObject(sql.replace("count_big(","count("),type,args);
        }
    };}
}
