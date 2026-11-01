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
    const employees = http.expectOne('/api/reports/employees');
    assert.equal(employees.request.headers.get('Authorization'), 'Bearer test-token');
    employees.flush({ byDepartment: [], byPosition: [], byStatus: [], byQualification: [] });
    assert.deepEqual(employee.byDepartment, []);

    for (const type of ['contracts', 'work', 'payroll']) {
      let result;
      service.get(type, filters).subscribe(value => result = value);
      const request = http.expectOne(req => req.url === `/api/reports/${type}`);
      assert.equal(request.request.params.get('fromDate'), '2026-09-01');
      assert.equal(request.request.params.get('toDate'), '2026-09-30');
      if (type === 'contracts') {
        assert.equal(request.request.params.get('departmentId'), '4');
        assert.equal(request.request.params.get('reportDate'), '2026-09-30');
        assert.equal(request.request.params.get('expiringWithinDays'), '15');
      } else assert.equal(request.request.params.has('departmentId'), false);
      if (type === 'payroll') request.flush('{"totals":{"grossPayroll":9007199254740993.1234}}');
      else request.flush({ ok: true });
      if (type === 'payroll') assert.equal(result.totals.grossPayroll, '9007199254740993.1234');

      let downloaded;
      service.export(type, filters).subscribe(value => downloaded = value);
      const exported = http.expectOne(req => req.url === `/api/reports/${type}/export`);
      assert.equal(exported.request.headers.get('Authorization'), 'Bearer test-token');
      assert.equal(exported.request.params.toString(), request.request.params.toString());
      const blob = new Blob(['excel'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      exported.flush(blob);
      assert.equal(downloaded.size, 5);
    }
    service.export('employees', filters).subscribe();
    const employeeExport = http.expectOne('/api/reports/employees/export');
    assert.equal(employeeExport.request.params.keys().length, 0);
    employeeExport.flush(new Blob(['excel']));
    let status;
    service.get('work', filters).subscribe({ error: error => status = error.status });
    http.expectOne(req => req.url === '/api/reports/work').flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    assert.equal(status, 403);

    const page = createPage();
    page.ngOnInit();
    http.expectOne('/api/reports/employees').flush({ byDepartment: [], byPosition: [], byStatus: [], byQualification: [] });
    assert.equal(page.employeeGroups.length, 4);
    page.select('contracts');
    const contract = http.expectOne(req => req.url === '/api/reports/contracts');
    const fromDate = contract.request.params.get('fromDate');
    const toDate = contract.request.params.get('toDate');
    contract.flush({ fromDate, toDate, reportDate: '2026-10-30', departmentId: null, expiringWithinDays: 30, contractCount: 2, effectiveCount: 1, expiringCount: 0 });
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
    assert.equal(page.money(page.payrollReport.totals.netPayroll), '9.007.199.254.740.993,1234');
    assert.equal(page.workReport, null);
    http.verify();
    injector.destroy();
  } finally {
    fs.unlinkSync(output);
  }
});
