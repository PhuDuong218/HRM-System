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
  global.localStorage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
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
      http.expectOne('/api/auth/web/login').flush({ accessToken: jwt(sequence), tokenType: 'Bearer', expiresInSeconds: seconds });
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
    data.set('hrm.session', JSON.stringify({ accessToken: jwt(5), username: 'admin', expiresAt: Date.now() - 1000, lastActivityAt: Date.now() - 1000 }));
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
    delete global.localStorage;
    fs.unlinkSync(output);
  }
});

test('activity renews the web token and ten idle minutes end the session', async () => {
  const output = path.join(__dirname, `.session-idle-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'logout-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  const data = new Map();
  global.localStorage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  global.document = new EventTarget();
  const originalNow = Date.now;
  let now = originalNow();
  Date.now = () => now;
  let injector;
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const context = setup();
    ({ injector } = context);
    const { auth, http } = context;
    const jwt = sequence => `header.${Buffer.from(JSON.stringify({ role: 'ADMIN', sequence })).toString('base64url')}.signature`;
    auth.login('admin', 'password').subscribe();
    http.expectOne('/api/auth/web/login').flush({ accessToken: jwt(1), tokenType: 'Bearer', expiresInSeconds: 600 });
    http.expectOne('/api/auth/me').flush({role:'ADMIN',fullName:'Admin'});
    http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:false});

    now += 31_000;
    document.dispatchEvent(new Event('keydown'));
    await new Promise(resolve => setTimeout(resolve, 10));
    const firstRefresh = http.expectOne('/api/auth/web/refresh');
    assert.equal(firstRefresh.request.headers.get('Authorization'), `Bearer ${jwt(1)}`);
    firstRefresh.flush({ accessToken: jwt(2), tokenType: 'Bearer', expiresInSeconds: 600 });
    assert.equal(auth.token(), jwt(2));
    assert.equal(auth.username(), 'admin', 'refresh preserves the current account');

    now += 9 * 60_000;
    document.dispatchEvent(new Event('pointerdown'));
    await new Promise(resolve => setTimeout(resolve, 10));
    http.expectOne('/api/auth/web/refresh').flush({ accessToken: jwt(3), tokenType: 'Bearer', expiresInSeconds: 600 });
    assert.equal(auth.token(), jwt(3));

    now += 10 * 60_000 + 1;
    document.dispatchEvent(new Event('visibilitychange'));
    assert.equal(auth.token(), null);
    assert.equal(data.has('hrm.session'), false);
    assert.match(auth.sessionNotice(), /hết hạn/);
    http.verify();
  } finally {
    Date.now = originalNow;
    injector?.destroy();
    delete global.document;
    delete global.localStorage;
    fs.unlinkSync(output);
  }
});

test('configured short session duration drives both refresh and idle expiration', async () => {
  const output = path.join(__dirname, `.session-short-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'logout-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  const data = new Map();
  global.localStorage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  global.document = new EventTarget();
  const originalNow = Date.now;
  let now = originalNow();
  Date.now = () => now;
  let injector;
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const context = setup();
    ({ injector } = context);
    const { auth, http } = context;
    const jwt = sequence => `header.${Buffer.from(JSON.stringify({ role: 'ADMIN', sequence })).toString('base64url')}.signature`;
    auth.login('admin', 'password').subscribe();
    http.expectOne('/api/auth/web/login').flush({ accessToken: jwt(1), tokenType: 'Bearer', expiresInSeconds: 30 });
    http.expectOne('/api/auth/me').flush({role:'ADMIN',fullName:'Admin'});
    http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:false});
    now += 8000;
    document.dispatchEvent(new Event('keydown'));
    await new Promise(resolve => setTimeout(resolve, 10));
    http.expectOne('/api/auth/web/refresh').flush({ accessToken: jwt(2), tokenType: 'Bearer', expiresInSeconds: 30 });
    now += 29_000;
    assert.equal(auth.token(), jwt(2));
    now += 1001;
    document.dispatchEvent(new Event('visibilitychange'));
    assert.equal(auth.token(), null);
    assert.match(auth.sessionNotice(), /không hoạt động/);
    http.verify();
  } finally {
    Date.now = originalNow;
    injector?.destroy();
    delete global.document;
    delete global.localStorage;
    fs.unlinkSync(output);
  }
});
