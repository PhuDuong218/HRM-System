const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const esbuild=require('esbuild');
test('accounts UI labels translate and the selected language survives reload',async()=>{
 const output=path.join(__dirname,`.accounts-i18n-${process.pid}.mjs`);
 await esbuild.build({entryPoints:[path.join(__dirname,'preferences-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
 const storage=new Map();global.localStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)};global.document={documentElement:{lang:''}};
 try {
  const {LanguageService}=await import(pathToFileURL(output).href);const language=new LanguageService();
  const template=fs.readFileSync(path.join(__dirname,'../src/app/pages/accounts/accounts.html'),'utf8');
  const labels=[...new Set([...template.matchAll(/'([^']+)'\s*\|\s*translate/g)].map(m=>m[1]).filter(t=>/[^\x00-\x7F]/.test(t)&&/\p{L}/u.test(t)))];assert.ok(labels.length>50);
  for(const label of labels)assert.equal(language.t(label),label);
  language.toggle();assert.deepEqual(labels.filter(label=>language.t(label).trim()===label.trim()),[], 'Missing English account UI labels');
  assert.equal(language.t('accounts.status.locked'),'Locked');
  assert.equal(language.t('accounts.unlock'),'Unlock');
  assert.equal(language.t('accounts.employeeId'),'Employee ID');
  const reloaded=new LanguageService();assert.equal(reloaded.current(),'en');assert.notEqual(reloaded.t('Tài khoản và phân quyền'),'Tài khoản và phân quyền');
  reloaded.toggle();assert.equal(reloaded.t('Tài khoản và phân quyền'),'Tài khoản và phân quyền');
  assert.equal(reloaded.t('accounts.status.locked'),'Đã khóa');
 } finally{fs.unlinkSync(output);delete global.localStorage;delete global.document;}
});
