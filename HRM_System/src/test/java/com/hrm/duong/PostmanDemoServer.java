package com.hrm.duong;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import com.sun.net.httpserver.HttpServer;
import com.hrm.controller.*;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.*;
import com.hrm.exception.GlobalExceptionHandler;
import org.springframework.data.domain.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static com.hrm.duong.TestData.*;

/** Test-classpath-only demo. No application context, DataSource, SQL, scheduling or real account. */
public final class PostmanDemoServer {
    static MockMvc fixture(){
        var employees=mock(EmployeeRepository.class);
        when(employees.findByIdForUpdate(anyLong())).thenAnswer(x->{long n=x.getArgument(0);return n==1||n==2?Optional.of(employee(n)):Optional.empty();});
        when(employees.existsById(anyLong())).thenAnswer(x->{long n=x.getArgument(0);return n==1||n==2;});
        var contractRepo=mock(ContractRepository.class);var typeRepo=mock(ContractTypeRepository.class);
        var c=contract(3,TODAY.withDayOfMonth(1),TODAY.withDayOfMonth(31));
        when(contractRepo.findById(3L)).thenReturn(Optional.of(c));when(typeRepo.findAll()).thenReturn(List.of(contractType()));
        when(contractRepo.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(c));
        var termRepo=mock(ContractSalaryTermRepository.class);
        when(termRepo.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(term(4,c,TODAY.withDayOfMonth(1),TODAY.withDayOfMonth(24),"10000000"),term(5,c,TODAY.withDayOfMonth(25),null,"12000000")));
        var contractService=new ContractService(contractRepo,typeRepo,employees,mock(ContractAuditService.class),mock(PayrollHistoryGuard.class));
        var rates=new SalaryRateService(employees,contractRepo,termRepo);
        var allowanceRepo=mock(EmployeeAllowanceRepository.class);var a=allowance(3,TODAY.withDayOfMonth(1),null,AllowanceCalculationBasis.THEO_NGAY_CONG,"30000");
        when(allowanceRepo.findByEmployee_EmployeeIdOrderByEffectiveFromAsc(1L)).thenReturn(List.of(a));
        when(allowanceRepo.findIntersecting(eq(1L),any(),any())).thenReturn(List.of(a));
        var allowances=new EmployeeAllowanceService(allowanceRepo,mock(AllowanceTypeRepository.class),employees,mock(BusinessAuditService.class),mock(PayrollHistoryGuard.class),CLOCK);
        var periods=mock(AllowancePeriodReader.class);when(periods.get(8L)).thenReturn(new AllowancePeriod(8L,TODAY.withDayOfMonth(1),TODAY.withDayOfMonth(31)));
        AllowanceWorkSource syntheticWork=(employeeId,from,to)->{
            var days=new ArrayList<EligibleAllowanceDay>();
            for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1))days.add(new EligibleAllowanceDay(d,money("1"),money("8")));
            return new AllowanceWorkData(employeeId,from,to,days,"SYNTHETIC-DEMO-NOT-REAL-ATTENDANCE","TEST-ONLY-v1");
        };
        var calculator=new AllowanceCalculationService(employees,allowanceRepo,periods,syntheticWork,"UNCONFIRMED","UNCONFIRMED");
        var decisions=mock(EmployeeDecisionRepository.class);var state=new HashMap<Long,EmployeeDecision>();var sequence=new AtomicLong(100);
        var approved=decision(9,DecisionPayrollEffect.ADD);approved.setAmount(money("500000"));approved.setApprovalStatus(DecisionApprovalStatus.DA_DUYET);approved.setApprovedBy(20L);approved.setApprovedAt(java.time.LocalDateTime.now(CLOCK));
        var pending=decision(7,DecisionPayrollEffect.ADD);pending.setAmount(money("250000"));
        state.put(7L,pending);state.put(8L,decision(8,DecisionPayrollEffect.DEDUCT));state.put(9L,approved);
        when(decisions.findById(anyLong())).thenAnswer(x->Optional.ofNullable(state.get(x.getArgument(0))));
        when(decisions.findEmployeeIdById(anyLong())).thenAnswer(x->{var d=state.get(x.getArgument(0));return d==null?Optional.empty():Optional.of(d.getEmployee().getEmployeeId());});
        when(decisions.findByIdForUpdate(anyLong())).thenAnswer(x->Optional.ofNullable(state.get(x.getArgument(0))));
        when(decisions.existsByDecisionNumber(anyString())).thenAnswer(x->state.values().stream().anyMatch(d->d.getDecisionNumber().equals(x.getArgument(0))));
        when(decisions.saveAndFlush(any())).thenAnswer(x->{var d=(EmployeeDecision)x.getArgument(0);if(d.getDecisionId()==null)id(d,"decisionId",sequence.incrementAndGet());state.put(d.getDecisionId(),d);return d;});
        doAnswer(x->{state.remove(((EmployeeDecision)x.getArgument(0)).getDecisionId());return null;}).when(decisions).delete(any(EmployeeDecision.class));
        when(decisions.findPayrollCandidates(anyLong(),any(),any(),any(),any())).thenAnswer(x->state.values().stream().filter(d->d.getEmployee().getEmployeeId().equals(x.getArgument(0))&&d.getApprovalStatus()==x.getArgument(1)&&d.getPayrollEffect()!=DecisionPayrollEffect.NONE&&d.getApplyDate()!=null&&!d.getApplyDate().isBefore(x.getArgument(3))&&!d.getApplyDate().isAfter(x.getArgument(4))).sorted(Comparator.comparing(EmployeeDecision::getDecisionId)).toList());
        DecisionActorProvider actors=()->new DecisionActor(20L,200L);
        var policy=new DecisionPayrollPolicy("TEST-ONLY-v1");
        var decisionService=new EmployeeDecisionService(decisions,employees,actors,mock(DecisionAuditService.class),mock(PayrollHistoryGuard.class),policy,CLOCK);
        var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any(),any())).thenReturn(0L);
        var payroll=new DecisionPayrollService(employees,decisions,periods,policy,jdbc);
        return MockMvcBuilders.standaloneSetup(new ContractTypeController(new ContractTypeService(typeRepo)),
            new ContractController(contractService,mock(ContractLifecycleService.class)),
            new ContractSalaryTermController(mock(ContractSalaryTermService.class),rates),
            new EmployeeAllowanceController(allowances),new AllowanceCalculationController(calculator),
            new EmployeeDecisionController(decisionService),new DecisionPayrollController(payroll,actors))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    public static void main(String[] args) throws Exception {
        var mvc=fixture();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",2006),0);
        server.createContext("/",exchange->{
            try{
                exchange.getResponseHeaders().set("X-HRM-Demo","in-memory-only");
                if(exchange.getRequestURI().getPath().equals("/__demo/health")){
                    var body="{\"mode\":\"in-memory-only\",\"database\":\"none\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
                }else{
                    var request=MockMvcRequestBuilders.request(HttpMethod.valueOf(exchange.getRequestMethod()),exchange.getRequestURI().getPath());
                    if(exchange.getRequestHeaders().getFirst("Content-Type")!=null)request.contentType(exchange.getRequestHeaders().getFirst("Content-Type"));
                    var query=exchange.getRequestURI().getRawQuery();if(query!=null)for(var pair:query.split("&")){var kv=pair.split("=",2);request.param(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),kv.length==2?URLDecoder.decode(kv[1],StandardCharsets.UTF_8):"");}
                    request.content(exchange.getRequestBody().readAllBytes());var response=mvc.perform(request).andReturn().getResponse();
                    if(response.getContentType()!=null)exchange.getResponseHeaders().set("Content-Type",response.getContentType());
                    var body=response.getContentAsByteArray();exchange.sendResponseHeaders(response.getStatus(),body.length==0?-1:body.length);
                    if(body.length>0)exchange.getResponseBody().write(body);
                }
            }catch(Exception e){exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}
        });
        Runtime.getRuntime().addShutdownHook(new Thread(()->server.stop(0)));
        server.start();System.out.println("D15/D16 demo ready at http://127.0.0.1:2006; synthetic repositories; NO DATABASE; fixed test actor 20; restart before each collection run.");
        new CountDownLatch(1).await();
    }
    private PostmanDemoServer(){}
}
