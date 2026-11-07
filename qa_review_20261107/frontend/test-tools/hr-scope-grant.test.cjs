const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('administrator selects departments for an HR account and cannot save an empty scope', async () => {
  const output = path.join(__dirname, `.hr-scope-grant-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'account-create-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const { page, http, injector } = setup();
    page.openDialog('account-view', { accountId: 8, username: 'hr', active: true, roleId: 2, roleCode: 'HR' });
    http.expectOne('/api/admin/accounts/8/contact').flush({ email: null, phone: null });
    http.expectOne('/api/admin/hr-scopes/account/8/active-departments').flush([{ departmentId: 3, departmentName: 'Giáo vụ' }]);
    page.showDetailAction('account-edit');
    page.editHrDepartments();
    http.expectOne('/api/admin/hr-scopes/account/8/department-options').flush([
      { departmentId: 2, departmentName: 'Đào tạo', active: true, assigned: false },
      { departmentId: 3, departmentName: 'Giáo vụ', active: true, assigned: true }
    ]);
    page.toggleScopeDepartment(3, false);
    page.saveAccountIdentity();
    assert.match(page.actionError, /ít nhất một/);
    page.toggleScopeDepartment(2, true);
    page.saveAccountIdentity();
    page.saveAccountIdentity();
    const request = http.expectOne('/api/admin/accounts/8/identity');
    assert.equal(request.request.method, 'PATCH');
    assert.deepEqual(request.request.body.expectedDepartmentIds, [3]);
    assert.deepEqual(request.request.body.departmentIds, [2]);
    request.flush({ accountId: 8, username: 'hr', active: true, roleId: 2, roleCode: 'HR' });
    http.expectOne('/api/admin/hr-scopes/account/8/active-departments').flush([{ departmentId: 2, departmentName: 'Đào tạo' }]);
    http.expectOne(req => req.method === 'GET' && req.url === '/api/admin/accounts').flush({ items: [], page: 0, totalPages: 0, totalElements: 0 });
    assert.equal(page.hrDepartments[0].departmentName, 'Đào tạo');
    assert.equal(page.saving, false);
    assert.match(page.actionMessage, /phạm vi phòng ban/);
    http.verify(); injector.destroy();
  } finally { fs.unlinkSync(output); }
});

test('changing an employee to HR can be saved without assigning departments yet', async () => {
  const output = path.join(__dirname, `.hr-role-optional-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'account-create-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const { page, http, injector } = setup();
    page.roles.push({ roleId: 3, roleCode: 'EMPLOYEE', roleName: 'Nhân viên', permissions: [] });
    const account = { accountId: 9, username: 'employee', active: true, roleId: 3, roleCode: 'EMPLOYEE' };
    page.openDialog('account-view', account);
    http.expectOne('/api/admin/accounts/9/contact').flush({ email: null, phone: null });
    page.showDetailAction('account-edit');
    page.editRoleId = 2;
    assert.equal(page.editRoleIsHr, true);
    page.saveAccountIdentity();
    const request = http.expectOne('/api/admin/accounts/9/identity');
    assert.equal(Object.hasOwn(request.request.body, 'departmentIds'), false);
    request.flush({ ...account, roleId: 2, roleCode: 'HR' });
    http.expectOne('/api/admin/hr-scopes/account/9/active-departments').flush([]);
    http.expectOne(req => req.method === 'GET' && req.url === '/api/admin/accounts').flush({ items: [], page: 0, totalPages: 0, totalElements: 0 });
    assert.equal(page.dialogMode, 'account-view');
    assert.deepEqual(page.hrDepartments, []);
    http.verify(); injector.destroy();
  } finally { fs.unlinkSync(output); }
});
