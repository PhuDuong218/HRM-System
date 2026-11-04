const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('overview respects HR scope and displays independent report results for admins', async () => {
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
    assert.equal(hr.page.employeeState, 'error');
    assert.match(hr.page.employeeError, /HR/);
    hr.http.expectNone(request => request.url.startsWith('/api/reports/'));
    hr.http.verify();
    hr.injector.destroy();

    const admin = setup();
    admin.page.ngOnInit();
    admin.profile.next({ role: 'ADMIN' });
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
