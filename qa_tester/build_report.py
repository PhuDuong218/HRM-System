from pathlib import Path
import json,csv,re,datetime,collections
Q=Path(__file__).parent; E=Q/'evidence'
def load(f):return json.loads((E/f).read_text(encoding='utf-8-sig'))
def write(f,text):(Q/f).write_text(text,encoding='utf-8')
http=load('http-results.json');scan=load('security-scan.json');ui=load('ui-observations.json');base=load('baseline.json');end=load('end-state.json');state=load('qa-state.json');java=load('java-results-current.json');cases=load('checklist.json');runtime=load('runtime.json')
metrics={'http':dict(collections.Counter(x['result'] for x in http+scan)),'http_requests':len(http)+len(scan),'java':{k:sum(x[k] for x in java) for k in ['tests','failures','errors','skipped']},'ui_route_openings':len(ui['tier1'])+len(ui['smoke'])};metrics['java']['pass']=metrics['java']['tests']-metrics['java']['failures']-metrics['java']['errors']-metrics['java']['skipped'];(E/'metrics.json').write_text(json.dumps(metrics,indent=2),encoding='utf-8')
emp=state['employees'][0]['employeeId'];hrb=state['accounts']['hrB']['username'];qaAdmin=state['accounts']['qaAdmin']['username']
bugs=[
 {'id':'BUG-001','severity':'S1','priority':'P1','task':'FE-006','layer':'BE / phân quyền dữ liệu','title':'HR ngoài phạm vi đọc được số dư phép của nhân viên phòng khác','type':'LỖI','pre':f'Nhân viên {emp} thuộc phòng A; {hrb} chỉ có scope phòng B; quỹ QA mới được cấp 16 giờ.','steps':f'1. Đăng nhập HR phòng B. 2. GET /employees/{emp}/detail để xác nhận bị 403. 3. GET /employee-requests/balances/{emp}. 4. Lặp lại request bước 3.','expected':'403 hoặc không trả dữ liệu ngoài phạm vi — đề cương 1.3; ENT-03 / BEG-03 của prompt; scope HR được cấp theo phòng.','actual':f'GET /employee-requests/balances/{emp} trả 200 với entitlementId=542, entitledHours=16, usedHours=0, remainingHours=16. GET /leave-entitlements/542 bằng cùng HR bị 403. SELECT xác nhận HR-B chỉ có phòng B và quỹ thuộc nhân viên phòng A.','evidence':'evidence/http-results.json: ENT-OUTSCOPE-LEAK-0/1, ENT-OUTSCOPE-DIRECT-0/1; evidence/BUG-001-database.json','frequency':'2/2 request có quỹ thật; không phụ thuộc UI. F5/đăng nhập lại chưa lặp.','impact':'Lộ quỹ phép ngoài phạm vi; kiểm tra quyền khác nhau giữa hai endpoint của cùng dữ liệu.','workaround':'Tránh sử dụng endpoint balances để tra cứu ngoài phạm vi; ẩn nút UI không khắc phục được API.','cause':'Nghi ngờ balances chỉ kiểm ownerOrManager mà thiếu kiểm tra employee scope theo đường dẫn /balances/{employeeId}; chưa sửa.','related':'ENT-03, BEG-03; không xác nhận là lỗi trùng D01…D12.'},
 {'id':'BUG-002','severity':'S1','priority':'P1','task':'FE-001','layer':'BE / phiên đăng nhập','title':'Đăng xuất trả 500 và không thu hồi token đang dùng','type':'LỖI','pre':f'Tài khoản ADMIN QA mới {qaAdmin} đang hoạt động, có JWT thật; sử dụng hrm_test.','steps':'1. Đăng nhập tài khoản QA. 2. POST /api/auth/logout với JWT đó. 3. Dùng lại JWT gọi GET /api/auth/me. 4. Đăng nhập lại và lặp toàn bộ.','expected':'Logout hoàn thành 204; token cũ bị 401 theo SES-05. Không 500 trên chức năng đăng xuất.','actual':'Hai lần POST logout đều 500. Hai lần dùng token cũ gọi /me đều 200. Log: IllegalStateException tại AuthService.logout: Không thể vô hiệu phiên đăng nhập. Sau đó khóa chính tài khoản QA qua API thì token bị chặn đúng.','evidence':'evidence/http-results.json: LOGOUT-0/1, LOGOUT-OLD-TOKEN-0/1, QA-LOCK, LOCKED-TOKEN-0/1; evidence/backend.log','frequency':'2/2 sau hai lượt đăng nhập API mới; chưa tái hiện thao tác nút logout trên UI.','impact':'Không kết thúc phiên ở server; UI xóa token cục bộ không bảo đảm token đã sao chép bị thu hồi.','workaround':'Khóa tài khoản QA chặn được token; không xem đây là thay thế logout trong vận hành.','cause':'AuthService kiểm giá trị trả về incrementTokenVersion phải bằng 1; nguyên nhân trả khác 1/transaction chưa được xác minh.','related':'SES-05; đề cương 1.1/1.3. Cơ chế thu hồi trong đề cương còn ghi cần xác nhận nhưng API logout hiện có không được phép trả 500.'},
 {'id':'BUG-003','severity':'S3','priority':'P2','task':'FE-005 / FE-006','layer':'FE','title':'Danh mục trình độ và loại nghỉ không có bộ lọc hoặc Xem tất cả','type':'THIẾU','pre':'ADMIN đăng nhập web QA, dữ liệu danh mục có nhiều bản ghi.','steps':'1. Mở /qualifications. 2. Tìm ô tìm kiếm, bộ lọc và nút Xem tất cả. 3. F5 và kiểm lại. 4. Lặp trên /leave-types.','expected':'Có tìm/lọc và xóa lọc theo quy tắc danh sách thống nhất của chủ dự án; FEG-02. Không yêu cầu mọi danh mục có cùng tiêu chí nghiệp vụ.','actual':'Cả hai route không có filter-actions, không có nút tìm kiếm hoặc Xem tất cả. Component Catalog chỉ hiển thị filter cho positions/departments.','evidence':'evidence/ui-observations.json: extraObservations.catalogFilters; source frontend/src/app/pages/catalog/catalog.html dòng điều kiện filter-bar','frequency':'2/2 mỗi route, gồm mở lại sau F5; source xác nhận điều kiện.','impact':'Không thể tìm nhanh trên UI hoặc bỏ lọc theo cùng mẫu; vẫn phân trang được.','workaround':'Duyệt phân trang hoặc gọi API bằng công cụ kiểm thử.','cause':'Điều kiện config.path loại trừ qualification-levels và leave-types.','related':'FEG-02; đây là thiếu UX theo yêu cầu đã thống nhất, không kết luận đề cương bắt mọi danh mục phải có tìm nâng cao.'},
 {'id':'BUG-004','severity':'S3','priority':'P2','task':'FE-001 / Tier 2 accounts','layer':'FE / ngôn ngữ','title':'Trang tài khoản giữ nhãn tiếng Việt khi chọn EN','type':'LỖI','pre':'Chọn EN; html lang=en; trang bằng cấp đã hiển thị Degrees and certificates / Search / View all.','steps':'1. Chọn EN tại trang bằng cấp. 2. Mở /accounts. 3. Quan sát tiêu đề và nhãn tìm kiếm. 4. F5 và kiểm lại.','expected':'Các nhãn giao diện chuyển sang EN, dữ liệu tài khoản giữ nguyên; FEG-12.','actual':'html lang=en nhưng h1 vẫn Tài khoản và phân quyền; các label vẫn Tìm kiếm, Trạng thái, Vai trò. Trong cùng phiên, trang bằng cấp dịch đúng.','evidence':'evidence/ui-observations.json: extraObservations.accountsEnglish; source frontend/src/app/pages/accounts/accounts.html','frequency':'2/2 gồm F5, lựa chọn EN được lưu.','impact':'Giao diện lẫn ngôn ngữ giữa các module, không mất dữ liệu.','workaround':'Dùng VI; chưa khẳng định toàn bộ nhãn của mọi trang sai.','cause':'Nhãn tĩnh ở template accounts chưa dùng dịch như component khác.','related':'FEG-12; Tier 2 chỉ smoke, không kết luận hoàn thành tất cả bản dịch.'},
 {'id':'BUG-005','severity':'S3','priority':'P2','task':'FE-009 / Tier 2 schedules','layer':'BE / xử lý lỗi','title':'Thiếu bộ lọc ngày tra cứu lịch trả 401 thay vì lỗi dữ liệu 400','type':'LỖI','pre':'JWT ADMIN hợp lệ; /api/auth/me và API khác dùng được.','steps':'1. GET /api/management/schedules/search không có from/to bằng JWT ADMIN. 2. Gọi cùng endpoint có from/to ngày 2026-11-01. 3. Lặp lại cả hai request.','expected':'Thiếu tham số bắt buộc trả 400 và chỉ ra from/to; 401 dành cho phiên không hợp lệ (BEG-06 / mã lỗi §4).','actual':'Không có ngày: 401, thông báo Phiên đăng nhập không hợp lệ hoặc tài khoản không còn được phép truy cập. Có from/to: 200 bằng JWT tương ứng.','evidence':'evidence/http-results.json: SCHEDULE-MISSING-DATES-0/1, SCHEDULE-VALID-DATES-0/1','frequency':'2/2 cho từng biến thể.','impact':'Client có thể hiểu nhầm là hết phiên và buộc đăng nhập lại thay vì cho sửa bộ lọc.','workaround':'Luôn gửi đủ from/to.','cause':'Chưa xác minh lớp nào chuyển lỗi binding thành 401; không coi JWT thực sự hết hạn.','related':'BEG-06, FEG-14; chưa kiểm việc UI có thực tế bị logout vì request này.'}
]
with (Q/'bugs.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['ID','Tiêu đề','Task','Tầng','Loại','Mức độ','Ưu tiên','Tần suất','Bước tái hiện (rút gọn)','Kỳ vọng','Thực tế','Bằng chứng','Ghi chú'])
 for b in bugs:w.writerow([b[k] for k in ['id','title','task','layer','type','severity','priority','frequency','steps','expected','actual','evidence','cause']])
text='# Lỗi đã xác minh\n\nChỉ kiểm tra, không có bản vá. Môi trường chung: XuanAnh / '+base['head']+' với working tree chưa commit theo ENVIRONMENT.md; Windows, Java 23, SQL Server hrm_test; API localhost cổng QA 2009; web QA cổng 4203, trình duyệt tích hợp dựa Chromium (phiên bản binary chưa thu được). Các lỗi UI ghi rõ thao tác web; các lỗi API không được coi đã tái hiện trên UI.\n'
for b in bugs:
 text+=f"\n## {b['id']} — {b['severity']} — {b['title']}\n\n- Module / Task: {b['task']}. Tầng: {b['layer']}.\n- Loại: {b['type']}. Mức độ: {b['severity']}. Ưu tiên: {b['priority']}.\n- Môi trường: working tree nêu trên; vai trò ADMIN/HR như tiền điều kiện; mật khẩu và JWT không xuất. UI quan sát ở 1440 CSS px, trừ kiểm responsive ghi riêng.\n- Tiền điều kiện: {b['pre']}\n- Các bước tái hiện: {b['steps']}\n- Kết quả kỳ vọng và căn cứ: {b['expected']}\n- Kết quả thực tế: {b['actual']}\n- Bằng chứng: {b['evidence']}\n- Tần suất / F5: {b['frequency']}\n- Ảnh hưởng: {b['impact']}\n- Cách tạm né: {b['workaround']}\n- Nghi ngờ nguyên nhân: {b['cause']}\n- Liên quan: {b['related']}\n"
write('BUG_LIST.md',text)
# Every checklist row receives an honest disposition; PASS is not inferred from endpoint existence.
failed={'FEG-02':'BUG-003','FEG-12':'BUG-004','SES-05':'BUG-002','ENT-03':'BUG-001','BEG-03':'BUG-001','BEG-06':'BUG-005','INT-03':'Bộ chọn test đỏ; phần lớn guard/fixture, xem ENVIRONMENT.md'}
partial={'FEG-11':'77 lượt mở trang, 7 độ rộng CSS gồm 6 mốc yêu cầu; chỉ một drawer bằng cấp được mở ở 390, chưa đủ mọi form.', 'EMP-01':'Đã tạo 5 nhân viên QA qua API và SELECT; chưa đủ từng cột/luồng UI như hàng yêu cầu.','EMP-08':'Đã thử tìm không dấu, nhiều từ, ký tự LIKE; chưa đủ email/SĐT và mọi tổ hợp.','ASG-01':'Điều chuyển QA qua API thành công, lịch sử được SELECT; chưa trọn UI→API→DB→audit từng bước.','QUA-02':'Đã thêm hai bằng QA và SELECT; chưa thao tác lưu bằng UI.','QUA-05':'Hai bằng chính liên tiếp chỉ còn một bằng chính; chưa đủ mọi tổ hợp và hồ sơ nhân viên.','RES-02':'Duyệt đơn ngày tương lai qua API; nhân viên vẫn DANG_LAM; chưa chạy job tới hạn.','NAV-06':'Tổng quan 231 hồ sơ /178 đang làm khớp SELECT, chưa kiểm tất cả biểu đồ.','NAV-04':'Hai đường dẫn lạ có trang 404; các placeholder đã xác minh, chưa kiểm đủ forbidden theo vai trò.','BEG-01':'356 lượt quét endpoint, 354 trả 401, /hello là ngoại lệ công khai cần chốt; chưa token hết hạn/tamper role cho mọi endpoint.','BEG-17':'5 kiểm tra SELECT đạt, chưa kiểm toàn bộ khoảng chồng/hở/quỹ/audit/mồ côi.','SES-01':'Đã đăng nhập web ADMIN; chưa quét toàn bộ route khi chưa đăng nhập.','LOG-03':'Khóa QA account: token và login đều401; chưa đủ nhân viên đã nghỉ và mọi tổ hợp UI.'}
with (Q/'TEST_EXECUTION_LOG.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['Mã ca','Task','Mô tả','Vai trò','Dữ liệu vào','Kỳ vọng','Thực tế','Kết quả','Bug liên quan','Bằng chứng','Thời điểm'])
 for c in cases:
  result='FAIL' if c['id'] in failed else 'NOT-RUN';note=partial.get(c['id'],'Chưa chạy đủ hàng checklist; không suy ra PASS từ build/test cũ.');bug=failed.get(c['id'],'')
  if result=='FAIL':note='Có ít nhất một nhánh đã tái hiện lỗi; các nhánh còn lại chưa mặc định đã kiểm.'
  w.writerow([c['id'],'Theo §5–§7 prompt',c['description'],'Theo hàng checklist','',c['expected'],note,result,bug,'BUG_LIST.md; evidence/http-results.json; evidence/ui-observations.json','2026-11-01, phiên QA +07:00'])
 for x in http+scan:w.writerow([x['id'],'API subcase',x['method']+' '+x['path'],x.get('role',''),json.dumps(x.get('input'),ensure_ascii=False),str(x.get('expected')),str(x.get('status'))+' '+x.get('note',''),x['result'],'','evidence/http-results.json' if x in http else 'evidence/security-scan.json','2026-11-01, phiên QA +07:00'])
 for x in ui['tier1']+ui['smoke']:w.writerow(['UI-'+x['route']+'-'+str(x['cssWidth']),'Route smoke','Mở route / đo tràn ngang','ADMIN',str(x['cssWidth'])+' CSS px','Có h1, không tràn ngang','NOT-IMPLEMENTED (UI)' if x.get('placeholder') else x.get('warning') or 'Không tràn ngang toàn trang','OBSERVED' if x.get('placeholder') or x.get('warning') else 'PASS','','evidence/ui-observations.json','2026-11-01, phiên QA +07:00'])
 for suite in java:
  for c in suite['cases']:w.writerow([suite['name']+'.'+c['name'],'Java existing',c['name'],'test fixture','','Theo assertion hiện có',c['reason'],c['status'],'','evidence/java-results-current.json; evidence/java-suite-outside-sandbox.log','2026-11-01, phiên QA +07:00'])
# Runtime endpoint and route inventories.
paths=re.findall(r'path:\s*["\']([^"\']*)',Path('frontend/src/app/app.routes.ts').read_text(encoding='utf-8'));known={x['route'] for x in ui['tier1']+ui['smoke']}|{'/login'}
coverage='# Phạm vi và bằng chứng\n\nWeb responsive, không phải mobile app. Công cụ: trình duyệt tích hợp Chromium; phiên bản binary chưa ghi nhận. Không kiểm Chrome + Edge độc lập, không giả định đã kiểm app nhân viên.\n\n11 route Tier 1 × 7 độ rộng đo thực tế (1440,1280,1024,768,390,321,320) =77; 15 route khác ×1440/390=30; tổng107 lượt mở. Không thấy tràn ngang toàn trang trong các lượt đo. Chưa nghiệm thu CRUD/form ở mọi kích thước.\n\n## Route\n\n| Route | Kết quả | Giới hạn |\n|---|---|---|\n'
for path in paths:
 if not path:continue
 route='/'+path;status='MỞ WEB ĐÃ KIỂM' if route in known else '404 đã thử 2 URL' if path=='**' else 'NOT-RUN';coverage+=f'| `{route}` | {status} | '+('NOT-IMPLEMENTED (UI)' if path in ['allowances','decisions','payroll','payslips'] else 'Đọc/mở trang; không mặc định CRUD đạt')+' |\n'
coverage+='\n## Endpoint runtime\n\nQuét không JWT và JWT sai trên từng method/path. ID thay bằng 999999999 để không động hồ sơ có sẵn. HTTP nghiệp vụ chỉ ở các request có bằng chứng riêng; danh sách endpoint không thay thế kiểm quyền mọi vai trò.\n\n| Method | URL | Controller | Xác thực | Nghiệp vụ |\n|---|---|---|---|---|\n'
for r in runtime['routes']:
 for path in r['paths']:
  for method in r['methods']:
   hits=[x for x in scan if x['path']==re.sub(r'\{[^}]+\}','999999999',path) and x['method']==method]
   coverage+=f"| {method} | `{path}` | {r['controller']} | "+('354/356 nhóm scan; xem bản ghi chi tiết' if hits else 'Public hoặc không thuộc bộ quét')+' | Chỉ nếu có bản ghi trong http-results; phần còn lại NOT-RUN |\n'
write('COVERAGE.md',coverage)
# Evidence-driven document/schema comparison.
doc=Path('qa_tester/evidence/Giai_thich_CSDL_HRM_Center.txt').read_text(encoding='utf-8');documentTables=set(re.findall(r'4\.\d+\.\s+([a-z_]+)\s*[–-]',doc));actualTables={x['name'] for x in load('database-preflight.json')['results'][2]};(E/'schema-table-comparison.json').write_text(json.dumps({'documentTables':sorted(documentTables),'actualTables':sorted(actualTables),'addedToActual':sorted(actualTables-documentTables),'missingFromActual':sorted(documentTables-actualTables),'columnComparison':'NOT-RUN'},ensure_ascii=False,indent=2),encoding='utf-8')
write('GAP_VS_DE_CUONG.md',f'''# Đối chiếu phạm vi còn thiếu

Đã đọc hai DOCX trong Downloads; bản trích văn bản ở evidence/. Không tự coi đặc tả tester là toàn bộ đề cương gốc.

- /allowances, /decisions, /payroll, /payslips: NOT-IMPLEMENTED (UI), hai độ rộng 1440/390 đều hiện trang chưa triển khai. Không tính là lỗi chạy, không coi đã hoàn thành mục 11–14 bằng UI.
- /accounts và /reports đã có giao diện thật; không còn placeholder theo bản kiểm này.
- /attendance hiện thông báo chưa tích hợp/xác minh API danh sách công quản lý. Chưa nghiệm thu quy trình UI tra cứu công (mục8.1).
- BUG-003: bộ lọc /qualifications và /leave-types chưa có trên UI theo mẫu thống nhất.
- Hồ sơ nhân viên hiển thị trình độ hiện tại, email/SĐT search, sắp xếp mọi danh sách: chưa có kết luận nghiệm thu đủ; không giả định thiếu chỉ từ một màn hình.
- Tệp/chứng chỉ ngày cấp–hết hạn, ảnh đại diện, thông báo nghiệp vụ, liên hệ khẩn cấp, nhập Excel, quên mật khẩu email/OTP: CHƯA KIỂM hoặc THIẾU-CHUẨN nếu sau này xác minh thiếu; không dùng làm lỗi trái đề cương hiện tại.

Tài liệu CSDL có {len(documentTables)} tên bảng, hrm_test hiện có {len(actualTables)} bảng. Bảng thêm: {', '.join(sorted(actualTables-documentTables))}. Bảng tài liệu chưa tìm thấy: {', '.join(sorted(documentTables-actualTables)) or 'không'}. Đây là chênh phiên bản tài liệu, chưa kết luận lỗi DB. So cột/ràng buộc từng bảng: NOT-RUN.
''')
spec=Path('qa_tester/evidence/De_cuong_chuc_nang_HRM_Center.txt').read_text(encoding='utf-8');sections=re.findall(r'(?m)^(\d+\.\d+)\.\s+([^\n]+)',spec)
feature='# Ma trận theo đề cương\n\nĐẠT ở một tầng chỉ cho phần đã có bằng chứng, không phải nghiệm thu toàn chức năng. Phần chưa đủ dùng CHƯA KIỂM thay vì suy đoán.\n\n| Mục | Chức năng | Giao diện | API | Ràng buộc DB | Phân quyền | Tích hợp |\n|---|---|---|---|---|---|---|\n'
for sec,description in sections:
 vals=['CHƯA KIỂM']*5
 if sec=='1.1':vals[0]='Đăng nhập ADMIN quan sát đạt';vals[1]='Đăng nhập thật nhiều vai trò đạt'
 if sec=='1.3':vals[1]='CÓ LỖI BUG-002';vals[3]='CÓ LỖI BUG-001';vals[4]='CÓ LỖI BUG-002'
 if sec in ['5.1','5.2']:vals[0]='CÓ LỖI BUG-003' if sec=='5.1' else 'Danh sách/chi tiết/tìm đã quan sát';vals[1]='Tạo/switch primary/xóa mềm QA đã kiểm';vals[2]='Một primary QA; dữ liệu đã SELECT'
 if sec=='2.1':vals[0]='Mở / tìm bộ lọc đã quan sát';vals[1]='Tạo, tìm, validation nhiều nhánh đạt';vals[2]='SELECT nhân viên QA đạt'
 if sec=='2.3':vals[1]='Thôi việc QA qua API đạt; tái tuyển tương lai bị chặn';vals[2]='Lịch sử QA còn; chưa đủ mọi module'
 if sec in ['13.1','13.2','13.3','14.1','14.2','14.3','11.1','11.2','11.3','12.1','12.2','12.3']:vals[0]='NOT-IMPLEMENTED (UI)'
 if sec=='9.2':vals[1]='Nghỉ việc: submit/reject/resubmit/approve/double approve đã kiểm'
 if sec=='9.3':vals[1]='Duyệt tương lai chưa thôi việc ngay: đã kiểm';vals[4]='Job tới hạn NOT-RUN'
 feature+='| '+sec+' | '+description.replace('|','/')+' | '+' | '.join(vals)+' |\n'
feature+='\n9.1 thiếu nội dung trong DOCX, không tự bổ sung làm căn cứ PASS/FAIL.\n\n## Checklist HRM thiết yếu §8\n\n| # | Nhóm | Phạm vi | Trạng thái |\n|---|---|---|---|\n'
names=['Đăng nhập/đăng xuất','Đổi/reset mật khẩu','Quên mật khẩu tự phục vụ','Vai trò và phạm vi','Hồ sơ nhân viên','Hồ sơ mở rộng','Tìm/lọc/phân trang/sắp xếp','Phòng ban/chức vụ/trưởng phòng','Phân công/điều chuyển','Trình độ/bằng cấp','Thôi việc/tái tuyển','Nghỉ phép/quỹ phép','Thông báo nghiệp vụ','Audit','Dữ liệu riêng tư','Hợp đồng','Lịch/công/OT/lương liên module','Báo cáo/xuất file','Nhập hàng loạt/in','VI/EN/responsive/bàn phím','Backup/khôi phục/tài liệu']
for i,name in enumerate(names,1):
 status='CHƯA KIỂM ĐỦ';scope='Có trong đề cương'
 if i in [3,6,13,19,20,21]:scope='Chuẩn HRM / không bắt buộc đề cương'
 if i==1:status='CÓ LỖI BUG-002'
 if i==4:status='CÓ LỖI BUG-001'
 if i==7:status='CÓ LỖI BUG-003; một số tìm kiếm đã kiểm'
 if i==20:status='CÓ LỖI BUG-004; responsive route smoke đạt'
 if i==21:status='Backup hrm_test + VERIFYONLY đạt; chưa RESTORE thật/máy sạch'
 feature+=f'| {i} | {name} | {scope} | {status} |\n'
write('FEATURE_STATUS_MATRIX.md',feature)
write('OPEN_QUESTIONS.md','''# Điểm cần chốt và giới hạn

1. Đề cương mục9.1 không có nội dung đầy đủ (chỉ còn dòng 4 trước9.2). Các nhánh nộp đơn chỉ đối chiếu API hiện có, không tự viết lại đặc tả.
2. /hello đang công khai theo SecurityConfig, trả lời chẩn đoán không chứa dữ liệu nhạy cảm. Tester prompt chỉ cho health/login công khai; cần chốt ngoại lệ. Đã ghi OBSERVED, không gán lỗi vượt quyền nhân sự.
3. EMPLOYEE đọc danh mục loại nghỉ là cần để nộp đơn; hai kỳ vọng403 ban đầu của script đã được loại khỏi lỗi sản phẩm, giữ lịch sử OBSERVED.
4. Không chạy execute-due toàn cục vì có thể xử lý đơn có sẵn của nhóm. Chưa mô phỏng tới ngày tương lai/job sau nghỉ việc.
5. Các test SQL Server có fixture SQL ghi trực tiếp không thỏa luật API-only/QA_ của phiên này; nhóm bị loại nằm trong java-selector.json.
6. Một số @SpringBootTest không pin profile test đã kết nối cấu hình hrm_db và dừng bởi SELECT DB_NAME guard trước fixture. Có ContractExpiryJob.onReady chạy khi ApplicationReadyEvent; chưa có snapshot trước của hrm_db nên KHÔNG KHẲNG ĐỊNH không có tác động tự động. Không chạy thêm nhóm này, không tự restore hrm_db. Đây là rủi ro môi trường cần nhóm đối chiếu audit/backup riêng.
7. Runtime QA vẫn chọn MockSalaryContractProvider / MockAttendanceDataProvider (đã biết D02, tái quan sát wiring hiện tại). Chưa tính lương ghi để tránh dùng nguồn giả và không đào sâu Tier2.
8. Các quyết định thuế bậc2+, người phụ thuộc, OT nghỉ/lễ và sàn/trần bảo hiểm theo tester prompt là chưa đặc tả; không đưa ra kết luận pháp luật hoặc lỗi công thức trong lượt này.
9. Ngày cấp/hết hạn/tệp bằng cấp không có trong đề cương5.2 đọc được; không tự coi là lỗi bắt buộc của đề cương.
10. Không đo được phiên bản binary của trình duyệt tích hợp; chưa kiểm Edge riêng. Ảnh chụp của công cụ có vấn đề tỷ lệ hiển thị nên kết luận responsive giới hạn ở DOM/CSS, không chứng nhận mọi pixel.
11. Bước DELETE phòng ban/chức vụ bị kiểm duyệt tự động chặn vì không xác minh quyền sở hữu QA tại bước đó; đã loại khỏi lượt chạy tiếp, chưa vô hiệu hóa/xóa phòng ban hoặc chức vụ bằng bước này.
''')
# Existing Java failures: separate environment/fixture from unproven product defects.
classification=[]
for x in java:
 if not(x['failures'] or x['errors']):continue
 reasons=' '.join(c['reason'] for c in x['cases'])
 kind='Môi trường: DB guard chặn cấu hình hrm_db' if 'hrm_db' in reasons or 'testDatabase' in reasons else 'Fixture thiếu bean/strict stub cũ' if 'Strict stubbing' in reasons or 'No qualifying bean' in reasons else 'Cần đối chiếu fixture và nghiệp vụ; chưa chứng minh lỗi sản phẩm'
 classification.append(f"- {x['name']}: {x['failures']} fail / {x['errors']} error — {kind}.")
env=f'''# Môi trường và lệnh

- Ngày: 01/11/2026, Asia/Saigon. Windows; Node v24.22.1; Java23.0.1, compile release21; Maven wrapper cache3.9.16.
- Branch `{base['branch']}`, HEAD `{base['head']}`. Không checkout/merge/commit/push/reset/stash/clean.
- Working tree chưa commit từ trước: giữ nguyên. Hash so sánh source/test đầu-cuối: {len(end['sourceFilesChangedDuringQA'])} file thay đổi. Chi tiết baseline.json và end-state.json.
- Runtime QA cổng2009, web4203 (proxy riêng qa_tester/proxy.json). Các kết nối JDBC/JWT không in ra báo cáo.
- SELECT DB_NAME xác nhận hrm_test; 50 bảng. Không chạy script tạo DB/migrations vì DB test đã có sẵn; chưa chứng minh migration provenance đầy đủ.
- Backup COPY_ONLY + CHECKSUM và RESTORE VERIFYONLY đạt, xem backup.json. Không RESTORE tự động; khôi phục hrm_test sau khi dừng các client và xác nhận không có thay đổi cần giữ từ người khác, dùng file backup đã ghi. Không dùng hrm_db làm đích restore.
- Ngoại lệ SQL chuẩn bị: prepare-account.ps1 theo README_TAI_KHOAN.md, chỉ admin.sample trên hrm_test: mở khóa, thay hash bằng hash của README, tăng token_version, cập nhật thời điểm. Không xuất giá trị mật khẩu/hash. Không reset hàng loạt.
- CRUD QA qua API: tiền tố {state['prefix']}; không dùng UPDATE/DELETE SQL dựng tình huống. Không thực thi job nghỉ việc toàn cục. QA data còn để tái hiện, QA admin đã khóa qua API; tài khoản mẫu test vẫn hoạt động sau chuẩn bị.
- Rủi ro: launcher QA tắt scheduler processor, nhưng ApplicationReadyEvent là cơ chế riêng; bản test mặc định có thể khởi tạo ContractExpiryJob.onReady. Chưa có bằng chứng đảm bảo job không ảnh hưởng dữ liệu có sẵn. Đọc OPEN_QUESTIONS.md mục6; không được diễn đạt là hrm_db chắc chắn nguyên vẹn.

## Lệnh đã chạy (tham số nhạy cảm không ghi)

- git status --short; git branch --show-current; git rev-parse HEAD; git diff --stat.
- probe-db.ps1 / backup.ps1 / select.ps1; tất cả connection helper kiểm DB_NAME trước thao tác.
- npm --prefix frontend run build: PASS (angular-build.log).
- javac QA-only QaBackendLauncher; start-backend.ps1 trên port2009. Không đổi source ứng dụng.
- ng serve --host127.0.0.1 --port4203 với proxy QA riêng.
- api_test.py, api_workflows.py, api_edge_cases.py (bản không DELETE), security_scan.py, recheck.py, session_checks.py.
- mvn -o -Dtest=<82 lớp trong java-selector.json> -DargLine=<cached Mockito agent> test. Không chạy full suite không chọn lọc vì các lớp SQL ghi fixture trực tiếp.
- Một lượt trong sandbox và một lượt ngoài sandbox để phân biệt lỗi loopback; kết quả cuối từ lượt ngoài sandbox, không cộng hai lượt thành số ca độc lập.

Kết quả Java: {metrics['java']['tests']} test, {metrics['java']['pass']} PASS, {metrics['java']['failures']} FAIL, {metrics['java']['errors']} ERROR, {metrics['java']['skipped']} SKIP. Các lớp bị loại là NOT-RUN, không cộng vào JUnit skip.

## Phân loại test đỏ

'''+ '\n'.join(classification)+f'''

Không sửa test để xanh. G1EndpointAccessTest hai assertion403 nhận200 cần đối chiếu quyền mới; chưa tự kết luận lộ dữ liệu từ test fixture đó.

## Git status đầu phiên

```text
{base['status']}
```

## Git status cuối lúc kiểm hash

```text
{end['status']}
```

Mọi thay đổi source/test nêu trong hai status đã tồn tại trước QA; chỉ qa_tester mới được tạo trong lượt này. Các build output ignored không tính là source. Các server QA tạm đã được dừng; chưa commit/push báo cáo.
'''
write('ENVIRONMENT.md',env)
sev=collections.Counter(b['severity'] for b in bugs)
summary=f'''# Kết quả kiểm thử HRM Center

**Chưa sẵn sàng bàn giao.** Có hai lỗi S1 đã tái hiện: HR đọc quỹ phép ngoài phạm vi và logout500 không thu hồi token. Chỉ báo cáo, chưa sửa mã nguồn.

- Repo: XuanAnh / {base['head'][:7]}, kiểm working tree có thay đổi sẵn. Source/test hash đầu-cuối không đổi.
- API: {metrics['http_requests']} lượt, {metrics['http'].get('PASS',0)} PASS, {metrics['http'].get('FAIL',0)} FAIL, {metrics['http'].get('OBSERVED',0)} OBSERVED. Đây là số request, không phải số chức năng nghiệm thu. /hello và danh mục nghỉ cho EMPLOYEE là quan sát cần chốt/được giải thích, không tự coi lỗi bảo mật.
- Java có chọn lọc:526 test —399 PASS,120 FAIL,6 ERROR,1 SKIP. Nhiều ca do fixture/config cũ; xem phân loại. 12 lớp có nguy cơ SQL fixture không chạy. Không tuyên bố full suite đạt.
- UI:107 lượt mở route —77 Tier1 tại7 độ rộng (gồm6 mốc yêu cầu),30 smoke; chưa thấy tràn ngang toàn trang. Chỉ một drawer được mở ở390; chưa kiểm mọi form/CRUD ở mọi kích thước. Build Angular PASS.
-165 hàng checklist gốc đều có disposition trong CSV:7 FAIL (có nhánh lỗi),158 NOT-RUN đủ toàn hàng; nhiều subcase đã chạy bên dưới. Không coi một request PASS là cả hàng đã hoàn thành. BLOCKED nêu bằng lý do trong OPEN_QUESTIONS và ENVIRONMENT.

| Lỗi | Mức | Task | Kết quả chính |
|---|---|---|---|
'''
for b in bugs:summary+=f"| {b['id']} | {b['severity']} | {b['task']} | {b['title']} |\n"
summary+='''
Theo mức: S1=2, S2=0 đã xác minh, S3=3, S4=0. Theo task: FE001=2, FE005/FE006 dùng chung=1, FE006=1, FE009/Tier2=1 (lỗi dùng chung không cộng hai lần).

Bốn UI còn placeholder: phụ cấp, khen thưởng/kỷ luật, tiền lương, phiếu lương. Không tính là lỗi chạy, không xác nhận hoàn thành. /accounts và /reports có UI thật.

Cần sửa/kiểm lại5 lỗi, rà test fixture/cấu hình, chốt rủi ro job lúc khởi động, rồi chạy các luồng A–F đầy đủ trước nghiệm thu. Chưa làm xong kiểm thử toàn bộ prompt: lịch nghỉ phép hợp lệ có giờ công, job tới hạn, race duyệt đơn, rollback ép lỗi,100 nhân viên QA, đối chiếu mọi cột/ràng buộc, Edge độc lập và mọi form responsive đều chưa đủ bằng chứng.

**Rủi ro cần đối chiếu:** một số SpringBootTest đã đọc cấu hình hrm_db rồi bị guard chặn trước fixture; job ApplicationReadyEvent có thể chạy trước guard. Không có baseline DB chính nên không thể bảo đảm không có tác động tự động; không tự restore. Các helper kiểm thử API/SQL của phiên dùng hrm_test đã backup. Chi tiết ENVIRONMENT.md.

Báo cáo cá nhân trong qa_tester; không dùng git add . để đẩy cả thư mục này.
'''
write('SUMMARY.md',summary)
print('Reports created:',len(list(Q.glob('*.md'))),'API metrics',metrics['http'],'Java',metrics['java'])
