const fs=require('fs'),path=require('path');
const root=__dirname;
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?files(path.join(dir,e.name)):[path.join(dir,e.name)]);}
const backend=files(path.join(root,'HRM_System/src/main/java'));
const frontend=files(path.join(root,'frontend/src'));
const endpoints=[];
for(const f of backend.filter(f=>f.endsWith('Controller.java'))){
 const s=fs.readFileSync(f,'utf8'),classAt=s.indexOf('public class'),prefix=s.slice(0,classAt);
 const base=prefix.match(/@RequestMapping\s*\(([^)]*)\)/s);
 const bases=base?[...base[1].matchAll(/"([^"\n]*)"/g)].map(m=>m[1]):[''];
 for(const m of s.slice(classAt).matchAll(/@(Get|Post|Put|Delete|Patch)Mapping\b(?:\s*\(([^)]*)\))?/gs)){
  const ps=m[2]?[...m[2].matchAll(/"([^"\n]*)"/g)].map(x=>x[1]).filter(x=>x.startsWith('/')):[''];
  for(const b of bases)for(const p of ps.length?ps:[''])endpoints.push({method:m[1].toUpperCase(),url:b+p,file:path.relative(root,f).replaceAll('\\','/'),line:s.slice(0,classAt+m.index).split('\n').length});
 }
}
const calls=[];
for(const f of frontend.filter(f=>f.endsWith('.ts'))){const s=fs.readFileSync(f,'utf8');for(const m of s.matchAll(/(?:this\.)?http\.(get|post|put|patch|delete)\s*(?:<[^;\n]*?>)?\s*\(\s*([`'"])(.*?)\2/gs))calls.push({method:m[1].toUpperCase(),url:m[3],file:path.relative(root,f).replaceAll('\\','/'),line:s.slice(0,m.index).split('\n').length});}
const counts={backendJava:backend.filter(f=>f.endsWith('.java')).length,controllers:backend.filter(f=>f.endsWith('Controller.java')).length,frontendSourceFiles:frontend.length,mappingPaths:endpoints.length,controllerHandlerAnnotations:backend.filter(f=>f.endsWith('Controller.java')).reduce((n,f)=>n+[...fs.readFileSync(f,'utf8').matchAll(/@(Get|Post|Put|Delete|Patch)Mapping\b/g)].length,0),literalHttpCalls:calls.length};
fs.writeFileSync(path.join(root,'inventory.json'),JSON.stringify({counts,endpoints,calls},null,2));
fs.writeFileSync(path.join(root,'API_INVENTORY.md'),'# Kiểm kê API từ mã trong ZIP\n\nTự trích xuất annotation; số đường dẫn có tính alias. Không phải kết quả gọi HTTP, không chứng minh quyền hoặc nghiệp vụ đúng. Các URL dựng động cần đọc thủ công.\n\n'+JSON.stringify(counts,null,2)+'\n\n| Method | Path | Source |\n|---|---|---|\n'+endpoints.map(e=>`| ${e.method} | \`${e.url}\` | [${path.basename(e.file)}:${e.line}](./${e.file}#L${e.line}) |`).join('\n')+'\n\n# Lời gọi HTTP có URL literal trong frontend\n\n| Method | URL template | Source |\n|---|---|---|\n'+calls.map(e=>`| ${e.method} | \`${e.url}\` | ${e.file}:${e.line} |`).join('\n'));
console.log(JSON.stringify(counts,null,2));
