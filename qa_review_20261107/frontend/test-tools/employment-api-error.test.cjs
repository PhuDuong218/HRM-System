const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const ts = require('typescript');

const source = readFileSync(join(__dirname, '../src/app/shared/employment-api-error.ts'), 'utf8');
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const helpers = {};
new Function('exports', js)(helpers);
const { employmentApiError } = helpers;

test('employment pages distinguish validation, access, missing data, conflict and unavailable services', () => {
  const expected = {
    400: /không hợp lệ/,
    403: /không có quyền/,
    404: /Không tìm thấy/,
    409: /xung đột/,
    503: /chưa sẵn sàng/,
  };
  for (const [status, pattern] of Object.entries(expected)) {
    assert.match(employmentApiError({ status: Number(status), error: null }, 'fallback'), pattern);
  }
});

test('employment pages preserve actionable details from the backend', () => {
  assert.equal(employmentApiError({ status: 409, error: { loi_chi_tiet: 'Kỳ lương đã chốt' } }, 'fallback'), 'Kỳ lương đã chốt');
  assert.equal(employmentApiError({ status: 400, error: { detail: { date: 'Ngày không hợp lệ' } } }, 'fallback'), 'Ngày không hợp lệ');
});
