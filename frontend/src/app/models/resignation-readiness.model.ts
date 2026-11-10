export interface ExitRecord {id:number;label:string;from:string|null;to:string|null;}
export interface ExitIssue {code:string;title:string;count:number;instruction:string;route:string;records:ExitRecord[];}
export interface ResignationReadiness {
 requestId:number;employeeId:number;employeeCode:string;employeeName:string;lastWorkingDate:string;
 canApprove:boolean;approvalMessage:string;issues:ExitIssue[];
}
