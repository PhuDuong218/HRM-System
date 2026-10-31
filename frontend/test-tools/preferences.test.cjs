const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const esbuild = require('esbuild');

test('theme and language choices survive navigation and reload; storage failure remains usable', async () => {
  const output = path.join(__dirname, `.preferences-${process.pid}.mjs`);
  await esbuild.build({ entryPoints: [path.join(__dirname, 'preferences-harness.ts')], bundle: true,
    packages: 'external', platform: 'node', format: 'esm', outfile: output });
  try {
    const { ThemeService, LanguageService } = await import(pathToFileURL(output).href);
    const saved = new Map();
    global.localStorage = { getItem: key => saved.get(key) ?? null, setItem: (key, value) => saved.set(key, value) };
    const attributes = {};
    global.document = { documentElement: { setAttribute: (key, value) => attributes[key] = value, lang: '' } };

    const theme = new ThemeService();
    const language = new LanguageService();
    assert.equal(attributes['data-bs-theme'], 'light');
    assert.equal(document.documentElement.lang, 'vi');
    theme.toggle();
    language.toggle();
    assert.equal(theme.darkMode(), true);
    assert.equal(attributes['data-bs-theme'], 'dark');
    assert.equal(language.t('Đăng nhập'), 'Sign in');
    for (const [vi, en] of [
      ['Đồng hành cùng đội ngũ', 'Supporting your team'],
      ['Nhân viên', 'Employees'],
      ['Phân công / Điều chuyển', 'Assignments / Transfers'],
      ['Hợp đồng', 'Contracts'],
      ['Phòng ban', 'Departments'],
      ['Quản lý đơn từ', 'Requests'],
      ['Báo cáo', 'Reports'],
      ['Vui lòng nhập tên đăng nhập và mật khẩu để tiếp tục.', 'Please enter your username and password to continue.'],
      ['Nhập thông tin tài khoản để truy cập không gian quản lý của bạn.', 'Enter your account details to access your management workspace.']
    ]) assert.equal(language.t(vi), en);
    assert.equal(document.documentElement.lang, 'en');
    assert.equal(new ThemeService().darkMode(), true);
    assert.equal(new LanguageService().current(), 'en');
    language.toggle();
    assert.equal(language.t('Đồng hành cùng đội ngũ'), 'Đồng hành cùng đội ngũ');

    global.localStorage = { getItem() { throw new Error('unavailable'); }, setItem() { throw new Error('unavailable'); } };
    const temporaryTheme = new ThemeService();
    const temporaryLanguage = new LanguageService();
    temporaryTheme.toggle();
    temporaryLanguage.toggle();
    assert.equal(temporaryTheme.darkMode(), true);
    assert.equal(attributes['data-bs-theme'], 'dark');
    assert.equal(temporaryLanguage.current(), 'en');
    assert.equal(document.documentElement.lang, 'en');
  } finally {
    delete global.localStorage;
    delete global.document;
    fs.unlinkSync(output);
  }
});
