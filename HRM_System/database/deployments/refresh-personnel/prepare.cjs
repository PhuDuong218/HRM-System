const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../../..'),out=path.join(root,'HRM_System/target/data-refresh-private');fs.mkdirSync(out,{recursive:true});
const names=['Nguyễn Minh Quân','Lê Thanh Hà','Trần Thị Thu Hương','Phạm Quốc Bảo','Nguyễn Hoàng Anh','Võ Thị Ngọc Mai','Đặng Trung Kiên','Bùi Thị Kim Ngân','Lê Minh Khôi','Trần Thảo Nguyên','Nguyễn Đức Huy','Phan Gia Bảo','Nguyễn Thị Minh Châu','Đỗ Hải Yến','Lâm Tuấn Anh','Hoàng Thị Lan Anh','Trương Quốc Đạt','Vũ Ngọc Ánh','Nguyễn Thanh Tâm','Lê Thị Bích Hân','Phạm Anh Tuấn','Trần Minh Nhật','Ngô Thị Thanh Trúc','Đinh Quang Huy','Huỳnh Thị Như Ý','Nguyễn Bảo Trâm','Mai Hoàng Phúc','Phạm Thị Khánh Linh','Võ Minh Khang','Trần Thị Mỹ Duyên','Lê Quốc Việt','Đặng Thị Diệu Linh'];
const female=new Set([2,3,6,8,10,13,14,16,18,20,23,25,26,28,30,32]);
const users=['quantri','nhansu.thanhha','truongphong.hanhchinh','truongphong.daotao','truongphong.tuyensinh','truongphong.ketoan','truongphong.congnghe',...Array.from({length:25},(_,i)=>'nv'+String(i+8).padStart(3,'0'))];users[12]='nhansu.minhchau';
const credentials=users.map((username,i)=>({username,name:names[i],password:'Hrm!'+crypto.randomBytes(12).toString('base64url')}));
const cryptoJar='C:/Users/GPA/.m2/repository/org/springframework/security/spring-security-crypto/7.1.1/spring-security-crypto-7.1.1.jar';
const loggingJar='C:/Users/GPA/.m2/repository/commons-logging/commons-logging/1.3.6/commons-logging-1.3.6.jar';
const r=spawnSync('java',['--class-path',cryptoJar+';'+loggingJar,path.join(__dirname,'HashCredentials.java')],{input:credentials.map(c=>c.username+'\t'+c.password).join('\n')+'\n',encoding:'utf8'});
if(r.status!==0)throw Error('Credential hashing failed; database untouched.');
const hashes=new Map(r.stdout.trim().split(/\r?\n/).map(l=>l.split('\t')));if(hashes.size!==32)throw Error('Unexpected credential count');
fs.writeFileSync(path.join(out,'TAI_KHOAN_MOI.txt'),'DỮ LIỆU GIẢ LẬP — KHÔNG COMMIT FILE NÀY\nMỗi tài khoản có mật khẩu riêng. ADMIN duy nhất: quantri.\n\n'+credentials.map(c=>c.name+'\nTên đăng nhập: '+c.username+'\nMật khẩu: '+c.password+'\n').join('\n'));
function transform(sql){
 sql=sql.replace(/^\uFEFF/,'');
 sql=sql.split(/\r?\n/).filter(l=>!l.startsWith(':')&&!/^USE \[/i.test(l)&&!/^GO\s*$/.test(l)).join('\n');
 sql=sql.replace(/INSERT @staff VALUES\((\d+),'[^']+',N'[^']+','([^']+)','([^']+)','[^']+','([^']+)'\);/g,(_,n,dept,pos,role)=>{const i=+n-1;return `INSERT @staff VALUES(${n},'NV${String(n).padStart(3,'0')}',N'${names[i]}','${dept}','${pos}','${users[i]}','${+n===13?'HR':role}');`;});
 for(const [a,b]of Object.entries({'demo.admin':'quantri','demo.hr':'nhansu.thanhha','DEMO-NOT-CALCULATED':'CHUA_TINH','DEMO_DATABASE_SEED':'DATA_INITIALIZATION','DEMO-DEG-':'BANG-','DEMO-HD-':'HD-','DEMO_PHONE':'DIEN_THOAI','DEMO_DAY':'HC_NGAY','DEMO_AM':'HC_SANG','DEMO-LEAVE-':'NP-','DEMO-RESIGN-':'NVIEC-','DEMO-KT-':'KT-','DEMO-':'LUONG-','DEMO':'NV','EVIDENCE':'OM_DAU'}))sql=sql.replaceAll(a,b);
 sql=sql.replace(/'\$2a\$10\$[^']+'/g,"CASE s.username "+credentials.map(c=>"WHEN '"+c.username+"' THEN '"+hashes.get(c.username)+"'").join(' ')+" END");
 sql=sql.replace("CONCAT('employee',n,'@example.invalid')","CONCAT(username,'@hrm.example.invalid')").replace("CONCAT(N'Địa chỉ giả lập ',n,N', không phải địa chỉ thật')","CONCAT(N'Phường Bình Thạnh, TP. Hồ Chí Minh - khu vực ',n)");
 sql=sql.replace("CASE WHEN n%2=0 THEN 'FEMALE' ELSE 'MALE' END","CASE WHEN n IN("+[...female].join(',')+") THEN 'FEMALE' ELSE 'MALE' END");
 sql=sql.replace("N'Chuyên ngành mẫu phù hợp công việc',N'Trường đại học giả lập'","CASE WHEN a.department_id=(SELECT department_id FROM dbo.departments WHERE department_code='DT') THEN N'Ngôn ngữ Anh' WHEN a.department_id=(SELECT department_id FROM dbo.departments WHERE department_code='KT') THEN N'Kế toán' WHEN a.department_id=(SELECT department_id FROM dbo.departments WHERE department_code='CNTT') THEN N'Công nghệ thông tin' ELSE N'Quản trị kinh doanh' END,N'Trường Đại học Sư phạm TP. Hồ Chí Minh'")
 .replace("FROM dbo.employees e CROSS JOIN dbo.qualification_levels q WHERE q.level_code='DH'","FROM dbo.employees e JOIN dbo.employee_assignments a ON a.employee_id=e.employee_id AND a.effective_to IS NULL CROSS JOIN dbo.qualification_levels q WHERE q.level_code='DH'");
 sql=sql.replace("N'Trường Đại học Sư phạm TP. Hồ Chí Minh',2022,1,'2022-06-01'","CASE WHEN a.department_id=(SELECT department_id FROM dbo.departments WHERE department_code='DT') THEN N'Trường Đại học Sư phạm TP. Hồ Chí Minh' WHEN a.department_id=(SELECT department_id FROM dbo.departments WHERE department_code='CNTT') THEN N'Trường Đại học Công nghệ Thông tin - ĐHQG TP. Hồ Chí Minh' ELSE N'Đại học Kinh tế TP. Hồ Chí Minh' END,YEAR(e.birth_date)+22,1,DATEFROMPARTS(YEAR(e.birth_date)+22,6,1)");
 sql=sql.replace('department_id=@tv,effective_to=',"department_id=@tv,position_id=(SELECT position_id FROM dbo.positions WHERE position_code='TV'),effective_to=").replaceAll('LUONG-OT-','OT-').replaceAll('Tăng ca mẫu chờ duyệt, chưa tính lương','Chuẩn bị chương trình đào tạo, chờ duyệt tăng ca');
 sql=sql.split('\n').filter(l=>!l.includes("'DATA_INITIALIZATION'" )).join('\n');
 sql=sql.replace('12000000+employee_id*200000',"CASE WHEN p.position_code='TRUONG_PHONG' THEN 25000000 WHEN p.position_code='ADMIN_SYS' THEN 28000000 WHEN p.position_code='GV' THEN 18000000 WHEN p.position_code='HR' THEN 16000000 WHEN p.position_code='IT' THEN 20000000 ELSE 14000000 END")
 .replace("SELECT contract_id,start_date,end_date,CASE", "SELECT c.contract_id,c.start_date,c.end_date,CASE")
 .replace("FROM dbo.contracts;","FROM dbo.contracts c JOIN dbo.employee_assignments a ON a.employee_id=c.employee_id AND a.effective_to IS NULL JOIN dbo.positions p ON p.position_id=a.position_id;");
 sql=sql.replaceAll('Hỗ trợ điện thoại mẫu','Hỗ trợ điện thoại').replaceAll('Nghỉ cần minh chứng','Nghỉ ốm có minh chứng').replaceAll('Điều khoản lương NV, không phải lương thực tế','Điều khoản lương theo vị trí công tác').replaceAll('Lịch làm việc dữ liệu NV','Lịch làm việc theo ca hành chính').replaceAll('"demo":true','"initialized":true');
 sql=sql.replaceAll('mẫu, không ảnh hưởng lương','không ảnh hưởng lương').replaceAll('Đơn nghỉ phép mẫu','Đơn nghỉ phép').replaceAll('Đơn nghỉ việc mẫu','Đơn nghỉ việc').replaceAll('Dữ liệu giả lập liên kết; lương nháp chưa tính, chưa chốt hoặc công bố','Khởi tạo dữ liệu vận hành; bảng lương đang nháp, chưa tính và chưa công bố');
 return sql;
}
let seed=transform(fs.readFileSync(path.join(root,'HRM_System/database/consolidated/03_DEMO_DATA.sql'),'utf8'));
let workflow=transform(fs.readFileSync(path.join(root,'HRM_System/database/consolidated/04_WORKFLOW_EXAMPLES.sql'),'utf8'));
seed+='\nUPDATE dbo.accounts SET token_version=1000000+CONVERT(int,account_id);\nINSERT dbo.hr_department_scopes(hr_account_id,department_id,effective_from,created_by) SELECT a.account_id,d.department_id,\'2024-01-01\',(SELECT account_id FROM dbo.accounts WHERE username=\'quantri\') FROM dbo.accounts a CROSS JOIN dbo.departments d WHERE a.username=\'nhansu.minhchau\' AND d.department_code IN(\'HCNS\',\'KT\',\'CNTT\');';
const quote=s=>"N'"+s.replaceAll("'","''")+"'";
const reset=`USE hrm_center_full;
GO
SET QUOTED_IDENTIFIER ON; SET ANSI_WARNINGS ON; SET ANSI_PADDING ON; SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF; SET XACT_ABORT ON; SET NOCOUNT ON; SET LOCK_TIMEOUT 15000;
IF DB_NAME()<>'hrm_center_full' THROW 51000,'Wrong database',1;
IF NOT EXISTS(SELECT 1 FROM msdb.dbo.backupset WHERE database_name=DB_NAME() AND type='D' AND backup_finish_date>DATEADD(hour,-2,GETDATE())) THROW 51000,'Recent verified backup required',1;
IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1) OR EXISTS(SELECT 1 FROM sys.check_constraints WHERE is_disabled=1 OR is_not_trusted=1) THROW 51000,'Existing constraints are not all enabled/trusted; stop',1;
IF EXISTS(SELECT 1 FROM sys.tables WHERE temporal_type<>0 OR is_memory_optimized=1) THROW 51000,'Unsupported table storage; stop',1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @enabled TABLE(cmdDisable nvarchar(max),cmdEnable nvarchar(max));
INSERT @enabled SELECT 'DISABLE TRIGGER '+QUOTENAME(OBJECT_SCHEMA_NAME(t.object_id))+'.'+QUOTENAME(t.name)+' ON '+QUOTENAME(SCHEMA_NAME(p.schema_id))+'.'+QUOTENAME(p.name),'ENABLE TRIGGER '+QUOTENAME(OBJECT_SCHEMA_NAME(t.object_id))+'.'+QUOTENAME(t.name)+' ON '+QUOTENAME(SCHEMA_NAME(p.schema_id))+'.'+QUOTENAME(p.name) FROM sys.triggers t JOIN sys.tables p ON p.object_id=t.parent_id WHERE t.is_disabled=0;
DECLARE @cmd nvarchar(max)='';SELECT @cmd+=cmdDisable+';' FROM @enabled;EXEC sys.sp_executesql @cmd;
SET @cmd='';SELECT @cmd+='ALTER TABLE '+QUOTENAME(SCHEMA_NAME(schema_id))+'.'+QUOTENAME(name)+' NOCHECK CONSTRAINT ALL;' FROM sys.tables WHERE is_ms_shipped=0;EXEC sys.sp_executesql @cmd;
SET @cmd='';SELECT @cmd+='DELETE FROM '+QUOTENAME(SCHEMA_NAME(schema_id))+'.'+QUOTENAME(name)+';' FROM sys.tables WHERE is_ms_shipped=0;EXEC sys.sp_executesql @cmd;
EXEC sys.sp_executesql ${quote(seed)};
EXEC sys.sp_executesql ${quote(workflow)};
SET @cmd='';SELECT @cmd+='ALTER TABLE '+QUOTENAME(SCHEMA_NAME(schema_id))+'.'+QUOTENAME(name)+' WITH CHECK CHECK CONSTRAINT ALL;' FROM sys.tables WHERE is_ms_shipped=0;EXEC sys.sp_executesql @cmd;
SET @cmd='';SELECT @cmd+=cmdEnable+';' FROM @enabled;EXEC sys.sp_executesql @cmd;
IF(SELECT COUNT(*) FROM employees)<>32 THROW 51000,'Expected 32 employees',1;
IF(SELECT COUNT(*) FROM accounts a JOIN roles r ON r.role_id=a.role_id WHERE r.role_code='ADMIN')<>1 THROW 51000,'Expected one admin',1;
IF EXISTS(SELECT 1 FROM employees e WHERE (SELECT COUNT(*) FROM employee_assignments a WHERE a.employee_id=e.employee_id AND a.effective_from<=CONVERT(date,GETDATE()) AND(a.effective_to IS NULL OR a.effective_to>CONVERT(date,GETDATE())))<>1) THROW 51000,'Invalid current assignment',1;
IF EXISTS(SELECT 1 FROM employees WHERE employee_code LIKE 'DEMO%' OR employee_code LIKE 'QA%') THROW 51000,'Old labels remain',1;
COMMIT;
SELECT DB_NAME() database_name,(SELECT COUNT(*) FROM employees) employees,(SELECT COUNT(*) FROM accounts) accounts,(SELECT COUNT(*) FROM departments) departments,(SELECT COUNT(*) FROM positions) positions;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK;THROW;END CATCH;
`;
fs.writeFileSync(path.join(out,'REFRESH_PRIVATE.sql'),reset);
fs.writeFileSync(path.join(out,'plan.json'),JSON.stringify({employees:32,departments:5,positions:7,admin:'quantri',hr:['nhansu.thanhha','nhansu.minhchau'],names,synthetic:true},null,2));
console.log('Prepared 32 linked synthetic profiles; private credentials and executable SQL are under ignored target/data-refresh-private. Database not changed.');
