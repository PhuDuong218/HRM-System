const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('session expires without interaction, notices revoked sessions, and ignores stale responses', async () => {
  const output = path.join(__dirname, `.session-expiry-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'logout-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  const data = new Map();
  global.sessionStorage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  global.document = new EventTarget();
  let injector;
  const originalNow = Date.now;
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const context = setup();
    ({ injector } = context);
    const { auth, http, visits } = context;
    const jwt = sequence => `header.${Buffer.from(JSON.stringify({ role: 'ADMIN', sequence })).toString('base64url')}.signature`;
    const login = (sequence, seconds = 300) => {
      auth.login('admin', 'password').subscribe();
      http.expectOne('/api/auth/login').flush({ accessToken: jwt(sequence), tokenType: 'Bearer', expiresInSeconds: seconds });
      http.expectOne('/api/auth/me').flush({role:'ADMIN',fullName:'Admin'});
      http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:false});
    };

    login(1, 0.05);
    await new Promise(resolve => setTimeout(resolve, 120));
    assert.equal(auth.token(), null);
    assert.match(auth.sessionNotice(), /đã hết hạn/);
    assert.equal(data.has('hrm.session'), false);
    assert.deepEqual(visits, []);
    auth.exitExpiredSession();
    assert.equal(auth.sessionNotice(), '');
    assert.deepEqual(visits, ['/login']);

    login(2);
    assert.equal(auth.sessionNotice(), '');
    auth.loadCurrentUser().subscribe({ error() {} });
    const oldRequest = http.expectOne('/api/auth/me');
    http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:false});
    login(3);
    oldRequest.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    assert.equal(auth.token(), jwt(3));
    assert.equal(auth.sessionNotice(), '');

    auth.loadCurrentUser().subscribe({ error() {} });
    http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:false});
    http.expectOne('/api/auth/me').flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    assert.equal(auth.token(), null);
    assert.match(auth.sessionNotice(), /không còn hợp lệ/);
    assert.equal(visits.length, 1);
    auth.exitExpiredSession();
    assert.equal(visits.length, 2);

    login(4, 60);
    Date.now = () => originalNow() + 61000;
    global.document.dispatchEvent(new Event('visibilitychange'));
    assert.equal(auth.token(), null);
    assert.match(auth.sessionNotice(), /đã hết hạn/);
    assert.equal(visits.length, 2);
    http.verify();

    injector.destroy();
    Date.now = originalNow;
    data.set('hrm.session', JSON.stringify({ accessToken: jwt(5), username: 'admin', expiresAt: Date.now() - 1000 }));
    const restored = setup();
    injector = restored.injector;
    await new Promise(resolve => setTimeout(resolve, 20));
    assert.equal(restored.auth.token(), null);
    assert.match(restored.auth.sessionNotice(), /đã hết hạn/);
    assert.deepEqual(restored.visits, []);
    assert.equal(data.has('hrm.session'), false);
  } finally {
    Date.now = originalNow;
    injector?.destroy();
    delete global.document;
    delete global.sessionStorage;
    fs.unlinkSync(output);
  }
});
