const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('report requests carry authentication, filters and matching Excel parameters', async () => {
  const output = path.join(__dirname, `.report-service-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'report-service-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const { injector, service, http, page: createPage } = setup();
    const filters = { fromDate: '2026-09-01', toDate: '2026-09-30', reportDate: '2026-09-30', expiringWithinDays: 15, departmentId: '4' };
    let employee;
    service.get('employees', filters).subscribe(value => employee = value);
    const employees = http.expectOne(req => req.url === '/api/reports/monitor/employees');
    assert.equal(employees.request.params.get('departmentId'), '4');
    assert.equal(employees.request.headers.get('Authorization'), 'Bearer test-token');
    employees.flush({ byDepartment: [], byPosition: [], byStatus: [], byQualification: [] });
    assert.deepEqual(employee.byDepartment, []);

    for (const type of ['contracts', 'work', 'payroll']) {
      let result;
      service.get(type, filters).subscribe(value => result = value);
      const prefix = type === 'contracts' ? '/api/reports' : '/api/reports/monitor';
      const request = http.expectOne(req => req.url === `${prefix}/${type}`);
      assert.equal(request.request.params.get('fromDate'), '2026-09-01');
      assert.equal(request.request.params.get('toDate'), '2026-09-30');
      if (type === 'contracts') {
        assert.equal(request.request.params.get('departmentId'), '4');
        assert.equal(request.request.params.get('reportDate'), '2026-09-30');
        assert.equal(request.request.params.get('expiringWithinDays'), '15');
      } else assert.equal(request.request.params.get('departmentId'), '4');
      if (type === 'payroll') request.flush('{"totals":{"grossPayroll":9007199254740993.1234}}');
      else request.flush({ ok: true });
      if (type === 'payroll') assert.equal(result.totals.grossPayroll, '9007199254740993.1234');

      let downloaded;
      service.export(type, filters).subscribe(value => downloaded = value);
      const exported = http.expectOne(req => req.url === `${prefix}/${type}/export`);
      assert.equal(exported.request.headers.get('Authorization'), 'Bearer test-token');
      assert.equal(exported.request.params.toString(), request.request.params.toString());
      const blob = new Blob(['excel'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      exported.flush(blob);
      assert.equal(downloaded.size, 5);
    }
    service.export('employees', filters).subscribe();
    const employeeExport = http.expectOne(req => req.url === '/api/reports/monitor/employees/export');
    assert.equal(employeeExport.request.params.get('departmentId'), '4');
    assert.equal(employeeExport.request.params.get('fromDate'), '2026-09-01');
    assert.equal(employeeExport.request.params.get('toDate'), '2026-09-30');
    employeeExport.flush(new Blob(['excel']));
    let status;
    service.get('work', filters).subscribe({ error: error => status = error.status });
    http.expectOne(req => req.url === '/api/reports/monitor/work').flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    assert.equal(status, 403);

    const page = createPage();
    page.ngOnInit();
    http.expectOne('/api/reports/monitor/departments').flush([{id:4,code:'A',name:'Phòng A'}]);
    http.expectOne('/api/reports/employees').flush({ byDepartment: [], byPosition: [], byStatus: [], byQualification: [] });
    const movements = http.match(req => req.url === '/api/reports/monitor/movements');
    assert.equal(movements.length, 2);
    movements[0].flush([{month:'2026-11',hired:3,terminated:1}]);
    movements[1].flush([{month:'2026-10',hired:1,terminated:2}]);
    assert.equal(page.hired, 3);
    assert.equal(page.previousTerminated, 2);
    assert.equal(page.employeeGroups.length, 4);
    page.filters.fromDate = '2026-09-01';
    page.filters.toDate = '2026-09-30';
    page.select('contracts');
    const contract = http.expectOne(req => req.url === '/api/reports/contracts');
    const fromDate = contract.request.params.get('fromDate');
    const toDate = contract.request.params.get('toDate');
    contract.flush({ fromDate, toDate, reportDate: '2026-10-30', departmentId: null, expiringWithinDays: 30, contractCount: 2, effectiveCount: 1, expiringCount: 0 });
    const previousContract = http.expectOne(req => req.url === '/api/reports/contracts');
    assert.equal(previousContract.request.params.get('fromDate'), '2026-08-01');
    assert.equal(previousContract.request.params.get('toDate'), '2026-08-31');
    previousContract.flush({ fromDate:'2026-09-01',toDate:'2026-09-30',reportDate:'2026-09-30',contractCount:1,effectiveCount:1,expiringCount:0 });
    const details = http.expectOne(req => req.url === '/api/reports/monitor/contracts' && req.params.get('kind') === 'expiring');
    assert.equal(details.request.params.get('kind'), 'expiring');
    details.flush({items:[],page:0,totalPages:0,totalElements:0});
    const expiredDetails = http.expectOne(req => req.url === '/api/reports/monitor/contracts' && req.params.get('kind') === 'expired');
    expiredDetails.flush({items:[],page:0,totalPages:0,totalElements:3});
    assert.equal(page.expiredTotal, 3);
    page.filters.fromDate = '2026-01-01';
    page.export();
    const reportExport = http.expectOne(req => req.url === '/api/reports/contracts/export');
    assert.equal(reportExport.request.params.get('fromDate'), fromDate);
    assert.equal(reportExport.request.params.get('reportDate'), '2026-10-30');
    reportExport.flush(new Blob(), { status: 403, statusText: 'Forbidden' });
    assert.match(page.exportError, /REPORT_VIEW/);
    page.select('work');
    const cancelledWork = http.expectOne(req => req.url === '/api/reports/work');
    page.select('payroll');
    assert.equal(cancelledWork.cancelled, true);
    const payroll = http.expectOne(req => req.url === '/api/reports/payroll');
    payroll.flush('{"fromDate":"2026-01-01","toDate":"2026-10-30","batchCount":0,"payrollRecordCount":0,"totals":{"grossPayroll":9007199254740993.1234,"allowances":0,"bonuses":0,"deductions":0,"netPayroll":9007199254740993.1234},"batches":[]}');
    const priorPayroll = http.expectOne(req => req.url === '/api/reports/payroll');
    priorPayroll.flush('{"fromDate":"2025-01-01","toDate":"2025-12-31","batchCount":0,"payrollRecordCount":0,"totals":{"grossPayroll":1,"allowances":0,"bonuses":0,"deductions":0,"netPayroll":1},"batches":[]}');
    assert.equal(page.money(page.payrollReport.totals.netPayroll), '9.007.199.254.740.993,1234');
    assert.equal(page.workReport, null);
    http.verify();
    injector.destroy();
  } finally {
    fs.unlinkSync(output);
  }
});

test('automatic refresh respects unapplied filters and only runs for a visible tab', async () => {
  const output = path.join(__dirname, `.report-refresh-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'report-service-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  const oldDocument = global.document;
  let injector;
  try {
    global.document = { hidden: false, activeElement: null };
    const { setup } = await import(pathToFileURL(output).href);
    const context = setup();
    ({ injector } = context);
    const { http, page: createPage } = context;
    const page = createPage();
    page.ngOnInit();
    http.expectOne('/api/reports/monitor/departments').flush([]);
    http.expectOne('/api/reports/employees').flush({byDepartment:[],byPosition:[],byStatus:[],byQualification:[]});
    http.match(req=>req.url==='/api/reports/monitor/movements').forEach(req=>req.flush([]));
    const oldFrom = page.filters.fromDate;
    page.filters.fromDate = '2026-01-01';
    page.refreshIfIdle();
    http.expectNone('/api/reports/employees');
    page.filters.fromDate = oldFrom;
    document.hidden = true;
    page.refreshIfIdle();
    http.expectNone('/api/reports/employees');
    document.hidden = false;
    page.refreshIfIdle();
    http.expectOne('/api/reports/employees').flush({byDepartment:[],byPosition:[],byStatus:[],byQualification:[]});
    const movements = http.match(req=>req.url==='/api/reports/monitor/movements');
    assert.equal(movements.length,2);
    movements.forEach(req=>req.flush([]));
    http.verify();
  } finally {
    injector?.destroy();
    if (oldDocument === undefined) delete global.document; else global.document=oldDocument;
    fs.unlinkSync(output);
  }
});

test('payroll matrix groups API batches without rounding money or inventing missing values', async () => {
  const output = path.join(__dirname, `.report-matrix-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'report-service-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  let injector;
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const context = setup();
    ({ injector } = context);
    const { http } = context;
    const page = context.page();
    const batch = (id, start, end, amount) => ({ periodId:id, periodCode:`P-${id}`, periodStart:start,
      periodEnd:end, batchId:id, versionNo:2, employeeCount:1,
      totals:{grossPayroll:amount,allowances:'0',bonuses:'0',deductions:'0',netPayroll:amount} });
    const batches = [batch(1,'2026-01-01','2026-01-31','9007199254740993.1000'),
      batch(2,'2026-02-01','2026-02-28','0.2000'), batch(3,'2025-12-01','2025-12-31','-1.0000')];
    const report = {fromDate:'2025-12-01',toDate:'2026-02-28',batchCount:3,payrollRecordCount:3,
      totals:{grossPayroll:'9007199254740992.3000',allowances:'0',bonuses:'0',deductions:'0',netPayroll:'9007199254740992.3000'},batches};
    page.filters.fromDate=report.fromDate;page.filters.toDate=report.toDate;
    page.select('payroll');
    const flushReport = () => {
      http.expectOne(req=>req.url==='/api/reports/payroll').flush(JSON.stringify(report));
      http.expectOne(req=>req.url==='/api/reports/payroll').flush(JSON.stringify({...report,batches:[],batchCount:0}));
    };
    flushReport();
    assert.deepEqual(page.payrollColumns.map(column=>column.label),['Quý 4/2025','Quý 1/2026']);
    const net = page.payrollRows.find(row=>row.key==='netPayroll');
    assert.equal(net.cells[1].value,'9007199254740993.3000');
    assert.equal(net.total,'9007199254740992.3000');
    assert.deepEqual(page.payrollReport.batches.map(item=>item.batchId),[1,2,3]);
    assert.equal(page.payrollReport.batches[0].periodEnd,'2026-01-31');
    page.selectPayrollCell(net,page.payrollColumns[1],net.cells[1]);
    assert.deepEqual(page.payrollDetailBatches.map(item=>item.batchId),[1,2]);
    page.load(true);flushReport();
    assert.deepEqual(page.payrollDetailBatches.map(item=>item.batchId),[1,2]);
    page.setPayrollGrouping('periods');
    assert.equal(page.payrollSelection,null);
    assert.equal(page.payrollRows.find(row=>row.key==='batch-1').cells[0].value,null);
    assert.equal(page.payrollMatrixTotal,'9007199254740992.3000');
    page.setPayrollGranularity('month');
    assert.deepEqual(page.payrollColumns.map(column=>column.label),['12/2025','01/2026','02/2026']);
    page.setPayrollMetric('allowances');
    assert.equal(page.payrollRows.find(row=>row.key==='batch-1').cells[1].value,'0');
    assert.equal(page.payrollMatrixTotal,'0');
    page.setPayrollGranularity('year');
    assert.deepEqual(page.payrollColumns.map(column=>column.label),['2025','2026']);
    page.setPayrollGranularity('invalid');
    assert.equal(page.payrollGranularity,'year');
    page.select('employees');
    http.expectOne('/api/reports/employees').flush({byDepartment:[],byPosition:[],byStatus:[],byQualification:[]});
    http.match(req=>req.url==='/api/reports/monitor/movements').forEach(req=>req.flush([]));
    assert.equal(page.payrollRows.length,0);
    assert.equal(page.payrollSelection,null);
    http.verify();
  } finally {
    injector?.destroy();
    fs.unlinkSync(output);
  }
});

test('employee drilldowns use applied groups, paginate, cancel stale requests and isolate comparison failures', async () => {
  const output = path.join(__dirname, `.report-employees-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'report-service-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  let injector;
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const context = setup(); ({ injector } = context);
    const { http } = context;
    const page = context.page();
    page.filters = {fromDate:'2026-09-01',toDate:'2026-09-30',reportDate:'',expiringWithinDays:30,departmentId:'4'};
    page.ngOnInit();
    http.expectOne('/api/reports/monitor/departments').flush([{id:4,code:'A',name:'Phòng A'}]);
    const group = {groupId:null,groupCode:'CHUA_PHAN_CONG',groupName:'Chưa có chức vụ',employeeCount:11};
    http.expectOne(req=>req.url==='/api/reports/monitor/employees').flush({byDepartment:[],byPosition:[group],byStatus:[],byQualification:[]});
    http.expectOne(req=>req.url==='/api/reports/monitor/movements' && req.params.get('fromDate')==='2026-09-01').flush([{month:'2026-09',hired:3,terminated:1}]);
    http.expectOne(req=>req.url==='/api/reports/monitor/movements' && req.params.get('fromDate')==='2026-08-01').flush({}, {status:500,statusText:'Error'});
    assert.equal(page.hired,3);
    assert.equal(page.movementError,'');
    assert.match(page.comparisonError,/kỳ trước/);
    page.filters.departmentId='9'; // Draft filters must not change the group being inspected.
    page.openEmployeeDetails('position',group);
    assert.equal(page.employeeDetailFocus,null,'Do not scroll while only the loading placeholder is rendered.');
    const request = http.expectOne(req=>req.url==='/api/reports/monitor/employees/details');
    assert.equal(request.request.params.get('departmentId'),'4');
    assert.equal(request.request.params.get('groupBy'),'position');
    assert.equal(request.request.params.has('groupId'),false);
    assert.equal(request.request.params.has('fromDate'),false);
    assert.equal(request.request.headers.get('Authorization'),'Bearer test-token');
    request.flush({items:[{employeeId:1,employeeCode:'NV01',employeeName:'Nguyễn A',departmentName:'Phòng A',positionName:null,employmentStatus:'DANG_LAM',hireDate:'2020-01-01'}],page:0,size:10,totalElements:11,totalPages:2});
    assert.equal(page.employeeDetails.items[0].positionName,null);
    assert.equal(page.employeeDetailFocus?.label,'Chưa có chức vụ');
    page.loadEmployeeDetails('position',group,1);
    assert.equal(page.employeeDetailFocus,null);
    http.expectOne(req=>req.url==='/api/reports/monitor/employees/details' && req.params.get('page')==='1')
      .flush({items:[],page:1,size:10,totalElements:1,totalPages:1});
    http.expectOne(req=>req.url==='/api/reports/monitor/employees/details' && req.params.get('page')==='0')
      .flush({}, {status:403,statusText:'Forbidden'});
    assert.match(page.employeeDetailError,/REPORT_VIEW/);
    page.loadEmployeeDetails('position',group);
    const pending = http.expectOne(req=>req.url==='/api/reports/monitor/employees/details');
    page.closeEmployeeDetails();
    assert.equal(pending.cancelled,true);
    assert.equal(page.employeeSelection,null);
    assert.equal(page.employeeDetailFocus,null,'Closing the panel must cancel its pending focus.');
    http.verify();
  } finally { injector?.destroy(); fs.unlinkSync(output); }
});

test('payroll drilldowns retain batch identity, exact amounts and applied filters, and cancel on tab changes', async () => {
  const output = path.join(__dirname, `.report-payroll-details-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'report-service-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  let injector;
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const context = setup(); ({ injector } = context);
    const { http } = context;
    const page = context.page();
    page.filters = {fromDate:'2026-11-01',toDate:'2026-11-30',reportDate:'',expiringWithinDays:30,departmentId:'4'};
    page.select('payroll');
    const totals={grossPayroll:'9007199254740993.1234',allowances:'0',bonuses:'0',deductions:'0',netPayroll:'9007199254740993.1234'};
    const batch={periodId:1,periodCode:'P11',periodStart:'2026-11-01',periodEnd:'2026-11-30',batchId:11,versionNo:2,employeeCount:1,totals};
    http.expectOne(req=>req.url==='/api/reports/monitor/payroll').flush(JSON.stringify({fromDate:'2026-11-01',toDate:'2026-11-30',batchCount:1,payrollRecordCount:1,totals,batches:[batch]}));
    http.expectOne(req=>req.url==='/api/reports/monitor/payroll').flush(JSON.stringify({totals,batches:[]}));
    page.filters.departmentId='9';
    page.loadPayrollEmployees(batch);
    const employees = http.expectOne(req=>req.url==='/api/reports/monitor/payroll/11/employees');
    assert.equal(employees.request.params.get('departmentId'),'4');
    assert.equal(employees.request.params.get('fromDate'),'2026-11-01');
    assert.equal(employees.request.params.get('toDate'),'2026-11-30');
    assert.equal(employees.request.responseType,'text');
    employees.flush('{"items":[{"payrollDetailId":111,"employeeId":1,"employeeCode":"NV01","employeeName":"Nguyễn A","totals":{"grossPayroll":9007199254740993.1234,"allowances":0,"bonuses":0,"deductions":0,"netPayroll":9007199254740993.1234}}],"page":0,"size":10,"totalElements":1,"totalPages":1}');
    const employee=page.payrollEmployees.items[0];
    assert.equal(employee.totals.netPayroll,'9007199254740993.1234');
    assert.equal(page.activePayrollBatch.versionNo,2);
    assert.equal(page.activePayrollBatch.periodEnd,'2026-11-30');
    page.loadPayrollLines(employee);
    const lines = http.expectOne(req=>req.url==='/api/reports/monitor/payroll/11/employees/111/lines');
    assert.equal(lines.request.params.get('departmentId'),'4');
    assert.equal(lines.request.headers.get('Authorization'),'Bearer test-token');
    lines.flush('[{"lineId":2,"componentCode":"LUONG","componentName":"Lương đã lưu","direction":"ADD","amount":9007199254740993.1234,"explanation":null}]');
    assert.equal(page.payrollLines[0].amount,'9007199254740993.1234');
    assert.equal(page.money(page.payrollLines[0].amount),'9.007.199.254.740.993,1234');
    page.loadPayrollLines(employee);
    http.expectOne(req=>req.url.endsWith('/111/lines')).flush({}, {status:403,statusText:'Forbidden'});
    assert.match(page.payrollLineError,/PAYROLL_VIEW/);
    assert.equal(page.payrollLines.length,0);
    page.loadPayrollLines(employee);
    const stale = http.expectOne(req=>req.url.endsWith('/111/lines'));
    page.loadPayrollEmployees(batch,1);
    assert.equal(stale.cancelled,true);
    const staleEmployees = http.expectOne(req=>req.url.endsWith('/11/employees'));
    page.select('employees');
    assert.equal(staleEmployees.cancelled,true);
    assert.equal(page.activePayrollBatch,null);
    assert.equal(page.activePayrollEmployee,null);
    http.expectOne(req=>req.url==='/api/reports/monitor/employees').flush({byDepartment:[],byPosition:[],byStatus:[],byQualification:[]});
    http.match(req=>req.url==='/api/reports/monitor/movements').forEach(req=>req.flush([]));
    http.verify();
  } finally { injector?.destroy(); fs.unlinkSync(output); }
});

test('work reports clip monthly API requests to applied dates and surface JSON Excel validation errors', async () => {
  const output = path.join(__dirname, `.report-work-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'report-service-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  let injector;
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const context = setup(); ({ injector } = context);
    const { http } = context;
    const page = context.page();
    page.filters={fromDate:'2026-08-31',toDate:'2026-10-02',reportDate:'',expiringWithinDays:30,departmentId:'4'};
    page.select('work');
    const report={fromDate:'2026-08-31',toDate:'2026-10-02',confirmedWorkDays:20,confirmedAbsentDays:1,approvedLeaveRequests:1,approvedLeaveHours:4,approvedOvertimeRequests:2,approvedOvertimeHours:3.5};
    http.expectOne(req=>req.url==='/api/reports/monitor/work').flush(report);
    const requests=http.match(req=>req.url==='/api/reports/monitor/work');
    const monthly=requests.filter(req=>req.request.params.get('fromDate')>='2026-08-31');
    assert.deepEqual(monthly.map(req=>[req.request.params.get('fromDate'),req.request.params.get('toDate')]),[['2026-08-31','2026-08-31'],['2026-09-01','2026-09-30'],['2026-10-01','2026-10-02']]);
    requests.forEach(req=>assert.equal(req.request.params.get('departmentId'),'4'));
    requests.find(req=>!monthly.includes(req)).flush({}, {status:500,statusText:'Error'});
    monthly.reverse().forEach(req=>req.flush({...report,fromDate:req.request.params.get('fromDate'),toDate:req.request.params.get('toDate')}));
    assert.deepEqual(page.workMonths.map(row=>row.label),['2026-08','2026-09','2026-10']);
    assert.equal(page.workReport.approvedOvertimeHours,3.5);
    assert.equal(page.workTrendError,'');
    assert.match(page.comparisonError,/kỳ trước/);
    page.filters.fromDate='2026-01-01';
    page.export();
    const exported=http.expectOne(req=>req.url==='/api/reports/monitor/work/export');
    assert.equal(exported.request.params.get('fromDate'),'2026-08-31');
    exported.flush(new Blob([JSON.stringify({loi_chi_tiet:'Khoảng ngày không hợp lệ từ API'})],{type:'application/json'}),{status:400,statusText:'Bad Request'});
    await new Promise(resolve=>setImmediate(resolve));
    assert.equal(page.exportError,'Khoảng ngày không hợp lệ từ API');
    assert.equal(page.exporting,false);
    http.verify();
  } finally { injector?.destroy(); fs.unlinkSync(output); }
});
