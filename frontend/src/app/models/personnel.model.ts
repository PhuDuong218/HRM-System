/** JSON contracts corresponding to the personnel DTOs in the merged backend. Dates are ISO strings. */
export interface EmployeeAssignmentDTO {assignmentId:number;employeeId:number;employeeCode:string;employeeName:string;departmentId:number;departmentName:string;positionId:number;positionName:string;effectiveFrom:string;effectiveTo:string|null;}
export interface EmployeeTransferRequestDTO {departmentId:number;positionId:number;effectiveFrom:string;reason?:string|null;}
export interface EmployeeQualificationDTO {employeeQualificationId:number;employeeId:number;qualificationLevelId:number;levelCode:string;levelName:string;major:string|null;institution:string|null;graduationYear:number|null;certificateNumber:string|null;isPrimary:boolean;}
export interface EmployeeQualificationRequestDTO {qualificationLevelId:number;major?:string|null;institution?:string|null;graduationYear?:number|null;certificateNumber?:string|null;isPrimary?:boolean|null;}
export interface LeaveEntitlementDTO {entitlementId:number;employeeId:number;employeeName:string;leaveTypeId:number;leaveTypeName:string;periodStart:string;periodEnd:string;entitledHours:number;}
export interface LeaveEntitlementRequestDTO {employeeId:number;leaveTypeId:number;periodStart:string;periodEnd:string;entitledHours:number;}
export interface RequestDecision {approvedLastWorkingDate:string|null;note:string|null;}
export interface RequestRejection {reason:string;}
export interface LeavePolicy {consumesEntitlement:boolean;}
export interface AuditEntry {auditId:number;actorAccountId:number|null;occurredAt:string;action:string;reason:string|null;beforeDataJson:string|null;afterDataJson:string|null;}
