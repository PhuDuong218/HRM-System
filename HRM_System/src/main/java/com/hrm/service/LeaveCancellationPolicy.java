package com.hrm.service;
import java.time.LocalDateTime;
public final class LeaveCancellationPolicy {
 private LeaveCancellationPolicy(){}
 public static String blocker(String type,String status,boolean withdrawn,LocalDateTime start,LocalDateTime now){
  if(!"NGHI_PHEP".equals(type))return "Chỉ áp dụng hủy đơn nghỉ phép";
  if(withdrawn||"DA_HUY".equals(status))return "Đơn đã được hủy";
  if(!"CHO_DUYET".equals(status)&&!"DA_DUYET".equals(status))return "Chỉ hủy đơn đang chờ hoặc đã duyệt";
  if(start==null||!start.isAfter(now))return "Đã đến thời gian nghỉ; cần quy trình điều chỉnh riêng";
  return null;
 }
}
