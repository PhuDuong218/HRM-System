const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

// Run the production TypeScript helpers directly without introducing a test framework.
const source = fs.readFileSync(path.join(__dirname, '../src/app/services/report-format.ts'), 'utf8');
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
const testModule = { exports: {} };
new Function('exports', 'module', 'require', js)(testModule.exports, testModule, require);
const { parsePayrollReport, formatMoney } = testModule.exports;

test('preserves exact payroll decimals, including values beyond JavaScript integer precision', () => {
  const result = parsePayrollReport('{"batchCount":1,"totals":{"grossPayroll":9007199254740993.1234,"allowances":20.0000,"bonuses":1e+3,"deductions":-0.0100,"netPayroll":9007199254740993.1134},"batches":[{"totals":{"grossPayroll":0.0001,"allowances":0,"bonuses":0,"deductions":0,"netPayroll":0.0001}}]}');
  assert.equal(result.batchCount, 1);
  assert.equal(result.totals.grossPayroll, '9007199254740993.1234');
  assert.equal(result.totals.bonuses, '1e+3');
  assert.equal(result.batches[0].totals.grossPayroll, '0.0001');
  assert.equal(formatMoney(result.totals.grossPayroll), '9.007.199.254.740.993,1234');
  assert.equal(formatMoney(result.totals.deductions), '-0,0100');
  assert.equal(formatMoney(result.totals.bonuses), '1.000');
});

test('does not alter JSON strings or accidentally treat matching values as keys', () => {
  const json = '{"periodCode":"\\\"netPayroll\\\": 20", "label":"grossPayroll", "totals":{"netPayroll":12.5000}}';
  const result = parsePayrollReport(json);
  assert.equal(result.periodCode, '"netPayroll": 20');
  assert.equal(result.label, 'grossPayroll');
  assert.equal(result.totals.netPayroll, '12.5000');
  assert.throws(() => parsePayrollReport('{"netPayroll":'), SyntaxError);
});

test('formats decimal exponents without rounding', () => {
  assert.equal(formatMoney('1.2345e-4'), '0,00012345');
  assert.equal(formatMoney('123.4500'), '123,4500');
  assert.equal(formatMoney('1e4'), '10.000');
});
