const fs=require('node:fs'),path=require('node:path');const root=path.resolve(__dirname,'../../../..'),out=path.join(root,'HRM_System/target/data-refresh-private');
const sql=fs.readFileSync(path.join(out,'REFRESH_PRIVATE.sql'),'utf8');
const content=sql.split('\n').filter(l=>!l.includes('Old labels remain')).join('\n');
const info={staffRows:(sql.match(/INSERT @staff VALUES/g)||[]).length,oldDemoLabels:/DEMO|demo\./.test(content),oldQaLabels:/QA6-/.test(content),hashCount:(sql.match(/THEN ''\$2/g)||[]).length};
console.log(info);if(info.staffRows!==32||info.oldDemoLabels||info.oldQaLabels||info.hashCount!==32)throw Error('Preparation validation failed');
for(const f of ['application.properties','application-local.properties','application-hrm-db.properties']){const p=path.join(root,'HRM_System/src/main/resources',f);if(fs.existsSync(p)){const x=fs.readFileSync(p,'utf8');console.log(f,{databases:[...x.matchAll(/databaseName=([^;\s}]+)/gi)].map(m=>m[1])});}}
