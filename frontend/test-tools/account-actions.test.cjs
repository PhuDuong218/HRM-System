const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('password reset and status change send validated requests and refresh state', async () => {
  const output = path.join(__dirname, `.account-actions-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'account-create-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const { page, http, injector, visits } = setup();
    const account = { accountId: 3, username: 'employee', active: true, roleId: 3, roleCode: 'EMPLOYEE' };
    page.openDialog('account-view', account);
    http.expectOne('/api/admin/accounts/3/contact').flush({ email: null, phone: null });
    page.showDetailAction('account-reset');
    page.resetPassword = 'Password123'; page.resetConfirm = 'wrong';
    page.submitPasswordReset();
    assert.match(page.actionError, /không khớp/);
    http.expectNone('/api/admin/accounts/3/reset-password');
    page.resetConfirm = 'Password123';
    page.submitPasswordReset(); page.submitPasswordReset();
    const reset = http.expectOne('/api/admin/accounts/3/reset-password');
    assert.equal(reset.request.method, 'POST');
    reset.flush(null, { status: 204, statusText: 'No Content' });
    assert.equal(page.resetPassword, '');
    assert.equal(page.dialogMode, 'account-view');
    http.expectOne('/api/admin/accounts/3').flush(account);
    http.expectOne(req => req.method === 'GET' && req.url === '/api/admin/accounts').flush({ items: [], page: 0, totalPages: 0, totalElements: 0 });
    page.showDetailAction('account-lock');
    page.submitStatusChange(); page.submitStatusChange();
    const lock = http.expectOne('/api/admin/accounts/3/status');
    assert.deepEqual(lock.request.body, { expectedActive: true, active: false });
    lock.flush({ ...account, active: false });
    assert.equal(page.selectedAccount.active, false);
    assert.match(page.actionMessage, /Đã khóa/);
    http.expectOne(req => req.method === 'GET' && req.url === '/api/admin/accounts').flush({ items: [], page: 0, totalPages: 0, totalElements: 0 });
    page.showDetailAction('account-lock');
    page.submitStatusChange();
    http.expectOne('/api/admin/accounts/3/status').flush({ loi_chi_tiet: 'Trạng thái đã thay đổi' }, { status: 409, statusText: 'Conflict' });
    assert.match(page.actionError, /đã thay đổi/);
    assert.equal(page.saving, false);
    page.backToDetail();
    page.showDetailAction('account-edit');
    page.editUsername = 'new.employee';
    page.editRoleId = 2;
    page.editHrDepartments();
    http.expectOne('/api/admin/hr-scopes/account/3/department-options').flush([
      { departmentId: 4, departmentName: 'Đào tạo', active: true, assigned: false }
    ]);
    page.toggleScopeDepartment(4, true);
    page.saveAccountIdentity();
    const edit = http.expectOne('/api/admin/accounts/3/identity');
    assert.deepEqual(edit.request.body, { expectedUsername: 'employee', expectedRoleId: 3, username: 'new.employee', roleId: 2,
      expectedDepartmentIds: [], departmentIds: [4] });
    edit.flush({ ...account, username: 'new.employee', roleId: 2, roleCode: 'HR', active: false });
    assert.equal(page.selectedAccount.username, 'new.employee');
    assert.equal(page.dialogMode, 'account-view');
    http.expectOne('/api/admin/hr-scopes/account/3/active-departments').flush([{ departmentId: 4, departmentName: 'Đào tạo' }]);
    http.expectOne(req => req.method === 'GET' && req.url === '/api/admin/accounts').flush({ items: [], page: 0, totalPages: 0, totalElements: 0 });
    assert.equal(page.selectedAccount.active, false);
    assert.equal(page.hrDepartments[0].departmentName, 'Đào tạo');
    const self = { accountId: 1, username: 'admin', active: true, roleId: 1, roleCode: 'ADMIN' };
    page.roles.push({ roleId: 1, roleCode: 'ADMIN', roleName: 'Quản trị', permissions: [] });
    page.openDialog('account-view', self);
    http.expectOne('/api/admin/accounts/1/contact').flush({ email: null, phone: null });
    page.showDetailAction('account-edit');
    page.editUsername = 'admin.new';
    page.saveAccountIdentity();
    http.expectOne('/api/admin/accounts/1/identity').flush({ ...self, username: 'admin.new' });
    assert.deepEqual(visits, ['cleared', '/login']);
    http.verify(); injector.destroy();
  } finally { fs.unlinkSync(output); }
});
