const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('Angular redirects an active session away from an old login history entry', async () => {
  const output = path.join(__dirname, `.router-history-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'router-history-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  let injector;
  try {
    const { setupRouter } = await import(pathToFileURL(output).href);
    const context = setupRouter();
    ({ injector } = context);
    const { router, location, setToken } = context;
    await router.navigateByUrl('/login');
    setToken('valid');
    await router.navigateByUrl('/overview');
    router.setUpLocationChangeListener();
    location.back();
    await new Promise(resolve => setTimeout(resolve, 30));
    assert.equal(router.url, '/overview');
    assert.equal(location.path(), '/overview');
    setToken(null);
    await router.navigateByUrl('/login');
    assert.equal(router.url, '/login');
  } finally {
    injector?.destroy();
    fs.unlinkSync(output);
  }
});
