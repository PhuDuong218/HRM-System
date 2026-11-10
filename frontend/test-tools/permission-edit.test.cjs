const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('permission details save without changing code and preserve role assignments', async () => {
  const output = path.join(__dirname, `.permission-edit-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'account-create-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const { page, http, injector } = setup();
    const permission = { permissionId: 5, permissionCode: 'REPORT_VIEW', permissionName: 'Báo cáo', description: null };
    page.permissions = [permission];
    page.roles[0].permissions = [permission];
    page.openDialog('permission-view', permission);
    page.showDetailAction('permission-edit');
    page.editPermissionName = '  ';
    page.savePermissionDetails();
    assert.match(page.actionError, /tên quyền/);
    http.expectNone('/api/admin/permissions/5/details');
    page.editPermissionName = 'Xem báo cáo';
    page.editPermissionDescription = 'Xem báo cáo nhân sự';
    page.savePermissionDetails(); page.savePermissionDetails();
    const request = http.expectOne('/api/admin/permissions/5/details');
    assert.equal(request.request.method, 'PATCH');
    assert.deepEqual(request.request.body, { expectedName: 'Báo cáo', expectedDescription: null,
      permissionName: 'Xem báo cáo', description: 'Xem báo cáo nhân sự' });
    const updated = { ...permission, permissionName: 'Xem báo cáo', description: 'Xem báo cáo nhân sự' };
    request.flush(updated);
    assert.equal(page.dialogMode, 'permission-view');
    assert.equal(page.selectedPermission.permissionCode, 'REPORT_VIEW');
    assert.equal(page.roles[0].permissions[0].permissionName, 'Xem báo cáo');
    assert.equal(page.permissions[0].permissionName, 'Xem báo cáo');
    page.showDetailAction('permission-edit');
    page.editPermissionName = 'Tên mới';
    page.savePermissionDetails();
    http.expectOne('/api/admin/permissions/5/details').flush({ loi_chi_tiet: 'Thông tin quyền đã thay đổi. Vui lòng tải lại.' }, { status: 409, statusText: 'Conflict' });
    assert.match(page.actionError, /đã thay đổi/);
    assert.equal(page.saving, false);
    http.verify(); injector.destroy();
  } finally { fs.unlinkSync(output); }
});
