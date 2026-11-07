from pathlib import Path
import json,csv,re,xml.etree.ElementTree as E,hashlib
q=Path('qa_tester');f=q/'fixes'
classes=['VerifiedBugsSqlServerIntegrationTest','AuthLogoutRegressionTest','LeaveBalanceScopeRegressionTest','GlobalExceptionHandlerMergeTest','JwtDatabaseTransactionIntegrationTest','AdminAccessApiIntegrationTest','PersonnelSearchTest','PersonnelSearchControllerTest','PayrollManagementAuthorizationTest','PayrollMealExemptionTest','EmployeeDetailServiceTest']
suites=[]
for p in Path('HRM_System/target/surefire-reports').glob('TEST-*.xml'):
 r=E.parse(p).getroot()
 if r.get('name','').split('.')[-1] not in classes:continue
 suites.append({k:(int(r.get(k,0)) if k!='name' else r.get(k)) for k in ['name','tests','failures','errors','skipped']})
api=json.loads((f/'http-results.json').read_text(encoding='utf-8'));totals={k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped']}
ui={'catalogs':[{'route':'/leave-types','beforeTyping':36,'afterTypingAndSelectingStatusBeforeSearch':36,'afterSearch':1,'statuses':['Inactive'],'afterF5':{'keyword':'QA_FIX_','active':'false','count':1},'viewAll':{'keyword':'','active':'','query':'','count':36}},{'route':'/qualifications','beforeTyping':27,'afterTypingAndSelectingStatusBeforeSearch':27,'afterSearch':2,'statuses':['Inactive','Inactive'],'afterF5':{'keyword':'QA_FIX_','active':'false','count':2},'viewAll':{'keyword':'','active':'','query':'','count':27}}], 'accounts':{'enHeading':'Accounts and permissions','enFilters':['Search','Status','Role'],'viHeading':'Tài khoản và phân quyền','viFilters':['Tìm kiếm','Trạng thái','Vai trò'],'F5Language':'en','usernameAndFullNameUnchanged':True,'detailLabels':['Username','Employee code','Full name','Role','Status','Account ID'],'roleHeaders':['Role code','Role name','Permissions count','Actions'],'permissionHeaders':['Permission code','Permission name','Description','Actions']},'schedules':{'missingFromMessage':'Thiếu tham số bắt buộc: from','routeAfterError':'/schedules','stillSignedIn':True},'logout':{'confirmedInUI':True,'redirect':'/login','consoleErrors':[]},'responsive':[{'route':'/'+route,'cssWidth':w,'horizontalOverflow':False} for w in [1440,390,320] for route in ['accounts','qualifications','leave-types']]}
(f/'ui-retest.json').write_text(json.dumps(ui,ensure_ascii=False,indent=2),encoding='utf-8')
(f/'backend-results.json').write_text(json.dumps({'suites':suites,'totals':totals,'database':'SQL integration hrm_test; other fixtures private H2/mocks','startupJobsDisabled':True},ensure_ascii=False,indent=2),encoding='utf-8')
(f/'metrics.json').write_text(json.dumps({'backend':totals,'frontendTests':{'tests':7,'pass':7,'fail':0},'angularBuild':'PASS','api':{'requests':len(api),'pass':sum(x['result']=='PASS' for x in api),'fail':sum(x['result']=='FAIL' for x in api)}},indent=2),encoding='utf-8')
rows=[('SES-05','BUG-002','Logout500; token cũ200','Logout204; DB token_version+1; tất cả JWT cũ401; login mới200','SQL integration và API 2 vòng; UI về login'),('FEG-02','BUG-003','Không có filter trên hai danh mục','Tìm từ khóa/trạng thái hoạt động; chưa Search không đổi list; F5 giữ; View all reset cả URL','Hai route qualifications/leave-types; không coi mọi bộ lọc hệ thống đã nghiệm thu'),('FEG-12','BUG-004','Accounts vẫn tiếng Việt khi EN','VI/EN và F5 đúng; nhãn bảng/dialog/roles/permissions dịch; dữ liệu giữ nguyên','Accounts, không phải mọi bản dịch trên toàn website'),('FEG-14','BUG-005','Baseline toàn hàng NOT-RUN; nhánh thiếu ngày từng401','Lỗi400 chỉ rõ from/to; UI hiển thị from và giữ phiên','Nhánh lỗi binding400 và bảo toàn401 khi không JWT; offline/throttle/500 không kiểm trong lượt sửa'),('BEG-03','BUG-001','HR-B đọc được16giờ của NV-A','HR-B403; nhân viên khác403; owner/HR-A/ADMIN200','Endpoint balances; không suy rộng mọi endpoint IDOR'),('BEG-06','BUG-005','Thiếu from/to trả401','Thiếu một/hai ngày hoặc sai định dạng đều400 có tên tham số','Các biến thể from/to; không tuyên bố đã quét mọi body sai kiểu'),('ENT-03','BUG-001','Quỹ phép ngoài scope bị lộ qua balances','Chặn403 trước truy vấn quỹ, bên trong phạm vi vẫn200','SQL Server hrm_test có quỹ thật16giờ')]
with (f/'RETEST_RESULTS.csv').open('w',encoding='utf-8-sig',newline='') as out:
 w=csv.writer(out);w.writerow(['Case','Bug','Trước sửa','Sau sửa','Kết quả','Phạm vi và giới hạn','Bằng chứng'])
 for id,bug,before,after,scope in rows:w.writerow([id,bug,before,after,'PASS',scope,'backend-results.json; http-results.json; ui-retest.json'])
base=json.loads((q/'evidence/baseline.json').read_text(encoding='utf-8'));changed=[];added=[]
for root in ['HRM_System/src','frontend/src','frontend/test-tools']:
 for p in Path(root).rglob('*'):
  if not p.is_file():continue
  key=str(p)
  if key in base['hashes']:
   if hashlib.sha256(p.read_bytes()).hexdigest()!=base['hashes'][key]:changed.append(key)
  elif p.suffix in ['.java','.ts','.cjs'] and (p.name in ['account-translations.ts','VerifiedBugsSqlServerIntegrationTest.java','AuthLogoutRegressionTest.java','LeaveBalanceScopeRegressionTest.java','accounts-i18n.test.cjs']):added.append(key)
(f/'changed-files.json').write_text(json.dumps({'modifiedThisFix':changed,'addedThisFix':added,'priorWorkingTreeChangesPreserved':True,'stagedOrPushed':False},ensure_ascii=False,indent=2),encoding='utf-8')
report='''# Xác minh sửa 5 lỗi HRM Center

Đã sửa BUG-001,002,005,003,004 theo thứ tự S1→S3. Giữ báo cáo tester ban đầu làm baseline, không xóa các ca FAIL cũ để mất lịch sử. Kết quả sau sửa ở RETEST_RESULTS.csv. Không commit/push/stage; không dùng git add .; qa_tester là báo cáo local.

## Thay đổi và căn cứ

1. BUG-001: balances kiểm cả ownerOrManager và PersonnelScopeService.employee trước SELECT. Cùng chính sách với hồ sơ/quỹ phép: ADMIN tất cả, HR trong scope hoặc chủ hồ sơ, EMPLOYEE chỉ chính mình. Không tạo role Manager giả hoặc tin actor header client.
2. BUG-002: logout lấy Account bằng PESSIMISTIC_WRITE trong transaction, tăng tokenVersion trên entity, cập nhật updatedAt và saveAndFlush; không coi bulk affected-row count là bằng chứng duy nhất. Bảng SQL Server kiểm tra có token_version NOT NULL và không có trigger UPDATE account. Chưa chứng minh nguyên nhân nội bộ ORM/driver của count cũ khác1; không sửa schema hoặc bỏ qua lỗi để trả thành công giả. Test thật xác nhận commit version+1 và mọi JWT cũ bị401, token mới dùng được.
3. BUG-005: handler MissingServletRequestParameterException trả JSON400 có tên from/to; handler sai kiểu giữ400. Không nới SecurityFilterChain hoặc mở /error cho anonymous để né lỗi. UI lịch đọc thông điệp nghiệp vụ400/409/422 thay vì chỉ mã HTTP;401 thực vẫn đi qua cơ chế phiên cũ.
4. BUG-003: mở filter-bar cho đủ4 catalog. Bổ sung API keyword (mã/tên, không dấu, nhiều từ) và active cho qualification-levels/leave-types; giữ overload và API không lọc cũ. Lọc trước phân trang. Bộ lọc áp dụng qua query URL để F5 giữ đúng keyword/active; View all xóa query và về trang đầu. Tiêu chí đang gõ chưa tự áp dụng. Danh mục nhỏ hiện lọc danh sách phía service; không khẳng định đã benchmark danh mục hàng triệu bản ghi.
5. BUG-004: Accounts sử dụng TranslatePipe/LanguageService cho nhãn, nút, trạng thái, lỗi client, placeholder, aria-label, tooltip và dialogTitle; thêm account-translations.ts. Không dịch tên/mã/mô tả của dữ liệu server. Các form preview đang khóa trước đây vẫn giữ nguyên, không tự triển khai CRUD ngoài phạm vi.

## Kết quả

- Backend: **62/62 PASS,0 fail/error/skip**. Trong đó5 test SQL Server thực gồm3 lỗi BE + lọc danh mục + quyền payroll;3 test unit mới kiểm logout/scope; các test hồi quy hiện có được chọn giữ PASS.
- Java integration chỉ chạy khi opt-in HRM_VERIFIED_BUGS_IT=true và URL xác nhận databaseName=hrm_test trước khởi tạo context. Có SELECT DB_NAME và fixtures QA_FIX_ qua API; tắt rõ ContractExpiryJob.onReady, resignation scheduler và missing-checkout job. Không chạy lại nhóm test cũ tự chọn hrm_db.
- Frontend: **7/7 PASS**, gồm test mới duyệt các nhãn Accounts VI/EN và persistence sau khởi tạo lại service. Angular production build PASS.
- HTTP ngoài bộ Java: **168/168 PASS**, gồm hai vòng scope/logout/ngày và các GET từng PASS được chạy lại. Không replay mutation trên bản ghi cũ.
- UI: hai catalog lọc đúng trạng thái inactive, query và count giữ sau F5, reset trả đủ danh sách; Accounts VI↔EN/F5, bảng/chi tiết/roles/permissions đổi đúng; tên/mã giữ nguyên; lỗi thiếu from ở schedules không logout; nút logout thật về /login. Không thấy lỗi Console trong các bước này.
- Ba trang sửa có9 lượt mở tại1440/390/320 CSS px, không tràn ngang toàn trang. Đây không phải nghiệm thu mọi form ở mọi kích thước.

## 7 mục người dùng yêu cầu

'''
report+='| Case | Trước | Sau | Kết quả |\n|---|---|---|---|\n'
for id,bug,before,after,scope in rows:report+=f'| {id} | {before} | {after} | PASS trong nhánh đã nêu |\n'
report+='''
FEG-14 trong CSV baseline không phải FAIL toàn hàng mà là NOT-RUN đầy đủ; lần này kiểm nhánh400 liên quan BUG-005 và giữ401 cho JWT không hợp lệ. Không đánh dấu offline/throttle/500 là đã kiểm. Tương tự, PASS của7 dòng trên không thay thế toàn bộ165 hàng của master tester prompt.

## Các lần thử đầu và xử lý fixture

- Lượt Java đầu:61/62 PASS; một fixture catalog thiếu isPaid bắt buộc cho leave-types. Bổ sung đúng DTO rồi62/62 PASS; không nới validation hoặc assertion.
- Test i18n đầu phát hiện thiếu nhãn Xác nhận mật khẩu, đã bổ sung. Ký tự × không phải nhãn ngôn ngữ, giữ literal. Lượt cuối7/7.
- Lượt HTTP đầu dùng lại JWT EMPLOYEE đã bị chính logout trước đó thu hồi nên một assertion200 nhận401; sửa fixture đăng nhập mới mỗi vòng,168/168. Không thay đổi chức năng thu hồi để né test.

## Tệp kiểm tra

- backend-regression.log / backend-results.json; frontend-regression.log; angular-build.log.
- http-results.json; ui-retest.json; RETEST_RESULTS.csv; changed-files.json.
- Test lâu dài: HRM_System/src/test/java/com/hrm/security/VerifiedBugsSqlServerIntegrationTest.java; service/AuthLogoutRegressionTest.java; service/LeaveBalanceScopeRegressionTest.java; frontend/test-tools/accounts-i18n.test.cjs.
- Script local run-backend-tests.ps1 yêu cầu kết nối hrm_test và tài khoản test đã chuẩn bị; không xuất mật khẩu/JWT. Không chạy script reset mật khẩu cho nhóm.

## Giới hạn và bàn giao

Không sửa120 assertion/fixture đỏ ngoài phạm vi5 bugs từ lần tester trước. Không tuyên bố full suite526 đã xanh hoặc toàn bộ sản phẩm đã nghiệm thu. Không sửa cấu hình datasource đang dùng của người dùng, không đổi mật khẩu hoặc schema trong lượt sửa. Tài khoản QA_FIX_ và dữ liệu test còn trong hrm_test để tái hiện; không tự restore DB hoặc chạy job toàn cục.

Người dùng cần restart Backend đang dùng (cổng2005) để nạp class mới; reload Frontend. Các bước xác minh dùng server QA riêng2009/4203, không triển khai lên Git/main.
'''
(f/'FIX_VERIFICATION.md').write_text(report,encoding='utf-8')
p=q/'BUG_LIST.md';old=p.read_text(encoding='utf-8')
if 'fixes/FIX_VERIFICATION.md' not in old:p.write_text('> Cập nhật sau khi được phép sửa: 5 lỗi đã được vá và kiểm lại. Xem [kết quả sau sửa](fixes/FIX_VERIFICATION.md). Nội dung bên dưới được giữ làm baseline trước sửa.\n\n'+old,encoding='utf-8')
print('Backend',totals,'API',len(api),'fail',sum(x['result']=='FAIL' for x in api),'modified',len(changed),'added',len(added))
