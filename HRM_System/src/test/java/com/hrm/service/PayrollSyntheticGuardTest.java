package com.hrm.service;

import com.hrm.exception.BusinessException;
import com.hrm.repository.*;
import com.hrm.service.engine.PayrollCalculationEngine;
import com.hrm.service.provider.*;
import com.hrm.service.provider.impl.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollSyntheticGuardTest {
 @Test void syntheticInputsAreRejectedBeforeReadingOrDeletingAnyBatch(){
  var batches=mock(PayrollBatchRepository.class);var periods=mock(PayrollPeriodRepository.class);
  var details=mock(PayrollDetailRepository.class);var lines=mock(PayrollLineRepository.class);
  var snapshots=mock(PayrollSnapshotRepository.class);var engine=mock(PayrollCalculationEngine.class);
  var service=new PayrollExecutionService(batches,periods,details,lines,snapshots,
      new MockSalaryContractProvider(),new MockAttendanceDataProvider(),engine);
  var error=assertThrows(BusinessException.class,()->service.calculateBatch(1L));
  assertEquals(409,error.getStatus().value());
  verifyNoInteractions(batches,periods,details,lines,snapshots,engine);
 }
}
