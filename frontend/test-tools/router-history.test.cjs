const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

// Keep the bundle beside node_modules so Angular's external imports resolve.
const output = path.join(__dirname, `.router-history-${process.pid}.mjs`);
const built = esbuild.build({ entryPoints: [path.join(__dirname, 'router-history-harness.ts')], bundle: true,
  packages: 'external', platform: 'node', format: 'esm', outfile: output });
test.after(async () => { await built; fs.rmSync(output, { force: true }); });
const settle = () => new Promise(resolve => setTimeout(resolve, 40));

async function setup() {
  await built;
  const { setupRouter } = await import(pathToFileURL(output).href);
  const context = setupRouter();
  context.router.setUpLocationChangeListener();
  return context;
}

test('login replaces only the login entry and preserves the page visited before the site', async () => {
  const { injector, router, location, setSession } = await setup();
  try {
    await router.navigateByUrl('/before');
    await router.navigateByUrl('/login');
    setSession('token-a');
    await router.navigateByUrl('/overview', { replaceUrl: true });
    location.back(); await settle();
    assert.equal(router.url, '/before');
    location.forward(); await settle();
    assert.equal(router.url, '/overview');
    assert.equal(location.getState().hrmSessionId, undefined);
  } finally { injector.destroy(); }
});

test('back and forward work in a session and cannot access protected routes after logout', async () => {
  const { injector, router, location, setSession, profileChecks } = await setup();
  try {
    await router.navigateByUrl('/login');
    setSession('token-a');
    await router.navigateByUrl('/overview', { replaceUrl: true });
    await router.navigateByUrl('/employees');
    await router.navigateByUrl('/contracts');
    location.back(); await settle();
    assert.equal(router.url, '/employees');
    location.forward(); await settle();
    assert.equal(router.url, '/contracts');
    setSession(null);
    await router.navigateByUrl('/login', { replaceUrl: true });
    const checks = profileChecks.length;
    location.back(); await settle();
    assert.equal(router.url, '/login');
    assert.equal(location.path(), '/login');
    assert.equal(profileChecks.length, checks, 'signed-out navigation stops before loading private data');
    location.forward(); await settle();
    assert.equal(router.url, '/login');
  } finally { injector.destroy(); }
});

test('a new login visits historical URLs using the new session instead of rewriting them to overview', async () => {
  for (const direction of ['back', 'forward']) {
    const { injector, router, location, setSession, profileChecks } = await setup();
    try {
      await router.navigateByUrl('/login');
      setSession('token-a');
      await router.navigateByUrl('/overview', { replaceUrl: true });
      await router.navigateByUrl('/employees?keyword=first#list');
      await router.navigateByUrl('/contracts');
      if (direction === 'forward') { location.back(); await settle(); }
      setSession(null);
      await router.navigateByUrl('/login', { replaceUrl: true });
      setSession('token-b');
      await router.navigateByUrl('/overview', { replaceUrl: true });
      location[direction](); await settle();
      assert.equal(router.url, direction === 'back' ? '/employees?keyword=first#list' : '/contracts');
      assert.equal(profileChecks.at(-1), 'token-b', 'history never restores the old authentication');
      assert.equal(location.getState().hrmSessionId, undefined);
    } finally { injector.destroy(); }
  }
});

test('history respects the new account permissions and keeps query and fragment navigation usable', async () => {
  const { injector, router, location, setSession, profileChecks } = await setup();
  try {
    setSession('admin-token');
    await router.navigateByUrl('/accounts');
    await router.navigateByUrl('/contracts');
    setSession(null);
    await router.navigateByUrl('/login', { replaceUrl: true });
    setSession('head-token', 'EMPLOYEE');
    await router.navigateByUrl('/requests/head', { replaceUrl: true });
    location.back(); await settle();
    assert.equal(router.url, '/requests/head', 'an old admin URL cannot grant the new user access');
    await router.navigateByUrl('/employees?keyword=first#one');
    await router.navigateByUrl('/employees?keyword=second#two');
    location.back(); await settle();
    assert.equal(router.url, '/employees?keyword=first#one');
    assert.equal(profileChecks.at(-1), 'head-token');
    const checks = profileChecks.length;
    await router.navigateByUrl('/employees?keyword=first#two');
    assert.equal(profileChecks.length, checks + 1, 'fragment-only navigation also checks the session');
    setSession(null);
    location.back(); await settle();
    assert.equal(router.url, '/login');
  } finally { injector.destroy(); }
});

test('an active session is redirected from an old login URL without creating another history entry', async () => {
  const { injector, router, location, setSession } = await setup();
  try {
    await router.navigateByUrl('/before');
    await router.navigateByUrl('/login');
    setSession('token-a');
    await router.navigateByUrl('/employees');
    location.back(); await settle();
    assert.equal(router.url, '/overview');
    location.back(); await settle();
    assert.equal(router.url, '/before');
  } finally { injector.destroy(); }
});
