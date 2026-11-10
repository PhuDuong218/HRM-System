// Astronomical Vietnamese lunar calendar, UTC+7 (1900–2099).
// Meeus new-moon/solar-longitude formulas; month numbering follows the
// Vietnamese calendar rules published by Ho Ngoc Duc.
const rad=Math.PI/180, floor=Math.floor;
function moon(k:number):number {
 const t=k/1236.85,t2=t*t,t3=t2*t;
 const m=(359.2242+29.10535608*k-.0000333*t2-.00000347*t3)*rad;
 const mp=(306.0253+385.81691806*k+.0107306*t2+.00001236*t3)*rad;
 const f=(21.2964+390.67050646*k-.0016528*t2-.00000239*t3)*rad;
 const correction=(.1734-.000393*t)*Math.sin(m)+.0021*Math.sin(2*m)-.4068*Math.sin(mp)+.0161*Math.sin(2*mp)-.0004*Math.sin(3*mp)+.0104*Math.sin(2*f)-.0051*Math.sin(m+mp)-.0074*Math.sin(m-mp)+.0004*Math.sin(2*f+m)-.0004*Math.sin(2*f-m)-.0006*Math.sin(2*f+mp)+.001*Math.sin(2*f-mp)+.0005*Math.sin(2*mp+m);
 const dt=t < -11 ? .001+.000839*t+.0002261*t2-.00000845*t3-.000000081*t*t3 : -.000278+.000265*t+.000262*t2;
 return floor(2415020.75933+29.53058868*k+.0001178*t2-.000000155*t3+.00033*Math.sin((166.56+132.87*t-.009173*t2)*rad)+correction-dt+.5+7/24);
}
function sector(day:number):number {
 const t=(day-.5-7/24-2451545)/36525;
 const m=(357.5291+35999.0503*t-.0001559*t*t-.00000048*t*t*t)*rad;
 const l=(280.46645+36000.76983*t+.0003032*t*t+(1.9146-.004817*t-.000014*t*t)*Math.sin(m)+(.019993-.000101*t)*Math.sin(2*m)+.00029*Math.sin(3*m))*rad;
 return floor(((l%(2*Math.PI)+2*Math.PI)%(2*Math.PI))/Math.PI*6);
}
function julian(date:string):number {return floor(Date.parse(date+'T00:00:00Z')/86400000)+2440588;}
function month11(year:number):number {
 const k=floor((julian(year+'-12-31')-2415021)/29.530588853);
 const day=moon(k);return sector(day)>=9?moon(k-1):day;
}
function leapOffset(a:number):number {
 const k=floor((a-2415021.076998695)/29.530588853+.5);
 let previous=-1,i=1,current=sector(moon(k+i));
 do {previous=current;i++;current=sector(moon(k+i));}while(current!==previous&&i<14);
 return i-1;
}
export interface LunarDate {day:number;month:number;year:number;leap:boolean;}
export function lunarDate(date:string):LunarDate|null {
 if(!/^\d{4}-\d{2}-\d{2}$/.test(date))return null;
 const year=+date.slice(0,4),jd=julian(date);
 if(year<1900||year>2099||!Number.isFinite(jd)||new Date((jd-2440588)*86400000).toISOString().slice(0,10)!==date)return null;
 const k=floor((jd-2415021.076998695)/29.530588853);
 let start=moon(k+1);if(start>jd)start=moon(k);
 let a=month11(year),b=a,ly=year;
 if(a>=start){ly=year;a=month11(year-1);}else{ly=year+1;b=month11(year+1);}
 const diff=floor((start-a)/29);let month=diff+11,leap=false;
 if(b-a>365){const offset=leapOffset(a);if(diff>=offset){month=diff+10;leap=diff===offset;}}
 if(month>12)month-=12;if(month>=11&&diff<4)ly--;
 return {day:jd-start+1,month,year:ly,leap};
}
export function lunarLabel(date:string):string {const l=lunarDate(date);return l?l.day+'/'+l.month+(l.leap?' nhuận':''):'';}
export function defaultHoliday(date:string):string {
 const fixed:Record<string,string>={'01-01':'Tết Dương lịch','04-30':'Ngày Chiến thắng','05-01':'Quốc tế Lao động','09-01':'Nghỉ Quốc khánh (mặc định)','09-02':'Quốc khánh'};
 if(fixed[date.slice(5)])return fixed[date.slice(5)];
 // 2026: Center Tet display policy: 16–21 February (through lunar day 5). Other years use the
 // configurable display convention: last lunar-year day and days 1–5.
 if(date>='2026-02-16'&&date<='2026-02-21')return 'Tết Âm lịch';
 const l=lunarDate(date);if(!l||l.leap)return '';
 if(l.month===3&&l.day===10)return 'Giỗ Tổ Hùng Vương';
 if(+date.slice(0,4)!==2026){const next=new Date(Date.parse(date+'T12:00:00Z')+86400000).toISOString().slice(0,10),n=lunarDate(next);if(l.month===1&&l.day<=5||n?.month===1&&n.day===1)return 'Tết Âm lịch (mặc định)';}
 return '';
}
