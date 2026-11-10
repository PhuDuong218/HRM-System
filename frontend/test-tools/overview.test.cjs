const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

function pending(context, changes = {}) {
  const ready = { count: 0, state: 'READY' };
  context.http.expectOne('/api/overview/pending').flush({ updatedAt:'2026-11-10T00:00:00Z',
    attendance:ready, leave:ready, overtimeApproval:ready, overtimeActual:ready, ...changes });
}

test('HR overview uses its scoped endpoint while admin retains report access', async () => {
  const proxy = JSON.parse(fs.readFileSync(path.join(__dirname, '../proxy.conf.json'), 'utf8'));
  assert.equal(proxy['/api/overview'].target, 'http://localhost:2005');
  assert.equal(proxy['/api/overview'].pathRewrite, undefined);
  const output = path.join(__dirname, `.overview-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'overview-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  global.document = { documentElement: { lang: '' } };
  global.localStorage = { getItem: () => null, setItem: () => {} };
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const hr = setup();
    hr.page.ngOnInit();
    hr.profile.next({ role: 'HR' });
    pending(hr, { attendance:{count:4,state:'READY'} });
    hr.http.expectOne('/api/overview/hr').flush({
      employees: { byDepartment: [{ groupName: 'Đào tạo', employeeCount: 2 },
        { groupName: 'Kế toán', employeeCount: 1 }], byStatus: [{ groupCode: 'DANG_LAM', employeeCount: 3 }] },
      contracts: { effectiveCount: 1, expiringCount: 0 },
      work: { confirmedWorkDays: 3, confirmedAbsentDays: 0 }
    });
    assert.equal(hr.page.employeeState, 'ready');
    assert.equal(hr.page.totalEmployees, 3);
    assert.equal(hr.page.work.confirmedWorkDays, 3);
    assert.equal(hr.page.departments.length, 2);
    hr.http.expectNone('/api/reports/employees');
    hr.http.expectNone(request => request.url.startsWith('/api/reports/'));
    assert.equal(hr.page.isAdmin, false);
    assert.equal(hr.page.pending.attendance.count,4);
    hr.http.verify();
    hr.injector.destroy();

    const unassigned = setup();
    unassigned.page.ngOnInit();
    unassigned.profile.next({ role: 'HR' });
    pending(unassigned);
    unassigned.http.expectOne('/api/overview/hr').flush({ employees: { byDepartment: [], byStatus: [] },
      contracts: { effectiveCount: 0, expiringCount: 0 }, work: { confirmedWorkDays: 0, confirmedAbsentDays: 0 } });
    assert.equal(unassigned.page.totalEmployees, 0);
    assert.equal(unassigned.page.employeeState, 'ready');
    unassigned.http.verify();
    unassigned.injector.destroy();

    const failed = setup();
    failed.page.ngOnInit();
    failed.profile.next({ role: 'HR' });
    pending(failed);
    failed.http.expectOne('/api/overview/hr').flush('Unavailable', { status: 503, statusText: 'Unavailable' });
    assert.equal(failed.page.employeeState, 'error');
    assert.equal(failed.page.contractState, 'error');
    assert.equal(failed.page.workState, 'error');
    assert.equal(failed.page.pendingState, 'ready');
    failed.http.verify();
    failed.injector.destroy();

    const admin = setup();
    admin.page.ngOnInit();
    admin.profile.next({ role: 'ADMIN' });
    pending(admin);
    assert.equal(admin.page.isAdmin, true);
    const employee = admin.http.expectOne('/api/reports/employees');
    assert.equal(employee.request.headers.get('Authorization'), 'Bearer test-token');
    employee.flush({ byDepartment: [
      { groupName: 'B', employeeCount: 3 }, { groupName: 'A', employeeCount: 8 },
      { groupName: 'C', employeeCount: 7 }, { groupName: 'D', employeeCount: 6 },
      { groupName: 'E', employeeCount: 5 }, { groupName: 'F', employeeCount: 4 },
      { groupName: 'G', employeeCount: 2 }, { groupName: 'Chưa phân công', employeeCount: 1 }
    ], byStatus: [
      { groupCode: 'DANG_LAM', employeeCount: 9 }, { groupCode: 'DA_NGHI', employeeCount: 2 }
    ] });
    const contract = admin.http.expectOne(request => request.url === '/api/reports/contracts');
    assert.equal(contract.request.params.get('reportDate'), admin.page.today);
    assert.equal(contract.request.params.get('expiringWithinDays'), '30');
    contract.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
    const work = admin.http.expectOne(request => request.url === '/api/reports/work');
    assert.equal(work.request.params.get('fromDate'), admin.page.fromDate);
    assert.equal(work.request.params.get('toDate'), admin.page.today);
    work.flush({ confirmedWorkDays: 12, confirmedAbsentDays: 3 });
    assert.equal(admin.page.totalEmployees, 11);
    assert.equal(admin.page.activeEmployees, 9);
    assert.deepEqual(admin.page.departments.map(row => row.groupName),
      ['A', 'C', 'D', 'E', 'F', 'B', 'G', 'Chưa phân công']);
    assert.equal(admin.page.departmentMax, 8);
    assert.deepEqual(admin.page.ticks(admin.page.departmentMax), [8, 6, 4, 2, 0]);
    assert.match(admin.page.statusGradient, /^conic-gradient/);
    assert.equal(admin.page.contractState, 'error');
    assert.equal(admin.page.workState, 'ready');
    assert.equal(admin.page.workMax, 12);
    assert.equal(admin.page.loading, false);
    admin.http.verify();
    admin.injector.destroy();
  } finally {
    delete global.document;
    delete global.localStorage;
    fs.unlinkSync(output);
  }
});

test('pending overview isolates failures, distinguishes unavailable counts from zero and retries independently', async () => {
  const output=path.join(__dirname,`.overview-pending-${process.pid}.mjs`);
  await esbuild.build({entryPoints:[path.join(__dirname,'overview-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
  const oldDocument=global.document, oldStorage=global.localStorage;
  let context;
  try {
    global.document={documentElement:{lang:''}};global.localStorage={getItem:()=>null,setItem:()=>{}};
    const {setup}=await import(pathToFileURL(output).href);
    context=setup();const {page,profile,http}=context;
    page.ngOnInit();profile.next({role:'HR'});
    http.expectOne('/api/overview/hr').flush({employees:{byDepartment:[],byStatus:[]},contracts:{effectiveCount:0,expiringCount:0},work:{confirmedWorkDays:0,confirmedAbsentDays:0}});
    const request=http.expectOne('/api/overview/pending');
    assert.equal(request.request.params.keys().length,0);
    assert.equal(request.request.headers.get('Authorization'),'Bearer test-token');
    request.flush({updatedAt:'2026-11-10T00:00:00Z',attendance:{count:null,state:'DENIED'},leave:{count:0,state:'READY'},overtimeApproval:{count:null,state:'ERROR'},overtimeActual:{count:-1,state:'READY'}});
    assert.equal(page.pending.attendance.count,null);
    assert.equal(page.pending.attendance.state,'DENIED');
    assert.equal(page.pending.leave.count,0);
    assert.equal(page.pending.leave.state,'READY');
    assert.equal(page.pending.overtimeActual.state,'ERROR');
    assert.deepEqual(page.pendingCards.map(card=>[card.route,card.query.pending]),[['/attendance','unconfirmed'],['/requests','leave'],['/overtime','approval'],['/overtime','actual']]);
    page.loadPending();http.expectOne('/api/overview/pending').flush({}, {status:503,statusText:'Unavailable'});
    assert.equal(page.pendingState,'error');assert.equal(page.employeeState,'ready');
    page.loadPending();const stale=http.expectOne('/api/overview/pending');
    page.loadPending();assert.equal(stale.cancelled,true);
    pending(context,{overtimeActual:{count:9,state:'READY'}});
    assert.equal(page.pending.overtimeActual.count,9);
    assert.equal(page.loading,false);
    http.verify();
    context.injector.destroy();context=setup();
    context.page.ngOnInit();context.profile.next({role:'EMPLOYEE'});
    context.http.expectNone('/api/overview/pending');context.http.verify();
  } finally {
    context?.injector.destroy();fs.unlinkSync(output);
    if(oldDocument===undefined)delete global.document;else global.document=oldDocument;
    if(oldStorage===undefined)delete global.localStorage;else global.localStorage=oldStorage;
  }
});

test('overview links open all-time source queues, preserve pagination and cancel stale OT navigation',async()=>{
  const output=path.join(__dirname,`.overview-queues-${process.pid}.mjs`);
  await esbuild.build({entryPoints:[path.join(__dirname,'overview-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
  let attendanceContext,otContext;
  try{
    const {queues}=await import(pathToFileURL(output).href);
    attendanceContext=queues();attendanceContext.params({pending:'unconfirmed'});
    const attendance=attendanceContext.attendance();attendance.ngOnInit();
    attendanceContext.http.expectOne('/api/attendance-workspace/context').flush({role:'ADMIN',manage:true,mine:false});
    attendanceContext.http.expectOne('/api/attendance-workspace/departments').flush([]);
    const pending=attendanceContext.http.expectOne(req=>req.url==='/api/attendance-workspace/pending');
    assert.deepEqual(pending.request.params.keys(),['page']);
    assert.equal(pending.request.headers.get('Authorization'),'Bearer test-token');
    pending.flush({items:[{scheduleId:1,workDate:'2020-01-01'}],page:0,totalElements:21,totalPages:2,stats:null});
    assert.equal(attendance.pendingQueue,true);assert.equal(attendance.rows[0].workDate,'2020-01-01');
    attendance.loadRecords(1);
    attendanceContext.http.expectOne(req=>req.url==='/api/attendance-workspace/pending'&&req.params.get('page')==='1')
      .flush({items:[],page:1,totalElements:21,totalPages:2,stats:null});
    attendance.exitPendingQueue();
    const normal=attendanceContext.http.expectOne(req=>req.url==='/api/attendance-workspace/records');
    assert.ok(normal.request.params.get('from'));assert.equal(attendance.pendingQueue,false);
    normal.flush({items:[],page:0,totalElements:0,totalPages:0,stats:null});
    attendanceContext.http.verify();
    otContext=queues();otContext.params({pending:'approval'});
    const ot=otContext.overtime();ot.ngOnInit();
    otContext.http.expectOne('/api/overtime/workspace/context').flush({role:'ADMIN',manage:true,mine:false,canRegister:false});
    otContext.http.expectOne('/api/overtime/workspace/departments').flush([]);
    const old=otContext.http.expectOne(req=>req.url==='/api/overtime/workspace/pending');
    assert.equal(old.request.params.get('kind'),'approval');
    assert.equal(old.request.params.has('from'),false);
    otContext.params({pending:'actual'});
    assert.equal(old.cancelled,true);
    const actual=otContext.http.expectOne(req=>req.url==='/api/overtime/workspace/pending'&&req.params.get('kind')==='actual');
    actual.flush({items:[{ot:{overtimeId:4}}],page:0,totalElements:1,totalPages:1,stats:null});
    assert.equal(ot.tab,'actual');assert.equal(ot.rows[0].ot.overtimeId,4);
    ot.exitPendingQueue();
    const filtered=otContext.http.expectOne(req=>req.url==='/api/overtime/workspace/requests');
    assert.equal(filtered.request.params.get('status'),'DA_DUYET');
    assert.equal(filtered.request.params.get('actual'),'UNFINISHED');
    assert.ok(filtered.request.params.get('from'));
    filtered.flush({items:[],page:0,totalElements:0,totalPages:0});
    assert.equal(ot.pendingQueue,'');assert.equal(otContext.navigations.length,1);
    otContext.http.verify();
  }finally{attendanceContext?.injector.destroy();otContext?.injector.destroy();fs.unlinkSync(output);}
});
