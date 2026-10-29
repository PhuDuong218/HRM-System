package com.hrm;

import com.hrm.service.OvertimeAuthorizationRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OvertimeAdminPolicyTest {
    @Test void adminDoesNotRegisterOt(){assertFalse(OvertimeAuthorizationRules.canRegister("ADMIN"));}
    @Test void employeeAndHrMayRegister(){assertTrue(OvertimeAuthorizationRules.canRegister("EMPLOYEE"));assertTrue(OvertimeAuthorizationRules.canRegister("HR"));}
    @Test void adminMayHandleEmployeeAndHr(){assertTrue(OvertimeAuthorizationRules.canApprove("ADMIN","EMPLOYEE",1L,3L,false));assertTrue(OvertimeAuthorizationRules.canApprove("ADMIN","HR",1L,2L,false));assertTrue(OvertimeAuthorizationRules.canConfirm("ADMIN","HR",1L,2L,9L,1L,false));}
    @Test void adminCannotApproveAdminOt(){assertFalse(OvertimeAuthorizationRules.canApprove("ADMIN","ADMIN",1L,1L,true));assertFalse(OvertimeAuthorizationRules.canConfirm("ADMIN","ADMIN",1L,1L,1L,1L,true));}
    @Test void hrCannotSelfApprove(){assertFalse(OvertimeAuthorizationRules.canApprove("HR","HR",2L,2L,true));}
}
