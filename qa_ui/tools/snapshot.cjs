const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '../..');
function walk(dir) { return fs.readdirSync(dir, {withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]); }
const files = walk(path.join(root,'frontend/src')).filter(f=>/\.(css|html|ts)$/.test(f));
const baseline = path.join(root,'qa_ui/BASELINE'); fs.mkdirSync(baseline,{recursive:true});
if (!fs.existsSync(path.join(baseline,'source-before.json'))) {
 const records=files.map(file=>{const bytes=fs.readFileSync(file), rel=path.relative(root,file);const dest=path.join(root,'qa_ui/backup_original',rel);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,bytes);return {file:rel,sha256:crypto.createHash('sha256').update(bytes).digest('hex'),bytes:bytes.length};});
 fs.writeFileSync(path.join(baseline,'source-before.json'),JSON.stringify(records,null,2));
}
const text=files.map(f=>fs.readFileSync(f,'utf8')).join('\n');
const css=files.filter(f=>f.endsWith('.css'));
const report={files:files.length,cssBytes:css.reduce((n,f)=>n+fs.statSync(f).size,0),bootstrapButtonOccurrences:(text.match(/class="[^"]*\bbtn\b/g)||[]).length,hexOccurrences:(css.map(f=>fs.readFileSync(f,'utf8')).join('\n').match(/#[\da-f]{3,8}\b/gi)||[]).length,mediaBreakpoints:[...new Set([...text.matchAll(/(?:min|max)-width\s*:\s*(\d+)px/g)].map(m=>+m[1]))].sort((a,b)=>a-b)};
const mode=process.argv[2]||'before';fs.writeFileSync(path.join(baseline,`static-${mode}.json`),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
