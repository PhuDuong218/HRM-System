package com.hrm.service;
import java.time.LocalDate;import java.util.Locale;
import com.hrm.enums.ContractTermKind;import com.hrm.exception.BusinessException;import org.springframework.http.HttpStatus;
public final class ContractGeneratedFields {
 private ContractGeneratedFields(){}
 public static LocalDate endDate(ContractTermKind kind,Integer months,LocalDate start,LocalDate supplied){
  if(start==null)throw new BusinessException(HttpStatus.BAD_REQUEST,"Cần ngày bắt đầu hợp đồng");
  if(kind==ContractTermKind.KHONG_XAC_DINH)return null;
  if(kind==ContractTermKind.XAC_DINH){if(months==null||months<1||months>36)throw new BusinessException(HttpStatus.BAD_REQUEST,"Loại hợp đồng cần số tháng từ 1 đến 36");return start.plusMonths(months).minusDays(1);}
  return supplied;
 }
 public static String number(String typeCode,long value){
  if(typeCode==null||!typeCode.matches("[A-Za-z0-9_-]+")||value<=0)throw new BusinessException(HttpStatus.BAD_REQUEST,"Mã loại hợp đồng không hợp lệ để cấp số");
  String result=typeCode.toUpperCase(Locale.ROOT)+"-"+String.format(Locale.ROOT,"%06d",value);
  if(result.length()>50)throw new BusinessException(HttpStatus.BAD_REQUEST,"Mã loại hợp đồng quá dài để cấp số hợp đồng (tối đa 50 ký tự gồm số)");return result;
 }
}
