const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

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
    hr.http.verify();
    hr.injector.destroy();

    const unassigned = setup();
    unassigned.page.ngOnInit();
    unassigned.profile.next({ role: 'HR' });
    unassigned.http.expectOne('/api/overview/hr').flush({ employees: { byDepartment: [], byStatus: [] },
      contracts: { effectiveCount: 0, expiringCount: 0 }, work: { confirmedWorkDays: 0, confirmedAbsentDays: 0 } });
    assert.equal(unassigned.page.totalEmployees, 0);
    assert.equal(unassigned.page.employeeState, 'ready');
    unassigned.http.verify();
    unassigned.injector.destroy();

    const failed = setup();
    failed.page.ngOnInit();
    failed.profile.next({ role: 'HR' });
    failed.http.expectOne('/api/overview/hr').flush('Unavailable', { status: 503, statusText: 'Unavailable' });
    assert.equal(failed.page.employeeState, 'error');
    assert.equal(failed.page.contractState, 'error');
    assert.equal(failed.page.workState, 'error');
    failed.http.verify();
    failed.injector.destroy();

    const admin = setup();
    admin.page.ngOnInit();
    admin.profile.next({ role: 'ADMIN' });
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
