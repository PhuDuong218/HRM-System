package com.hrm.service;
import com.hrm.enums.BatchStatus;
import com.hrm.model.*;
import com.hrm.repository.*;
import com.hrm.service.provider.*;
import com.hrm.service.engine.PayrollCalculationEngine;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PayrollReadinessWiringTest {
 @Test void lockCannotWriteBatchWhenInputsAreIncomplete(){
  var batches=mock(PayrollBatchRepository.class);var periods=mock(PayrollPeriodRepository.class);var details=mock(PayrollDetailRepository.class);var guard=mock(PayrollReadinessGuard.class);
  var batch=new PayrollBatch();batch.setPeriodId(7L);batch.setStatus(BatchStatus.NHAP);
  when(batches.findForUpdate(9L)).thenReturn(Optional.of(batch));when(details.findByBatchId(9L)).thenReturn(List.of(new PayrollDetail()));
  doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"pending attendance")).when(guard).requireReady(7L);
  var service=new PayrollBatchService(batches,periods,details,mock(PayslipService.class));ReflectionTestUtils.setField(service,"readiness",guard);
  assertThrows(ResponseStatusException.class,()->service.lockBatch(9L,2L));assertEquals(BatchStatus.NHAP,batch.getStatus());verify(batches,never()).save(any());
 }
 @Test void calculationCannotDeleteSnapshotsWhenInputsAreIncomplete(){
  var batches=mock(PayrollBatchRepository.class);var periods=mock(PayrollPeriodRepository.class);var details=mock(PayrollDetailRepository.class);var snapshots=mock(PayrollSnapshotRepository.class);var lines=mock(PayrollLineRepository.class);var guard=mock(PayrollReadinessGuard.class);
  var batch=new PayrollBatch();batch.setPeriodId(7L);batch.setStatus(BatchStatus.NHAP);when(batches.findForUpdate(9L)).thenReturn(Optional.of(batch));when(periods.findById(7L)).thenReturn(Optional.of(new PayrollPeriod()));
  doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"pending OT")).when(guard).requireReady(7L);
  var service=new PayrollExecutionService(batches,periods,details,lines,snapshots,mock(SalaryContractProvider.class),mock(AttendanceDataProvider.class),mock(PayrollCalculationEngine.class));ReflectionTestUtils.setField(service,"readiness",guard);
  assertThrows(ResponseStatusException.class,()->service.calculateBatch(9L));verifyNoInteractions(details,lines,snapshots);
 }

 @Test void closingWindowIncludesOnlyThirdThroughFifthOfNextMonth(){
  var end=java.time.LocalDate.of(2026,10,31);
  for(int day:new int[]{3,4,5})assertTrue(PayrollReadinessGuard.withinClosingWindow(end,java.time.LocalDate.of(2026,11,day)));
  for(int day:new int[]{1,2,6,30})assertFalse(PayrollReadinessGuard.withinClosingWindow(end,java.time.LocalDate.of(2026,11,day)));
  assertTrue(PayrollReadinessGuard.withinClosingWindow(java.time.LocalDate.of(2026,12,31),java.time.LocalDate.of(2027,1,3)));
 }
}
