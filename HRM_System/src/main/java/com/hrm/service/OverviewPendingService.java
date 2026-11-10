package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Current queues, including older months; source services own visibility and status rules. */
@Service
public class OverviewPendingService {
    public record Item(Long count,String state) {}
    public record Pending(Instant updatedAt,Item attendance,Item leave,Item overtimeApproval,Item overtimeActual) {}
    private final AttendancePortalService attendance;
    private final EmployeeRequestService requests;
    private final OvertimeWorkspaceService overtime;
    private final OvertimePermissionService identities;
    private final Clock clock;

    public OverviewPendingService(AttendancePortalService attendance,EmployeeRequestService requests,
            OvertimeWorkspaceService overtime,OvertimePermissionService identities,@Qualifier("hrmBusinessClock") Clock clock) {
        this.attendance=attendance;this.requests=requests;this.overtime=overtime;this.identities=identities;this.clock=clock;
    }
    public Pending get(AuthenticatedAccount principal) {
        var actor=identities.active(principal);
        if(!"ADMIN".equals(actor.roleCode())&&!"HR".equals(actor.roleCode()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return new Pending(Instant.now(clock),count(()->attendance.pendingCount(principal)),
            count(()->requests.pendingLeaveCount(principal)),
            count(()->overtime.pendingCount(principal,"approval")),count(()->overtime.pendingCount(principal,"actual")));
    }
    private Item count(LongSupplier source) {
        try{return new Item(source.getAsLong(),"READY");}
        catch(ResponseStatusException error){
            if(error.getStatusCode()==HttpStatus.FORBIDDEN)return new Item(null,"DENIED");
            if(error.getStatusCode()==HttpStatus.UNAUTHORIZED)throw error;
            return new Item(null,"ERROR");
        } catch(org.springframework.security.access.AccessDeniedException error){return new Item(null,"DENIED");}
        catch(DataAccessException error){return new Item(null,"ERROR");}
    }
}
