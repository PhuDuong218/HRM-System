package com.hrm.service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Advisory only, Article 35; exceptional occupations and exemption grounds require HR review. */
@Service
public class ResignationNoticeAdvice {
 private final JdbcTemplate db;public ResignationNoticeAdvice(JdbcTemplate db){this.db=db;}
 public String warning(long employee,LocalDate submitted,LocalDate last){
  var rows=db.queryForList("SELECT c.start_date,c.end_date,t.type_code FROM dbo.contracts c JOIN dbo.contract_types t ON t.contract_type_id=c.contract_type_id WHERE c.employee_id=? AND c.start_date<=? AND (c.end_date IS NULL OR c.end_date>=?) AND c.status IN ('DA_KY','DA_KET_THUC') ORDER BY c.start_date DESC,c.contract_id DESC",employee,submitted,submitted);
  if(rows.size()!=1)return "Cần HR đối chiếu hợp đồng, căn cứ nghỉ và thời hạn báo trước; chưa đủ dữ liệu để kết luận.";
  var row=rows.get(0);String code=String.valueOf(row.get("type_code"));
  Integer days=null;
  if("KHONG_XAC_DINH_THOI_HAN".equals(code))days=45;
  if("XAC_DINH_THOI_HAN".equals(code)&&row.get("end_date")!=null){
   LocalDate start=((java.sql.Date)row.get("start_date")).toLocalDate(),end=((java.sql.Date)row.get("end_date")).toLocalDate();
   if(end.isBefore(start.plusMonths(12).minusDays(1)))return "Hợp đồng dưới 12 tháng: đối chiếu tối thiểu 3 ngày làm việc theo lịch thực tế, ngoại lệ và căn cứ nghỉ (Điều 35); không tự tính theo cuối tuần.";
   if(!end.isAfter(start.plusMonths(36).minusDays(1)))days=30;
  }
  if(days==null)return "Loại/thời hạn hợp đồng cần HR đối chiếu quy định báo trước và ngoại lệ; không tự kết luận từ tên hợp đồng.";
  long actual=ChronoUnit.DAYS.between(submitted,last.plusDays(1));
  return (actual<days?"Cảnh báo: khoảng báo trước ngắn hơn "+days+" ngày.":"Khoảng báo trước đạt ngưỡng tham chiếu "+days+" ngày.")+" HR vẫn cần đối chiếu căn cứ miễn báo trước, thỏa thuận và ngành nghề đặc thù theo Điều 35; cảnh báo không chặn duyệt.";
 }
}
