const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('status and role filters apply immediately without submitting a new keyword', async () => {
  const output = path.join(__dirname, `.account-filter-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'account-create-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const { page, http, injector } = setup();
    const result = { items: [], page: 0, totalPages: 0, totalElements: 0 };

    page.filterSelectionChanged('true', '');
    const status = http.expectOne(req => req.url === '/api/admin/accounts');
    assert.equal(status.request.params.get('active'), 'true');
    assert.equal(status.request.params.get('keyword'), null);
    assert.equal(status.request.params.get('page'), '0');

    page.filterSelectionChanged('true', '2');
    assert.equal(status.cancelled, true);
    const role = http.expectOne(req => req.url === '/api/admin/accounts');
    assert.equal(role.request.params.get('active'), 'true');
    assert.equal(role.request.params.get('roleId'), '2');
    assert.equal(role.request.params.get('keyword'), null);
    role.flush(result);

    page.search({ preventDefault() {} }, '  nhân viên  ', 'true', '2');
    const searched = http.expectOne(req => req.url === '/api/admin/accounts');
    assert.equal(searched.request.params.get('keyword'), 'nhân viên');
    searched.flush(result);

    // Changing a dropdown keeps the last submitted keyword, not the unsubmitted text.
    page.filterSelectionChanged('false', '2');
    const changed = http.expectOne(req => req.url === '/api/admin/accounts');
    assert.equal(changed.request.params.get('keyword'), 'nhân viên');
    assert.equal(changed.request.params.get('active'), 'false');
    changed.flush(result);

    page.reset({ reset() {} });
    const reset = http.expectOne(req => req.url === '/api/admin/accounts');
    assert.equal(reset.request.params.get('keyword'), null);
    assert.equal(reset.request.params.get('active'), null);
    assert.equal(reset.request.params.get('roleId'), null);
    reset.flush(result);
    http.verify();
    injector.destroy();
  } finally { fs.unlinkSync(output); }
});
