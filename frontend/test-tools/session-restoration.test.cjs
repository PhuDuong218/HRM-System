const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

async function build(name) {
  const output = path.join(__dirname, `.${name}-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'logout-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  return { output, setup: (await import(pathToFileURL(output).href)).setup };
}

test('late profile responses cannot replace or clear another login or restore a logged-out profile', async () => {
  const { output, setup } = await build('session-profile');
  const data = new Map();
  global.localStorage = { getItem: key => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  let injector;
  try {
    const context = setup();
    ({ injector } = context);
    const { auth, http } = context;
    const jwt = sequence => `header.${Buffer.from(JSON.stringify({ role: 'ADMIN', sequence })).toString('base64url')}.signature`;
    const login = sequence => {
      auth.login('admin', 'password').subscribe();
      http.expectOne('/api/auth/web/login').flush({ accessToken: jwt(sequence), tokenType: 'Bearer', expiresInSeconds: 600 });
      http.expectOne('/api/auth/me').flush({ role: 'ADMIN', fullName: `Admin ${sequence}` });
      http.expectOne('/api/employee-requests/capabilities').flush({ departmentHead: false });
    };
    login(1);
    let staleError;
    auth.loadCurrentUser().subscribe({ error: error => staleError = error });
    const previous = http.expectOne('/api/auth/me');
    http.expectOne('/api/employee-requests/capabilities').flush({ departmentHead: false });
    login(2);
    previous.flush({ role: 'EMPLOYEE', fullName: 'Old account without web access' });
    assert.ok(staleError);
    assert.equal(auth.token(), jwt(2));
    assert.equal(auth.profile().fullName, 'Admin 2');

    auth.loadCurrentUser().subscribe({ error() {} });
    const pending = http.expectOne('/api/auth/me');
    http.expectOne('/api/employee-requests/capabilities').flush({ departmentHead: false });
    auth.logout().subscribe();
    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    pending.flush({ role: 'ADMIN', fullName: 'Logged-out account' });
    assert.equal(auth.token(), null);
    assert.equal(auth.profile(), null);
    assert.equal(data.has('hrm.session'), false);
    http.verify();
  } finally {
    injector?.destroy();
    delete global.localStorage;
    fs.rmSync(output, { force: true });
  }
});

test('restoring a document from browser cache reloads the current entry to revalidate authentication', async () => {
  const { output, setup } = await build('session-bfcache');
  const browserWindow = new EventTarget();
  let reloads = 0;
  browserWindow.location = { reload: () => reloads++ };
  global.window = browserWindow;
  let injector;
  try {
    ({ injector } = setup());
    browserWindow.dispatchEvent(new Event('pageshow'));
    assert.equal(reloads, 0, 'ordinary page loads do not reload in a loop');
    const restored = new Event('pageshow');
    Object.defineProperty(restored, 'persisted', { value: true });
    browserWindow.dispatchEvent(restored);
    assert.equal(reloads, 1);
    injector.destroy();
    injector = undefined;
    browserWindow.dispatchEvent(restored);
    assert.equal(reloads, 1, 'destroyed application removes its event listener');
  } finally {
    injector?.destroy();
    delete global.window;
    fs.rmSync(output, { force: true });
  }
});

test('closing and reopening a tab restores a valid shared session and verifies its profile again', async () => {
  const { output, setup } = await build('session-reopen');
  const data = new Map();
  global.localStorage = { getItem: key => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  const legacy = new Map();
  global.sessionStorage = { removeItem: key => legacy.delete(key) };
  let injector;
  try {
    const first = setup();
    injector = first.injector;
    const token = `header.${Buffer.from(JSON.stringify({ role: 'ADMIN' })).toString('base64url')}.signature`;
    first.auth.login('admin', 'password').subscribe();
    first.http.expectOne('/api/auth/web/login').flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 600 });
    first.http.expectOne('/api/auth/me').flush({ role: 'ADMIN', fullName: 'Admin' });
    first.http.expectOne('/api/employee-requests/capabilities').flush({ departmentHead: false });
    first.http.verify();
    first.injector.destroy();
    assert.ok(data.has('hrm.session'), 'destroying a tab does not sign out the browser');

    const reopened = setup();
    injector = reopened.injector;
    assert.equal(reopened.auth.token(), token);
    assert.equal(reopened.auth.profile(), null, 'private profile is fetched again, not persisted');
    reopened.auth.loadCurrentUser().subscribe();
    const me = reopened.http.expectOne('/api/auth/me');
    assert.equal(me.request.headers.get('Authorization'), `Bearer ${token}`);
    me.flush({ role: 'ADMIN', fullName: 'Current server profile' });
    reopened.http.expectOne('/api/employee-requests/capabilities').flush({ departmentHead: false });
    assert.equal(reopened.auth.profile().fullName, 'Current server profile');
    reopened.auth.logout().subscribe();
    reopened.http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    reopened.http.verify();
    reopened.injector.destroy();

    legacy.set('hrm.session', 'old per-tab credentials');
    const afterLogout = setup();
    injector = afterLogout.injector;
    assert.equal(afterLogout.auth.token(), null);
    assert.equal(legacy.has('hrm.session'), false, 'legacy tab storage cannot revive a logged-out session');
  } finally {
    injector?.destroy();
    delete global.localStorage;
    delete global.sessionStorage;
    fs.rmSync(output, { force: true });
  }
});

test('shared token refresh keeps the login but logout in another tab clears memory and prevents resurrection', async () => {
  const { output, setup } = await build('session-shared');
  const data = new Map();
  global.localStorage = { getItem: key => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  const browserWindow = new EventTarget();
  let reloads = 0;
  browserWindow.location = { reload: () => reloads++ };
  global.window = browserWindow;
  global.document = new EventTarget();
  let injector;
  try {
    const context = setup();
    ({ injector } = context);
    const { auth, http } = context;
    const jwt = sequence => `header.${Buffer.from(JSON.stringify({ role: 'ADMIN', sequence })).toString('base64url')}.signature`;
    auth.login('admin', 'password').subscribe();
    http.expectOne('/api/auth/web/login').flush({ accessToken: jwt(1), tokenType: 'Bearer', expiresInSeconds: 600 });
    http.expectOne('/api/auth/me').flush({ role: 'ADMIN', fullName: 'Admin' });
    http.expectOne('/api/employee-requests/capabilities').flush({ departmentHead: false });
    const shared = JSON.parse(data.get('hrm.session'));
    data.set('hrm.session', JSON.stringify({ ...shared, accessToken: jwt(2), expiresAt: shared.expiresAt + 30000 }));
    const changed = new Event('storage');
    Object.defineProperty(changed, 'key', { value: 'hrm.session' });
    browserWindow.dispatchEvent(changed);
    assert.equal(auth.token(), jwt(2));
    assert.equal(auth.profile().fullName, 'Admin');
    assert.equal(reloads, 0, 'renewal does not interrupt another active tab');
    auth.invalidateSession(jwt(1));
    assert.equal(auth.token(), jwt(2), 'an old token failure cannot end the renewed session');

    data.delete('hrm.session');
    browserWindow.dispatchEvent(changed);
    assert.equal(auth.token(), null);
    assert.equal(auth.profile(), null);
    assert.equal(reloads, 1);
    document.dispatchEvent(new Event('keydown'));
    assert.equal(data.has('hrm.session'), false, 'activity cannot write back credentials after remote logout');
    http.verify();
  } finally {
    injector?.destroy();
    delete global.localStorage;
    delete global.window;
    delete global.document;
    fs.rmSync(output, { force: true });
  }
});

test('a late logout response cannot remove a newer login written by another tab', async () => {
  const { output, setup } = await build('session-shared-logout');
  const data = new Map();
  global.localStorage = { getItem: key => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  let injector;
  try {
    const context = setup();
    ({ injector } = context);
    const { auth, http } = context;
    const token = `header.${Buffer.from(JSON.stringify({ role: 'ADMIN' })).toString('base64url')}.signature`;
    auth.login('admin', 'password').subscribe();
    http.expectOne('/api/auth/web/login').flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 600 });
    http.expectOne('/api/auth/me').flush({ role: 'ADMIN', fullName: 'Admin' });
    http.expectOne('/api/employee-requests/capabilities').flush({ departmentHead: false });
    let loggedOut;
    auth.logout().subscribe(value => loggedOut = value);
    const logout = http.expectOne('/api/auth/logout');
    const newer = { ...JSON.parse(data.get('hrm.session')), sessionId: 'another-login', username: 'another-account' };
    data.set('hrm.session', JSON.stringify(newer));
    logout.flush(null, { status: 204, statusText: 'No Content' });
    assert.equal(loggedOut, false);
    assert.equal(JSON.parse(data.get('hrm.session')).sessionId, 'another-login');
    assert.equal(auth.token(), null, 'the previous page must reload before using another identity');
    http.verify();
  } finally {
    injector?.destroy();
    delete global.localStorage;
    fs.rmSync(output, { force: true });
  }
});
