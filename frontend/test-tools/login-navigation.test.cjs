const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('selected destination is used only after successful login', async () => {
  const output = path.join(__dirname, `.login-navigation-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'login-navigation-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const chosen = setup();
    chosen.page.selectDestination('contracts', 'Hợp đồng');
    chosen.page.submit({ preventDefault() {} }, 'hr', 'password');
    assert.deepEqual(chosen.visits, []);
    chosen.login.next({});
    assert.deepEqual(chosen.visits, ['/contracts']);
    chosen.injector.destroy();
    const standard = setup();
    standard.page.submit({ preventDefault() {} }, 'hr', 'password');
    standard.login.next({});
    assert.deepEqual(standard.visits, ['/overview']);
    standard.injector.destroy();
    const failed = setup();
    failed.page.selectDestination('employees', 'Nhân viên');
    failed.page.submit({ preventDefault() {} }, 'hr', 'wrong');
    failed.login.error(new Error('failure'));
    assert.deepEqual(failed.visits, []);
    failed.injector.destroy();
  } finally { fs.unlinkSync(output); }
});
