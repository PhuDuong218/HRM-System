const fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const ts=require('../frontend/node_modules/typescript');
(async()=>{
 const base=path.join(__dirname,'proposed/snapshots/QA-10/after');const results=[];
 for(const p of ['frontend/src/app/pages/payroll/payroll.ts','frontend/src/app/services/language.service.ts']){
  const result=ts.transpileModule(fs.readFileSync(path.join(base,p),'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,experimentalDecorators:true},reportDiagnostics:true});
  results.push({file:p,syntaxErrors:result.diagnostics.filter(d=>d.category===ts.DiagnosticCategory.Error).map(d=>ts.flattenDiagnosticMessageText(d.messageText,' '))});
 }
 const angular=await import(pathToFileURL(path.resolve('frontend/node_modules/@angular/compiler/fesm2022/compiler.mjs')).href);
 const html=fs.readFileSync(path.join(base,'frontend/src/app/pages/payroll/payroll.html'),'utf8');
 results.push({file:'payroll.html',syntaxErrors:angular.parseTemplate(html,'payroll.html').errors?.map(String)||[]});
 fs.writeFileSync('qa_fix/evidence/proposed-syntax.json',JSON.stringify({note:'Syntax only; proposals not applied or integration-tested',results},null,2));
 console.log(results.map(r=>({file:r.file,errors:r.syntaxErrors.length})));if(results.some(r=>r.syntaxErrors.length))process.exitCode=1;
})();
