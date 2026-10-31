"""Build review artifacts from recorded evidence; never connects to a database."""
from pathlib import Path
import json, csv, collections, difflib

ROOT = Path(__file__).resolve().parents[2]
Q = ROOT / 'qa'
E = Q / 'evidence'
def read(name): return json.loads((E/name).read_text(encoding='utf-8-sig'))
def write(name, content): (Q/name).write_text(content, encoding='utf-8')
http=read('http-results.json'); suite=read('existing-suite-results.json')
ui=read('web-calibrated-results.json'); oracle=read('payroll-oracle-results.json')
runtime=read('runtime-beans.json'); hc=collections.Counter(x['status'] for x in http)
sc=collections.Counter(x['status'] for x in suite)

# Keep HTTP probes separate from the composite acceptance cases in the prompt.
# A route returning 401 does not establish its authenticated business workflow.
rows=list(csv.DictReader((Q/'test-cases.csv').open(encoding='utf-8-sig',newline='')))
for row in rows:
    if row['status'] not in ('CHƯA-ĐẶC-TẢ','N/A'):
        row['status']='NOT-RUN'
        row['actual']='Chưa đủ bằng chứng để kết luận toàn bộ tiêu chí; xem coverage-matrix.md.'
        row['evidence']=''
    matches=[r for r in http if r['id'].startswith(row['ID']+'-')]
    if matches:
        row['actual']='Có probe liên quan; ID probe không đồng nghĩa hoàn tất ca tổng hợp: '+', '.join(r['id'] for r in matches)
        row['evidence']='evidence/http-results.json'
    overrides={
        'AUTH-04':('PARTIAL','QA retired đồng thời is_active=0 và DA_NGHI bị 401; chưa cô lập hai điều kiện độc lập.','evidence/http-results.json'),
        'AUTH-05':('PARTIAL','QA retired đồng thời is_active=0 và DA_NGHI bị 401; chưa kiểm DA_NGHI với account còn active.','evidence/http-results.json'),
        'AUTH-15':('PASS','Web giữ /login và báo cổng quản lý chỉ ADMIN/HR; backend quản lý payroll vẫn lỗi quyền, xem RBAC-01.','evidence/web-session-results.json'),
        'RBAC-01':('FAIL','Có phản ví dụ: EMPLOYEE tạo draft payroll HTTP 201. Các endpoint khác chưa quét hết từng quyền.','evidence/http-results.json'),
        'RBAC-02':('FAIL','EMPLOYEE A đọc batch details có payroll_detail của B HTTP 200; chưa kiểm mọi loại dữ liệu khác.','evidence/http-results.json'),
        'RBAC-09':('FAIL','EMPLOYEE lọt payroll-periods/payroll-batches và shifts; chi tiết từng endpoint trong coverage.','evidence/http-results.json'),
        'ATT-01':('FAIL','-30/-16 bị chặn, +16 bị chặn, trái quyết định mới của người dùng; probe policy, chưa HTTP clock.','evidence/payroll-policy-probe.json'),
        'NFR-02':('FAIL','Lịch sử có literal secret, khác mật khẩu hiện tại; chưa xác minh secret cũ đã thu hồi.','evidence/secret-history-summary.json'),
    }
    if row['ID'] in overrides:row['status'],row['actual'],row['evidence']=overrides[row['ID']]
    if row['ID']=='ATT-01':
        row['scenario']='Check-in sớm -31/-30/-16/-15, đúng giờ, muộn +15/+16; anomaly sau ngưỡng 15'
        row['expected']='Theo người dùng chốt: sớm tối đa 30 phút; sau +15 vẫn lưu giờ thực tế và flag muộn. Giới hạn cuối ca chưa chốt.'
    if row['ID'].startswith('TC-'):
        result=next((r for r in oracle if r['case']==row['ID']),None)
        if result:
            row['status']='PASS-ENGINE' if result['status']=='PASS' else 'PASS-THEO-CODE'
            row['actual']='Engine-only: net '+result['actualNet']+', delta '+result['delta']+'; SQL pipeline NOT-RUN. TC-06 không phát hiện trần qua net vì thu nhập tính thuế âm; xem MEAL-CAP-01.'
            row['evidence']='evidence/payroll-oracle-results.json'
with (Q/'test-cases.csv').open('w',encoding='utf-8-sig',newline='') as f:
    w=csv.DictWriter(f,fieldnames=['ID','scenario','expected','status','actual','evidence']);w.writeheader();w.writerows(rows)

write('TEST_REPORT.md',f'''# Báo cáo QA HRM-Center — 31/10/2026

**Kết luận: CHƯA SẴN SÀNG nghiệm thu toàn hệ thống.** Có lỗi quyền truy cập bảng lương và tính lương đã tái hiện. Đây là báo cáo bằng chứng hiện có, không xác nhận hoàn thành toàn bộ 218 ca.

| Lớp kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Bộ Java hiện có | {len(suite)}: {sc['PASS']} PASS, {sc['FAIL']} FAIL, {sc['ERROR']} ERROR, {sc['SKIP']} SKIP | Có lỗi harness/fixture; không coi mọi test đỏ là bug sản phẩm |
| HTTP với JWT thật | {hc['PASS']} PASS, {hc['FAIL']} FAIL, {hc['OBSERVED']} quan sát | Dữ liệu QA trong hrm_test; không gọi tính lương ghi từ provider giả |
| Oracle lương độc lập | 11 tình huống: 4 PASS, 4 khớp code, 3 FAIL | Engine-only; 4 ca bậc thuế cao chưa được đặc tả chính thức |
| Web responsive | {len(ui)} lượt mở: 26 route × 4 kích thước CSS | 1280/768/390/320 px; không tràn ngang toàn trang; chưa kiểm hết form CRUD |
| Angular production build | PASS | Không thay thế kiểm thử nghiệp vụ |
| npm audit | 21 cảnh báo: 4 high, 16 moderate, 1 low | Chưa cập nhật dependency tự động |

Ưu tiên: (1) chặn EMPLOYEE tạo/xem bảng lương của người khác; (2) thay provider giả bằng dữ liệu thật trước nghiệm thu lương; (3) sửa căn cứ NGÀY/GIỜ, trần miễn thuế ăn trưa và JSON snapshot; (4) đồng bộ check-in theo quyết định đã chốt.

Chi tiết: [defects.md](defects.md), [coverage-matrix.md](coverage-matrix.md), [payroll-reconciliation.md](payroll-reconciliation.md), [recommendations.md](recommendations.md).

Baseline được kiểm thử trước khi sửa source. Sau khi người dùng cho phép, đã sửa PayrollBatchController, PayrollPeriodController, PayrollCalculationEngine và thêm 2 file test; xem fix-verification.md. Giữ nhánh Duong, không ghi hrm_db, không commit/push. Các bản sao lưu COPY_ONLY/CHECKSUM đã RESTORE VERIFYONLY thành công; chưa thực hiện khôi phục.
''')

defects='''# Các vấn đề đã xác minh

Mức S1: chặn nghiệm thu bảo mật/tài chính. S2: cần sửa trước bàn giao. Các phát hiện không mặc định quy về nhiệm vụ Xuân Anh; cần người sở hữu module xử lý.

## D01 — S1 — EMPLOYEE truy cập và tạo bảng lương quản trị

- Đăng nhập bằng QA EMPLOYEE; GET `/payroll-batches`, `/payroll-periods` trả 200.
- ADMIN tạo kỳ QA mới; cùng EMPLOYEE POST `/payroll-batches/draft` trả **201**, phải 403.
- Gắn một payroll_detail của QA nhân viên B vào chính batch QA vừa tạo; EMPLOYEE A GET `/payroll-batches/{id}/details` trả **200** chứa employeeId của B, phải 403.
- Đây là truy cập chéo có bằng chứng, không chỉ suy luận từ thiếu annotation. Không dùng hồ sơ lương thật để tái hiện.
- Nguồn: PayrollBatchController, PayrollPeriodController; evidence/http-results.json (PAY-employee-create-draft, PAY-other-details).
- Cần role + permission backend, actor lấy từ principal, API phiếu lương cá nhân riêng có kiểm tra chủ sở hữu. Patch đề xuất mới chặn cấp controller, chưa thay thế rà soát service/actor đầy đủ.

## D02 — S1 — Runtime lương đang dùng nguồn giả lập

- QA chạy HrmApplication thật, profile local, SQL hrm_test: bean salaryProvider=MockSalaryContractProvider; attendanceProvider=MockAttendanceDataProvider.
- Provider trả lương/công cố định; kết quả engine đúng không chứng minh truy vấn hợp đồng/công/đơn thật đúng.
- Nguồn: evidence/runtime-beans.json, service/provider/impl. Chưa chứng minh các profile khác có cùng wiring.
- Cần provider thật và chỉ cho phép mock trong profile demo/test rõ ràng; thêm integration test thay dữ liệu nguồn làm tiền lương thay đổi đúng.

## D03 — S1 — Engine bỏ qua căn cứ trả NGÀY/GIỜ

- NGÀY 1.000.000 × 22 công: gross kỳ vọng 22.000.000; thực tế 1.000.000. Bảo hiểm phải quy đổi tháng ×22 theo quyết định nhóm.
- GIỜ 100.000 × 176 giờ: gross kỳ vọng 17.600.000; thực tế 100.000. Bảo hiểm phải quy đổi ×8×22.
- Engine luôn chia baseSalary cho standardDays và bảo hiểm luôn ×10,5% baseSalary. Đây là lỗi engine; chưa đối soát SQL end-to-end.
- evidence/payroll-oracle-results.json DAILY-01/HOURLY-01. Net NGÀY dùng bậc thuế 2 chỉ là tham chiếu code; lỗi gross và căn cứ bảo hiểm không phụ thuộc bậc thuế đó.

## D04 — S2 — Miễn thuế toàn bộ tiền ăn, chưa áp trần 730.000 theo spec đồ án

- Lương tháng 15 triệu, 22 công, ăn trưa 800.000: thuế kỳ vọng 124.750, thực tế 121.250; net cao hơn 3.500.
- Ca nằm trong bậc thuế 1 đã chốt. Không kết luận đây là cách áp thuế pháp luật hiện hành.
- evidence/payroll-oracle-results.json MEAL-CAP-01. Cần cap tổng khoản AN_TRUA, tránh cộng nhiều dòng vượt trần.

## D05 — S2 — Snapshot JSON không escape chuỗi

- Tên QA chứa dấu nháy kép và xuống dòng; gọi buildSnapshotJson thực tế bằng probe: JSON parser từ chối.
- evidence/payroll-policy-probe.json snapshotWithQuotesValid=false.
- Cần JSON serializer có schema/version; kiểm tra lưu/đọc và hoàn tác giao dịch khi snapshot lỗi. Chưa xác nhận bản ghi SQL bị lưu hỏng vì không gọi batch calculate với nguồn giả.

## D06 — S2 — Thiếu salary component vẫn sinh dòng tiền fallback

- Repository danh mục trống: engine vẫn tạo 4 dòng, fallback component_id=1 và direction ADD.
- Có nguy cơ gắn sai thành phần hoặc cộng khoản khấu trừ. Chưa khẳng định SQL đã lưu dòng sai.
- evidence/payroll-policy-probe.json missingComponentsProducesLines=4. Cần fail rõ ràng trước ghi, không tự dựng ID.

## D07 — S2 — Check-in lệch quy tắc đã chốt

- Clock probe: -30 và -16 phút bị từ chối; +16 phút bị từ chối.
- Quyết định người dùng: sớm tối đa 30 phút; sau +15 vẫn ghi giờ thực tế, đánh dấu muộn.
- AttendanceAnomalyPolicy hiện đánh dấu LATE_ARRIVAL ngay sau giờ bắt đầu, chưa đợi +15.
- evidence/payroll-policy-probe.json; AttendanceClockPolicy.java và AttendanceAnomalyPolicy.java. Giới hạn cuối cùng được check-in sau hết ca chưa chốt; không tự đặt trong patch.

## D08 — S2 — Kết quả 401/403 không nhất quán với JWT hợp lệ

- EMPLOYEE GET `/api/management/schedules`, `/api/management/attendance`: 401 thay vì 403.
- Điều này không chứng minh vượt quyền nhưng có thể làm frontend đăng xuất sai khi thiếu quyền.
- GET `/api/management/shifts` trả 200 cho EMPLOYEE không có lịch; cần kiểm tra và giới hạn dữ liệu lịch cá nhân theo ma trận.
- evidence/http-results.json, nhóm RBAC-employee-api-management-*.

## D09 — S2 — Endpoint lock thiếu chặn quyền trước khi chạm nghiệp vụ

- EMPLOYEE POST lock batch QA, truyền actorAccountId của HR khác: trả 500 thay vì 403.
- DB trigger từ chối batch thiếu dòng tiền/snapshot; DB vẫn NHAP, locked_by=NULL. **Không có bằng chứng giả actor/chốt lương thành công.**
- Cần kiểm tra quyền trước service, lấy actor từ JWT; map xung đột nghiệp vụ thành 409 khi người có quyền thao tác.
- evidence/http-results.json PAY-employee-lock-spoof/PAY-lock-actor-evidence và qa-server.log.

## D10 — S2 — Bộ regression chưa xanh

- 17 FAIL/6 ERROR được tách theo từng class trong regression-failures.md. Có harness cũ không chạy filter/AOP, fixture thiếu và contract API thay đổi.
- Không sửa test bằng cách bỏ assertions hoặc nới quyền production. Phải đối chiếu rule rồi cập nhật fixture/harness.

## D11 — S2 — Lịch sử Git từng chứa mật khẩu datasource

- Commit 5b8f70e6582278f0c52c53475ca5f923b73b2ed3 có literal password; khác cấu hình hiện tại. application-local.properties hiện không tracked.
- Chưa xác minh mật khẩu cũ đã vô hiệu hóa. Không đưa mật khẩu vào báo cáo. evidence/secret-history-summary.json.

## D12 — Cảnh báo dependency và bàn giao schema

- npm audit: 4 high/16 moderate/1 low; xem dependency-findings.md, không tự chạy audit fix --force.
- DB hiện có các bảng/trigger không thấy trong phép đối chiếu CREATE TABLE của migration repo (evidence/schema-not-in-migrations.json). Đây là khoảng trống tái tạo môi trường cần kiểm chứng bootstrap riêng, chưa kết luận cài mới thất bại.
- Không thiếu bảng dbo mà source tham chiếu trực tiếp trong phép rà soát đã chạy.
'''
write('defects.md',defects)

lines=['# Đối soát lương','', 'Oracle dùng Python Decimal, làm tròn HALF_UP, 22 công/8 giờ, OT 1,5, bảo hiểm 10,5%, giảm trừ 11 triệu theo spec đồ án. NGÀY/GIỜ quy đổi căn cứ tháng và phân bổ phụ cấp theo quyết định người dùng. Không thẩm định pháp luật.','', '**Chỉ đối chiếu engine với đầu vào kiểm soát, chưa phải đối soát dữ liệu SQL → batch → phiếu lương.** Provider runtime giả là blocker cho kết luận này.','', '| Ca | Gross kỳ vọng | BH kỳ vọng | Net kỳ vọng | Net thực tế | Chênh lệch | Kết quả |','|---|---:|---:|---:|---:|---:|---|']
for r in oracle: lines.append('| '+' | '.join(r[k] for k in ['case','expectedGross','expectedInsurance','expectedNet','actualNet','delta','status'])+' |')
lines+=['','TC-03/05/07/08: PASS-THEO-CODE chỉ khớp thuật toán bậc 2–5 hiện có; chưa có kỳ vọng nghiệp vụ chính thức để nghiệm thu. DAILY-01 không dùng net bậc 2 làm kết luận tiền lương cuối cùng; gross và BH đã sai độc lập.','', 'Chưa kiểm chứng pipeline thực với đổi hợp đồng giữa kỳ, phụ cấp một phần kỳ, correction đã duyệt, nghỉ/OT, tính lại idempotent, lock/publish, điều chỉnh và tổng batch khớp phiếu. Cần làm sau khi có provider thật; không cộng những ca này vào PASS.']
write('payroll-reconciliation.md','\n'.join(lines)+'\n')

coverage=['# Phạm vi đã chạy','', 'Không cộng số lượt HTTP/Java/viewport vào 218 ca nghiệp vụ. Một ca tổng hợp còn điều kiện chưa kiểm chứng được giữ NOT-RUN; probe liên quan ghi riêng.','', '## HTTP endpoint × actor × bằng chứng','', '| Probe | Method | Endpoint | Actor | HTTP | Kết quả |','|---|---|---|---|---:|---|']
for x in http:
    if 'method' in x: coverage.append(f"| {x['id']} | {x['method']} | `{x['path']}` | {x['role']} | {x['actualStatus']} | {x['status']} |")
tested={(x.get('method'),x.get('path','').split('?')[0]) for x in http if 'method' in x}
coverage+=['','## Toàn bộ mapping runtime: mức bằng chứng','', 'Anonymous sweep xác nhận yêu cầu đăng nhập; không xác nhận quyền từng role hoặc nội dung nghiệp vụ. Route có biến path cần xem probe concrete tương ứng.','', '| Controller | Method | Mapping | Mức kiểm chứng |','|---|---|---|---|']
for r in runtime['routes']:
    for p in r['paths']:
        for m in r['methods']:
            level='Có HTTP probe trực tiếp' if (m,p) in tested else 'Có anonymous sweep; authenticated workflow NOT-RUN' if m=='GET' and p not in ['/api/health','/hello','/error'] and '*' not in p else 'NOT-RUN hoặc xem probe concrete'
            coverage.append(f"| {r['controller']} | {m} | `{p}` | {level} |")
coverage+=['','## Web responsive','', '26 route × CSS 1280,768,390,320 = 104 lượt. documentElement.scrollWidth <= innerWidth ở từng lượt. Không đảm bảo mọi nút/form đều không cắt chữ; không phải test app mobile. Có kiểm tra thao tác mở/đóng menu 320 px, chuyển VI/EN trang nhân viên. Bảng dữ liệu vẫn cuộn ngang bên trong theo thiết kế.','', '## Những phần chưa đủ bằng chứng','', '- CRUD/validation toàn bộ form, tất cả tổ hợp quyền ADMIN/HR/DEPARTMENT_HEAD/EMPLOYEE; HR ngoài scope chỉ kiểm một số endpoint.','- Token đổi mật khẩu/khóa giữa phiên, logout revocation tất cả tình huống, brute force/rate limit.','- Pipeline lương SQL đầy đủ, lock/publish/adjust và trigger tiền lương ở mọi loại bảng.','- Job chạy hai lần/nhiều instance, đổi ngày/múi giờ thực tế và race mỗi workflow. Có 2 test concurrency hiện có PASS không bao phủ tất cả race.','- Tải lớn, SLA p95/p99, export injection, accessibility và tất cả thông báo VI/EN.','- Bootstrap database sạch từ migration và restore drill. Không drop/reset DB test hiện tại để giả lập.','', 'Xem test-cases.csv để theo dõi 218 tiêu chí chưa nghiệm thu đầy đủ.']
write('coverage-matrix.md','\n'.join(coverage)+'\n')

failrows=[x for x in suite if x['status'] in ('FAIL','ERROR')]
reg=['# Phân loại bộ test đỏ','', '| Class | Số FAIL/ERROR | Chẩn đoán cần xử lý |','|---|---:|---|']
notes={'G1EndpointAccessTest':'standaloneSetup/mock services không chạy SecurityFilter/method-security; HTTP runtime anonymous sweep trả 401.','PermissionAuthorizationTests':'Context thiếu PersonnelScopeService sau khi service thêm dependency bắt buộc; thêm mock vào context test.','AttendanceServiceIntegrationTest':'Fixture tham chiếu schedule ID 4 không tồn tại; không chứng minh code attendance lỗi.','AttendanceWorkflowIntegrationTest':'Fixture ADMIN không khớp dữ liệu quyền/account của DB hiện tại.','ModuleRepositoryIntegrationTest':'H2 tạo payroll_periods đụng bảng đã tồn tại; cần sửa cô lập database/context.','WorkShiftDurationTest':'Test legacy create/duration không khớp API break-aware và giới hạn mới; đối chiếu spec trước sửa.','WorkShiftPermissionTest':'HR kỳ vọng được phép nhưng scope/permission fixture không khớp; cần kiểm tra context test.','WorkShiftValidationTest':'Kỳ vọng IllegalArgumentException trong khi API hiện trả ResponseStatusException; xác nhận status/message thay vì chỉ class exception.','WorkShiftValidCasesTest':'Legacy create bị 409 yêu cầu detailed break-aware API; phải cập nhật test đúng API còn được hỗ trợ.'}
for c,n in collections.Counter(x['className'] for x in failrows).items():reg.append(f"| {c} | {n} | {notes.get(c.split('.')[-1],'Xem evidence gốc.')} |")
reg+=['','## Từng test và lỗi gốc','']
for x in failrows: reg+=['### '+x['className']+'.'+x['name'], '', x['status']+': '+x['message'][:1800], '']
write('regression-failures.md','\n'.join(reg))

audit=read('npm-audit.json'); dep=['# Cảnh báo dependency','', 'Kết quả npm audit hiện tại; không mặc định mọi advisory khai thác được qua ứng dụng. Chưa sửa lockfile.','', '| Package | Severity | Direct |','|---|---|---|']
for name,v in audit.get('vulnerabilities',{}).items():dep.append(f"| {name} | {v['severity']} | {v.get('isDirect')} |")
write('dependency-findings.md','\n'.join(dep)+'\n')

write('acceptance-mapping.md','''# Đối chiếu nghiệm thu nhiệm vụ Xuân Anh

| Task | Chức năng | Kết luận QA hiện tại |
|---|---|---|
| FE-001 | Layout, route, phiên, kết nối | Build/đăng nhập ADMIN và route smoke có bằng chứng; role matrix toàn bộ chưa đủ |
| FE-002 | Nhân viên/hồ sơ | GET scope/privacy một số actor PASS; responsive danh sách PASS; CRUD form và toàn bộ validation chưa nghiệm thu |
| FE-003 | Phòng ban/trưởng phòng/chức vụ | Route smoke PASS; chưa xác nhận đầy đủ create/update/end-term/primary action |
| FE-004 | Phân công/điều chuyển | Route smoke; assignment concurrency test hiện có PASS, không thay thế workflow UI |
| FE-005 | Trình độ/bằng cấp | Route smoke; chưa nghiệm thu đầy đủ primary/date validation/CRUD |
| FE-006 | Loại nghỉ/quỹ phép/đơn phép | Route smoke và một số regression; chưa chạy trọn toàn bộ business edge case |
| FE-007 | Nghỉ việc/duyệt/chấm dứt | Route smoke; approval lock concurrency test hiện có PASS, job và rehire cần ca riêng |
| FE-008 | Lý do/audit | API audit trái quyền bị chặn trong probe; chưa đối chiếu đầy đủ before/after cho mọi thao tác |
| FE-009 | Tích hợp/bàn giao | Chưa đạt: suite đỏ và lỗi tài chính/bảo mật liên module |

accounts, allowances, decisions, payroll, payslips, reports là 6 placeholder trong bản frontend kiểm thử. Ghi NOT-IMPLEMENTED, không gọi là runtime bug và không tự nhận là nhiệm vụ Xuân Anh phải viết thay nhóm. Cần nhóm xác nhận người phụ trách và deadline trước nghiệm thu chung.
''')
write('recommendations.md','''# Thứ tự xử lý và kiểm thử lại

1. **P0 — Quyền lương:** người phụ trách payroll bổ sung role/permission backend cho cả period/batch; actor lấy từ principal. Test ADMIN có quyền, ADMIN thiếu permission, HR đúng/ngoài scope, EMPLOYEE và anonymous; assert không có DB write khi bị chặn.
2. **P0 — Nguồn lương thật:** thay provider giả ở môi trường nghiệm thu; nguồn hợp đồng, phân công, công/OT/nghỉ đã duyệt phải theo kỳ/ngày hiệu lực. Không dùng data giả để chốt một batch thật.
3. **P1 — Engine:** căn cứ THANG/NGAY/GIO và BH quy đổi theo quyết định nhóm; cap tổng AN_TRUA 730.000 theo spec; fail danh mục component thiếu; serialize JSON bằng thư viện. Chốt bậc thuế >1 trước kết luận tiền cuối cùng.
4. **P1 — Chấm công:** sớm 30; sau 15 ghi thực tế và flag muộn. Chốt giới hạn check-in sau hết ca; không biến mất dữ liệu chấm công bằng việc chặn giờ đến muộn. Test biên tới giây/phút, ca qua đêm, ownership, concurrency.
5. **P1 — Regression:** sửa harness/fixture đúng hợp đồng; không bỏ assertion hoặc hạ bảo mật. Chạy lại nhóm liên quan rồi toàn bộ suite.
6. **P1 — Dữ liệu khóa:** test giao dịch QA riêng cho INSERT/UPDATE/DELETE tiền lương/snapshot/details sau lock, idempotency/adjust và DB trigger. Đối chiếu tổng batch/detail/phiếu bằng oracle SQL độc lập.
7. **P2 — UI:** hoàn tất 6 placeholder theo người sở hữu; form CRUD/validation/mobile drawer/buttons/VI-EN từng thông báo, chứ không chỉ route/build.
8. **P2 — Vận hành:** migration bootstrap sạch, restore drill, rotation mật khẩu cũ nếu chưa làm; dependency upgrade có review; tách cấu hình secret khỏi source.

Các patch ghi nhận đề xuất ở thời điểm baseline. Trạng thái áp dụng mới nhất xem fix-verification.md nếu có; không sửa hàng loạt ngoài các lỗi người dùng đã cho phép.

Các lệnh automation có thể tạo dữ liệu QA, phải chạy riêng trên hrm_test với backup; đọc script trước khi chạy lại. Không dùng chúng với hrm_db. Runtime QA tắt scheduler để không tạo side effect ngoài ca chủ động; điều đó đồng nghĩa chưa kiểm chứng job tự chạy.
''')

# Minimal scoped proposals. Production files are read only here.
(Q/'patches').mkdir(exist_ok=True)
def patch(name, changes):
    if (Q/'patches'/name).exists():return # Preserve proposals recorded against the original baseline.
    out=''
    for rel,old,new in changes:
        out+=''.join(difflib.unified_diff(old.splitlines(True),new.splitlines(True),fromfile='a/'+rel,tofile='b/'+rel))
    (Q/'patches'/name).write_text(out,encoding='utf-8')
changes=[]
for filename in ['PayrollBatchController','PayrollPeriodController']:
    rel='HRM_System/src/main/java/com/hrm/controller/'+filename+'.java'; old=(ROOT/rel).read_text(encoding='utf-8')
    new=old.replace('import org.springframework.http.HttpStatus;','import org.springframework.http.HttpStatus;\nimport org.springframework.security.access.prepost.PreAuthorize;')
    new=new.replace('@RestController','@RestController\n@PreAuthorize("hasAnyRole(\'ADMIN\', \'HR\') and hasAuthority(\'PAYROLL_MANAGE\')")',1)
    changes.append((rel,old,new))
patch('01-payroll-controller-gate.PROPOSED.diff',changes)
rel='HRM_System/src/main/java/com/hrm/service/engine/PayrollCalculationEngine.java';old=(ROOT/rel).read_text(encoding='utf-8')
new=old.replace('BigDecimal taxableIncome = grossIncome.subtract(taxExemptAllowance);','BigDecimal taxableIncome = grossIncome.subtract(taxExemptAllowance.min(new BigDecimal("730000")));')
patch('02-meal-cap.PROPOSED.diff',[(rel,old,new)])
rel='HRM_System/src/test/java/com/hrm/security/PermissionAuthorizationTests.java';old=(ROOT/rel).read_text(encoding='utf-8')
new=old.replace('import com.hrm.service.EmployeeService;','import com.hrm.service.EmployeeService;\nimport com.hrm.service.PersonnelScopeService;').replace('context.register(MethodSecurityConfiguration.class);','context.register(MethodSecurityConfiguration.class);\n        context.registerBean(PersonnelScopeService.class, () -> mock(PersonnelScopeService.class));')
patch('03-method-security-test-context.PROPOSED.diff',[(rel,old,new)])
write('patches/README.md','''# Đề xuất chưa áp dụng

01: thêm gate quản trị lương theo tên permission hiện tại; cần xác nhận phạm vi HR/actor từ JWT và test service. Chưa giải quyết actorAccountId, scope HR, toàn bộ API payroll khác.

02: cap tổng miễn thuế AN_TRUA 730.000 theo spec đồ án; chưa sửa NGAY/GIO, bảo hiểm, provider, component fallback hoặc snapshot.

03: bổ sung dependency mock thiếu của context test; không làm @Autowired optional và không tắt method security. Cần chạy lại context để xác minh còn dependency/assertion nào phải cập nhật.

Đây là mô tả đề xuất lúc baseline; trạng thái áp dụng/retest mới nhất xem fix-verification.md nếu có. Clock chưa có diff vì giới hạn cuối ca chưa được chốt. Tham khảo D07 và quyết định người dùng trong open-questions.md.
''')
if (E/'http-retest-results.json').exists():
    after=read('http-retest-results.json'); ac=collections.Counter(r['status'] for r in after)
    verified={r['id']:r for r in after}
    fixed=['PAY-employee-create-draft','PAY-other-details','PAY-employee-lock-spoof']
    fixes='''# Xác minh bản sửa đã được cho phép

Người dùng cho phép sửa các lỗi phân quyền lương và trần ăn trưa sau khi nhận bằng chứng; các file production dưới đây đã thay đổi, không chỉ có patch đề xuất.

- PayrollBatchController: quản lý lương yêu cầu ADMIN/HR + PAYROLL_MANAGE; đọc chấp nhận PAYROLL_VIEW hoặc PAYROLL_MANAGE. EMPLOYEE bị chặn kể cả có authority payroll. Lock/publish lấy accountId từ principal, bỏ qua query actorAccountId để giữ tương thích URL.
- PayrollPeriodController: áp cùng gate đọc/ghi, tránh truy cập quản trị chỉ cần đăng nhập.
- PayrollCalculationEngine: trần miễn thuế AN_TRUA tính trên tổng nhiều dòng, tối đa 730.000 theo spec đồ án.
- Test mới: PayrollManagementAuthorizationTest và PayrollMealExemptionTest: **10/10 PASS**, 0 fail/error/skip. Không dùng SQL trong hai class này. Có test ADMIN/HR hợp lệ, thiếu permission, EMPLOYEE có permission giả, chỉ quyền xem và giả actor.

## HTTP thật sau sửa

'''
    fixes+=f"{len(after)} bản ghi: {ac['PASS']} PASS, {ac['FAIL']} FAIL, {ac['OBSERVED']} quan sát; evidence/http-retest-results.json. Runtime mới dùng source đã build, JWT thật, hrm_test; tắt server/tài khoản QA sau chạy.\n\n"
    for key in fixed+['PAY-admin-create-draft']:
        r=verified[key];fixes+=f"- {key}: HTTP {r['actualStatus']}, {r['status']}.\n"
    fixes+='''
Batch QA vẫn NHAP/locked_by=NULL sau request EMPLOYEE; admin tạo draft vẫn 201. Test method-security xác nhận lock/publish chuyển actor JWT 71 sang service thay vì ID giả 999. Chưa thực hiện admin chốt/công bố batch lương đầy đủ trên SQL.

## Oracle độc lập sau sửa

MEAL-CAP-01: thuế 124.750, net 14.100.250, chênh 0. Trước sửa net cao hơn 3.500. Các ca TC-01…08 không thay đổi kết quả. DAILY-01/HOURLY-01 vẫn FAIL; không gọi là đã sửa NGÀY/GIỜ. evidence/payroll-oracle-retest.json.

## Còn mở

D02/D03/D05/D06/D07 chưa sửa. D08 vẫn 3 probe FAIL: shifts trả 200, schedules/attendance trả 401 thay 403. D09 đã chặn EMPLOYEE trước nghiệp vụ; mapping lỗi 500 khi ADMIN hợp lệ gặp trigger vẫn chưa sửa. Chưa chạy lại toàn bộ 541 test sau sửa; kết quả 477/17/6/41 là baseline, không phải suite cuối cùng.

Patch 01/02 trong patches/ là bản đề xuất ban đầu đã được áp dụng và bổ sung actor/read permission như mô tả; không áp lại máy móc. Patch 03 context test vẫn chưa áp dụng.
'''
    write('fix-verification.md',fixes)
    with (Q/'fix-verification.md').open('a',encoding='utf-8') as f:
        f.write('\n## Dọn phiên QA và giữ workspace\n\nBackend QA 2009 và frontend QA 4202 đã dừng; không dừng server người dùng 2005/4200. SQL kiểm tra 21 tài khoản QA ngày 31/10: activeQaAccounts=0. File mật khẩu QA tạm đã xóa. Fixture QA được giữ để đối chiếu, không xóa cứng lịch sử. Không còn unmerged paths; staged diff giữ đúng hash baseline, các source untracked cũ không đổi. Xem cleanup-database.json và final-worktree-verification.json trong evidence/.\n\nBackend đang chạy của người dùng chưa được restart để nạp bản sửa; cần restart bằng cấu hình hiện có trước khi kiểm tra trên cổng làm việc bình thường. Không commit/push trong lượt này.\n')
    note='**Cập nhật sau sửa được phép:** đã chặn các API payroll bị lọt và sửa trần ăn trưa; 10 test mới PASS, HTTP retest 124 PASS/3 FAIL/1 quan sát. Xem [fix-verification.md](fix-verification.md). Các kết quả bên dưới là baseline để giữ lịch sử.\n\n'
    for name in ['TEST_REPORT.md','defects.md','recommendations.md','patches/README.md']:
        p=Q/name;p.write_text(note+p.read_text(encoding='utf-8'),encoding='utf-8')
    with (Q/'coverage-matrix.md').open('a',encoding='utf-8') as f:
        f.write('\n## HTTP retest sau bản sửa được phép\n\n| Probe | HTTP | Kết quả |\n|---|---:|---|\n')
        for r in after:
            if r['id'] in fixed or r['status']=='FAIL' or r['id']=='PAY-admin-create-draft':f.write(f"| {r['id']} | {r.get('actualStatus','—')} | {r['status']} |\n")
    with (Q/'payroll-reconciliation.md').open('a',encoding='utf-8') as f:
        f.write('\nSau bản sửa được phép: MEAL-CAP-01 PASS (delta 0), TC-01…08 không đổi; DAILY-01/HOURLY-01 vẫn FAIL. Xem evidence/payroll-oracle-retest.json.\n')
print(json.dumps({'http':dict(hc),'java':dict(sc),'responsiveCases':len(ui),'compositeCases':len(rows),'report':'qa/TEST_REPORT.md'},ensure_ascii=False))
