const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('logout clears local session on success, offline, and timeout without clearing a newer login', async () => {
  const output = path.join(__dirname, `.logout-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'logout-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  const data = new Map();
  global.localStorage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const { auth, http, injector } = setup();
    const jwt = sequence => `header.${Buffer.from(JSON.stringify({ role: 'ADMIN', sequence })).toString('base64url')}.signature`;
    function login(sequence) {
      auth.login('admin', 'password').subscribe();
      http.expectOne('/api/auth/web/login').flush({ accessToken: jwt(sequence), tokenType: 'Bearer', expiresInSeconds: 300 });
      http.expectOne('/api/auth/me').flush({role:'ADMIN',fullName:'Admin'});
      http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:false});
    }
    login(1);
    let result;
    auth.logout().subscribe(value => result = value);
    let request = http.expectOne('/api/auth/logout');
    assert.equal(request.request.headers.get('Authorization'), `Bearer ${jwt(1)}`);
    request.flush(null, { status: 204, statusText: 'No Content' });
    assert.equal(result, true); assert.equal(auth.token(), null); assert.equal(data.has('hrm.session'), false);

    login(2);
    auth.logout().subscribe(value => result = value);
    http.expectOne('/api/auth/logout').error(new Event('error'));
    assert.equal(result, true); assert.equal(auth.token(), null); assert.equal(data.has('hrm.session'), false);

    login(3);
    const timedOut = new Promise(resolve => auth.logout().subscribe(resolve));
    request = http.expectOne('/api/auth/logout');
    assert.equal(await timedOut, true);
    assert.equal(request.cancelled, true); assert.equal(auth.token(), null);

    login(4);
    auth.logout().subscribe(value => result = value);
    request = http.expectOne('/api/auth/logout');
    login(5);
    request.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    assert.equal(result, false); assert.equal(auth.token(), jwt(5));
    http.verify(); injector.destroy();
  } finally { delete global.localStorage; fs.unlinkSync(output); }
});
