const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const { spawn } = require('node:child_process');
const esbuild = require('esbuild');

const browserPath = [process.env.HRM_TEST_BROWSER,
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  '/usr/bin/chromium', '/usr/bin/google-chrome',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
].find(file => file && fs.existsSync(file));

const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(read, check, message) {
  const deadline = Date.now() + 15000;
  let value;
  while (Date.now() < deadline) {
    try { value = await read(); if (check(value)) return value; } catch { /* A document may be navigating. */ }
    await delay(40);
  }
  throw new Error(`${message}: ${JSON.stringify(value)}`);
}

async function connect(url) {
  const socket = new WebSocket(url);
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });
  let sequence = 0;
  const pending = new Map();
  socket.addEventListener('message', event => {
    const response = JSON.parse(event.data);
    const handler = pending.get(response.id);
    if (!handler) return;
    pending.delete(response.id);
    clearTimeout(handler.timer);
    if (response.error) handler.reject(new Error(JSON.stringify(response.error)));
    else handler.resolve(response.result);
  });
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timed out: ${method}`)); }, 15000);
    pending.set(id, { resolve, reject, timer });
    socket.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const response = await send('Runtime.evaluate', { expression, awaitPromise: true,
      returnByValue: true, userGesture: true });
    if (response.exceptionDetails) throw new Error(JSON.stringify(response.exceptionDetails));
    return response.result?.value;
  };
  return { send, evaluate, close: () => socket.close() };
}

test('native browser Back/Forward preserves pre-site history and uses current authentication', {
  skip: !browserPath && 'Set HRM_TEST_BROWSER to a Chromium browser executable', timeout: 120000
}, async t => {
  // Real authentication and production guards, with lightweight pages and a fake backend.
  // No production account, backend, or browser profile is used.
  const bundle = await esbuild.build({ stdin: { resolveDir: path.resolve(__dirname, '..'), loader: 'ts', contents: `
    import 'zone.js';
    import '@angular/compiler';
    import { Component, inject } from '@angular/core';
    import { bootstrapApplication } from '@angular/platform-browser';
    import { Router, RouterOutlet, provideRouter, withRouterConfig } from '@angular/router';
    import { HttpBackend, HttpResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
    import { of, delay, firstValueFrom } from 'rxjs';
    import { routes } from './src/app/app.routes';
    import { AuthService } from './src/app/services/auth.service';
    import { authInterceptor } from './src/app/services/auth.interceptor';
    import { PendingEditorService } from './src/app/services/pending-editor.service';
    let canLeave = true;
    const checks = [];
    class FakeBackend extends HttpBackend {
      handle(request) {
        let body;
        if (request.url.endsWith('/auth/web/login')) {
          const accessToken = 'header.' + btoa(JSON.stringify({ role: request.body.password,
            username: request.body.username, sequence: crypto.randomUUID() })) + '.signature';
          body = { accessToken, tokenType: 'Bearer', expiresInSeconds: 600 };
        } else {
          const token = request.headers.get('Authorization')?.split(' ')[1];
          const claims = token ? JSON.parse(atob(token.split('.')[1])) : {};
          if (request.url.endsWith('/auth/me')) {
            checks.push(claims.username);
            body = { username: claims.username, fullName: claims.username, role: claims.role };
          } else if (request.url.endsWith('/capabilities')) body = { departmentHead: claims.role === 'EMPLOYEE' };
          else if (request.url.endsWith('/auth/web/refresh')) body = { accessToken: token, tokenType: 'Bearer', expiresInSeconds: 600 };
        }
        return of(new HttpResponse({ status: 200, body })).pipe(delay(0));
      }
    }
    @Component({ standalone: true, template: '<main>{{ router.url }} | {{ auth.username() }}</main>' })
    class Page { router = inject(Router); auth = inject(AuthService); }
    @Component({ selector: 'history-test', standalone: true, imports: [RouterOutlet], template: '<router-outlet />' })
    class Root {}
    const pages = routes.map(route => {
      const { component, loadComponent, children, ...config } = route;
      return children ? { ...config, children: children.map(child => {
        const { component, loadComponent, ...settings } = child;
        return { ...settings, ...(child.redirectTo ? {} : { component: Page }) };
      }) } : { ...config, component: Page };
    });
    bootstrapApplication(Root, { providers: [
      provideRouter(pages, withRouterConfig({ canceledNavigationResolution: 'computed' })),
      provideHttpClient(withInterceptors([authInterceptor])),
      { provide: HttpBackend, useClass: FakeBackend },
      { provide: PendingEditorService, useValue: { allow: () => Promise.resolve(canLeave) } }
    ] }).then(app => {
      const router = app.injector.get(Router);
      const auth = app.injector.get(AuthService);
      window.hrmHistoryTest = {
        navigate: url => router.navigateByUrl(url),
        login: async (username, role = 'ADMIN') => {
          await firstValueFrom(auth.login(username, role));
          await router.navigateByUrl(role === 'EMPLOYEE' ? '/requests/head' : '/overview', { replaceUrl: true });
        },
        logout: async () => {
          await firstValueFrom(auth.logout());
          await router.navigateByUrl('/login', { replaceUrl: true });
        },
        allowLeave: value => { canLeave = value; },
        snapshot: () => ({ url: router.url, username: auth.username(), checks: [...checks] })
      };
    });
  ` }, bundle: true, write: false, format: 'iife', platform: 'browser',
    tsconfigRaw: { compilerOptions: { experimentalDecorators: true } }, logLevel: 'silent' });
  const html = '<!doctype html><html><head><base href="/"></head><body><history-test></history-test><script src="/fixture.js"></script></body></html>';
  const server = http.createServer((request, response) => {
    response.setHeader('Content-Type', request.url === '/fixture.js' ? 'application/javascript' : 'text/html');
    response.end(request.url === '/fixture.js' ? bundle.outputFiles[0].contents :
      request.url === '/before' ? '<!doctype html><a id="enter" href="/login">Open app</a>' : html);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'hrm-history-test-'));
  const browser = spawn(browserPath, ['--headless=new', '--no-first-run', '--no-default-browser-check',
    '--disable-extensions', '--disable-gpu', '--remote-debugging-port=0',
    `--user-data-dir=${temporaryRoot}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
  let controller;
  const tabs = [];
  try {
    const portFile = path.join(temporaryRoot, 'DevToolsActivePort');
    await until(() => fs.existsSync(portFile), Boolean, 'Browser did not start');
    const [port, endpoint] = fs.readFileSync(portFile, 'utf8').trim().split(/\r?\n/);
    controller = await connect(`ws://127.0.0.1:${port}${endpoint}`);
    async function openApp(existingContext, expectedUrl = '/login') {
      const { browserContextId } = existingContext ? { browserContextId: existingContext } :
        await controller.send('Target.createBrowserContext');
      const { targetId } = await controller.send('Target.createTarget', { url: `${origin}/before`, browserContextId });
      const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
      const tab = await connect(targets.find(target => target.id === targetId).webSocketDebuggerUrl);
      tab.browserContextId = browserContextId;
      tab.targetId = targetId;
      tabs.push(tab);
      await until(() => tab.evaluate('!!document.querySelector("#enter")'), Boolean, 'Before-site page did not load');
      await tab.evaluate('document.querySelector("#enter").click()');
      await until(() => tab.evaluate('!!window.hrmHistoryTest'), Boolean, 'Angular did not bootstrap');
      await waitUrl(tab, expectedUrl);
      return tab;
    }
    const command = (tab, action) => tab.evaluate(`window.hrmHistoryTest.${action}`);
    const waitUrl = (tab, url) => until(() => tab.evaluate('location.pathname + location.search + location.hash'),
      actual => actual === url, `Expected browser URL ${url}`);
    const move = async (tab, direction, url) => {
      const previous = await tab.send('Page.getNavigationHistory');
      await tab.evaluate(`history.${direction}()`);
      await until(() => tab.send('Page.getNavigationHistory'), state => state.currentIndex !== previous.currentIndex,
        `Browser did not move ${direction}`);
      // Same-URL entries still traverse asynchronously before Angular handles popstate.
      await delay(60);
      await waitUrl(tab, url);
      if (url !== '/before') await until(() => command(tab, 'snapshot()'), state => state.url === url, 'Router and browser URL differ');
    };

    await t.test('logout blocks private history and Back eventually returns to the actual page before the app', async () => {
      const tab = await openApp();
      await command(tab, 'login("first-user")');
      await command(tab, 'navigate("/employees")');
      await command(tab, 'navigate("/contracts")');
      await move(tab, 'back', '/employees');
      await move(tab, 'forward', '/contracts');
      await command(tab, 'logout()');
      const checks = (await command(tab, 'snapshot()')).checks.length;
      await move(tab, 'back', '/login');
      await move(tab, 'back', '/login');
      assert.equal((await command(tab, 'snapshot()')).checks.length, checks);
      await move(tab, 'back', '/before');
      assert.equal(await tab.evaluate('document.querySelector("#enter").textContent'), 'Open app');
    });

    await t.test('a new login traverses old URLs with the new user and freshly checked permissions', async () => {
      for (const direction of ['back', 'forward']) {
        const tab = await openApp();
        await command(tab, 'login("old-user")');
        await command(tab, 'navigate("/employees?keyword=old#list")');
        await command(tab, 'navigate("/contracts")');
        if (direction === 'forward') await move(tab, 'back', '/employees?keyword=old#list');
        await command(tab, 'logout()');
        await command(tab, 'login("new-user")');
        const expected = direction === 'back' ? '/employees?keyword=old#list' : '/contracts';
        await move(tab, direction, expected);
        const state = await command(tab, 'snapshot()');
        assert.equal(state.username, 'new-user');
        assert.equal(state.checks.at(-1), 'new-user');
        assert.equal(await tab.evaluate('document.querySelector("main").textContent.includes("old-user")'), false);
      }
      const tab = await openApp();
      await command(tab, 'login("admin-user")');
      await command(tab, 'navigate("/accounts")');
      await command(tab, 'navigate("/contracts")');
      await command(tab, 'logout()');
      await command(tab, 'login("head-user", "EMPLOYEE")');
      await move(tab, 'back', '/requests/head');
      assert.equal((await command(tab, 'snapshot()')).username, 'head-user');
    });

    await t.test('canceling Back for an unsaved form preserves both original history entries', async () => {
      const tab = await openApp();
      await command(tab, 'login("editor-user")');
      await command(tab, 'navigate("/employees")');
      await command(tab, 'navigate("/contracts")');
      const previous = await tab.send('Page.getNavigationHistory');
      await command(tab, 'allowLeave(false)');
      await tab.evaluate('history.back()');
      await delay(250);
      await waitUrl(tab, '/contracts');
      const canceled = await tab.send('Page.getNavigationHistory');
      assert.equal(canceled.currentIndex, previous.currentIndex);
      assert.deepEqual(canceled.entries.map(entry => entry.url), previous.entries.map(entry => entry.url));
      await command(tab, 'allowLeave(true)');
      await move(tab, 'back', '/employees');
      await move(tab, 'forward', '/contracts');
    });

    await t.test('closing the signed-in tab and reopening the app keeps a valid session', async () => {
      const first = await openApp();
      await command(first, 'login("persistent-user")');
      await command(first, 'navigate("/contracts")');
      // Keep a separate unrelated tab open, as in the reported browser workflow.
      await controller.send('Target.createTarget', { url: 'about:blank', browserContextId: first.browserContextId });
      await controller.send('Target.closeTarget', { targetId: first.targetId });
      const reopened = await openApp(first.browserContextId, '/overview');
      const state = await command(reopened, 'snapshot()');
      assert.equal(state.username, 'persistent-user');
      assert.equal(state.checks.at(-1), 'persistent-user', 'the restored login is verified again');
      await command(reopened, 'logout()');
      const signedOut = await openApp(first.browserContextId);
      assert.equal((await command(signedOut, 'snapshot()')).username, '');
    });

    await t.test('logout and account changes propagate to another already-open tab', async () => {
      const first = await openApp();
      await command(first, 'login("shared-admin")');
      const second = await openApp(first.browserContextId, '/overview');
      await command(second, 'navigate("/accounts")');
      await command(first, 'logout()');
      await waitUrl(second, '/login');
      await until(() => command(second, 'snapshot()'), state => state.username === '', 'Other tab retained old identity');
      await command(first, 'login("shared-head", "EMPLOYEE")');
      await waitUrl(second, '/requests/head');
      await until(() => command(second, 'snapshot()'), state => state.username === 'shared-head', 'Other tab did not adopt new login');
      const state = await command(second, 'snapshot()');
      assert.equal(state.url, '/requests/head', 'old administrator access is not retained');
    });

    await t.test('an expired shared session does not survive reopening the app', async () => {
      const first = await openApp();
      await command(first, 'login("expired-user")');
      const context = first.browserContextId;
      await controller.send('Target.closeTarget', { targetId: first.targetId });
      // Simulate time passing while all application tabs are closed.
      const { targetId } = await controller.send('Target.createTarget', { url: origin + '/before', browserContextId: context });
      const targets = await (await fetch('http://127.0.0.1:' + port + '/json/list')).json();
      const helper = await connect(targets.find(target => target.id === targetId).webSocketDebuggerUrl);
      tabs.push(helper);
      await until(() => helper.evaluate('!!document.querySelector("#enter")'), Boolean, 'Helper did not load');
      await helper.evaluate(`(() => {
        const session = JSON.parse(localStorage.getItem('hrm.session'));
        session.expiresAt = Date.now() - 1000;
        localStorage.setItem('hrm.session', JSON.stringify(session));
      })()`);
      const reopened = await openApp(context);
      assert.equal((await command(reopened, 'snapshot()')).username, '');
      assert.equal(await reopened.evaluate('localStorage.getItem("hrm.session")'), null);
    });
  } finally {
    for (const tab of tabs) tab.close();
    if (controller) {
      await controller.send('Browser.close').catch(() => {});
      controller.close();
    }
    await until(() => browser.exitCode !== null, Boolean, 'Browser did not exit').catch(() => browser.kill());
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
    // Remove only the unique directory created by this test, under the OS temp folder.
    assert.equal(path.dirname(path.resolve(temporaryRoot)), path.resolve(os.tmpdir()));
    assert.ok(path.basename(temporaryRoot).startsWith('hrm-history-test-'));
    fs.rmSync(temporaryRoot, { recursive: true, force: true, maxRetries: 10, retryDelay: 100 });
  }
});
