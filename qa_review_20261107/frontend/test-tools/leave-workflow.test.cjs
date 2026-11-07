const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url'),esbuild=require('esbuild');
test('first approval checks appointment and preserves failed decision drafts',async()=>{
 const file=path.join(__dirname,`.leave-workflow-${process.pid}.mjs`);
 await esbuild.build({entryPoints:[path.join(__dirname,'leave-workflow-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:file});
 try{const {setup}=await import(pathToFileURL(file).href);
 const noHead=setup();noHead.http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:false});noHead.page.load();noHead.http.expectNone(r=>r.url.includes('head-queue'));assert.ok(noHead.page.error.includes('nhiệm kỳ'));noHead.http.verify();noHead.injector.destroy();
 const head=setup();head.http.expectOne('/api/employee-requests/capabilities').flush({departmentHead:true});
 const row={requestId:123,requestCode:'QA',requestType:'NGHI_PHEP'};
 head.http.expectOne(r=>r.url==='/api/employee-requests/head-queue').flush({content:[row],totalPages:1,totalElements:1});head.page.open(row);head.http.expectOne('/api/employee-requests/123').flush({reason:'QA reason',leaveStart:'2026-11-06T08:00:00'});
 head.page.note='  ';head.page.decide(false);head.http.expectNone('/api/employee-requests/123/head-decision');
 head.page.note='Giữ lý do sau lỗi';head.page.decide(false);head.page.decide(false);const request=head.http.expectOne('/api/employee-requests/123/head-decision');assert.equal(request.request.body.approve,false);request.flush({loi_chi_tiet:'Đơn đã đổi trạng thái'},{status:409,statusText:'Conflict'});
 assert.equal(head.page.note,'Giữ lý do sau lỗi');assert.equal(head.page.selected.requestId,123);assert.equal(head.page.saving,false);head.page.loading=true;head.page.load(3);assert.equal(head.page.page,0);head.http.verify();head.injector.destroy();
 }finally{fs.rmSync(file,{force:true});}
});
