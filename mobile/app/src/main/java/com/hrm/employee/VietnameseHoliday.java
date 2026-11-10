package com.hrm.employee;
import java.time.LocalDate;
/** Center display calendar, UTC+7. Holiday labels do not generate attendance or payroll. */
public final class VietnameseHoliday {
 private VietnameseHoliday(){}
 private static final double RAD=Math.PI/180;
 private static long moon(long k){double t=k/1236.85,t2=t*t,t3=t2*t,m=(359.2242+29.10535608*k-.0000333*t2-.00000347*t3)*RAD,mp=(306.0253+385.81691806*k+.0107306*t2+.00001236*t3)*RAD,f=(21.2964+390.67050646*k-.0016528*t2-.00000239*t3)*RAD;
 double c=(.1734-.000393*t)*Math.sin(m)+.0021*Math.sin(2*m)-.4068*Math.sin(mp)+.0161*Math.sin(2*mp)-.0004*Math.sin(3*mp)+.0104*Math.sin(2*f)-.0051*Math.sin(m+mp)-.0074*Math.sin(m-mp)+.0004*Math.sin(2*f+m)-.0004*Math.sin(2*f-m)-.0006*Math.sin(2*f+mp)+.001*Math.sin(2*f-mp)+.0005*Math.sin(2*mp+m);
 double dt=t< -11?.001+.000839*t+.0002261*t2-.00000845*t3-.000000081*t*t3:-.000278+.000265*t+.000262*t2;
 return (long)Math.floor(2415020.75933+29.53058868*k+.0001178*t2-.000000155*t3+.00033*Math.sin((166.56+132.87*t-.009173*t2)*RAD)+c-dt+.5+7.0/24);}
 private static int sector(long day){double t=(day-.5-7.0/24-2451545)/36525,m=(357.5291+35999.0503*t-.0001559*t*t-.00000048*t*t*t)*RAD,l=(280.46645+36000.76983*t+.0003032*t*t+(1.9146-.004817*t-.000014*t*t)*Math.sin(m)+(.019993-.000101*t)*Math.sin(2*m)+.00029*Math.sin(3*m))*RAD;return(int)Math.floor(((l%(2*Math.PI)+2*Math.PI)%(2*Math.PI))/Math.PI*6);}
 private static long month11(int y){long k=(long)Math.floor((LocalDate.of(y,12,31).toEpochDay()+2440588-2415021)/29.530588853),day=moon(k);return sector(day)>=9?moon(k-1):day;}
 private static int leap(long a){long k=(long)Math.floor((a-2415021.076998695)/29.530588853+.5);int prev=-1,i=1,current=sector(moon(k+i));do{prev=current;i++;current=sector(moon(k+i));}while(current!=prev&&i<14);return i-1;}
 static int[] lunar(LocalDate date){int year=date.getYear();if(year<1900||year>2099)return null;long jd=date.toEpochDay()+2440588,k=(long)Math.floor((jd-2415021.076998695)/29.530588853),start=moon(k+1);if(start>jd)start=moon(k);long a=month11(year),b=a;int ly=year;if(a>=start)a=month11(year-1);else{ly=year+1;b=month11(year+1);}int diff=(int)Math.floor((start-a)/29.0),month=diff+11,extra=0;if(b-a>365){int offset=leap(a);if(diff>=offset){month=diff+10;extra=diff==offset?1:0;}}if(month>12)month-=12;if(month>=11&&diff<4)ly--;return new int[]{(int)(jd-start+1),month,ly,extra};}
 public static String name(LocalDate d){int m=d.getMonthValue(),day=d.getDayOfMonth();if(m==1&&day==1)return "Tết Dương lịch";if(m==4&&day==30)return "Ngày Chiến thắng";if(m==5&&day==1)return "Quốc tế Lao động";if(m==9&&(day==1||day==2))return "Quốc khánh";int[] l=lunar(d);if(l==null||l[3]!=0)return "";if(l[1]==3&&l[0]==10)return "Giỗ Tổ Hùng Vương";int[] next=lunar(d.plusDays(1));if(l[1]==1&&l[0]<=5||next!=null&&next[1]==1&&next[0]==1)return "Tết Âm lịch";return "";}
}
