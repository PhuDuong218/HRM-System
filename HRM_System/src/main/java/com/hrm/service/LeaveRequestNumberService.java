package com.hrm.service;

import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** SQL Server sequences allocate numbers atomically across concurrent application instances. */
@Service
public class LeaveRequestNumberService {
 private static final Map<String,String> SEQUENCES=Map.of(
   "PN","leave_request_number_pn","PO","leave_request_number_po",
   "TS","leave_request_number_ts","KP","leave_request_number_kp","NP","leave_request_number_np");
 private final JdbcTemplate jdbc;
 public LeaveRequestNumberService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public static String prefix(String typeCode){
  return switch(typeCode==null?"":typeCode.trim().toUpperCase(Locale.ROOT)){
   case "ANNUAL","PHEP_NAM" -> "PN";
   case "SICK","NGHI_OM" -> "PO";
   case "MATERNITY","THAI_SAN" -> "TS";
   case "UNPAID","KHONG_LUONG" -> "KP";
   default -> "NP";
  };
 }
 public static String format(String prefix,long number){
  if(!SEQUENCES.containsKey(prefix)||number<=0)throw new IllegalArgumentException("Invalid leave request number");
  return prefix+"-"+String.format(Locale.ROOT,"%06d",number);
 }
 public String next(long leaveTypeId){
  String type=jdbc.queryForObject("SELECT type_code FROM dbo.leave_types WHERE leave_type_id=?",String.class,leaveTypeId);
  String prefix=prefix(type);
  // Identifier comes exclusively from this fixed allowlist, never from request data.
  Long number=jdbc.queryForObject("SELECT NEXT VALUE FOR dbo."+SEQUENCES.get(prefix),Long.class);
  if(number==null)throw new IllegalStateException("Leave request sequence returned no value");
  return format(prefix,number);
 }
}
