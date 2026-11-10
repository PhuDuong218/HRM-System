const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');const ts=require('typescript'),rx=require('rxjs');
function fixture(){
 const scheduler=new rx.VirtualTimeScheduler(),end=new rx.Subject();const listeners={},calls=[];
 const doc={hidden:false,addEventListener:(n,f)=>listeners[n]=f,removeEventListener:n=>delete listeners[n]};
 const HttpClient=class HttpClient{},DestroyRef=class DestroyRef{},ElementRef=class ElementRef{};
 const inbox={items:[],totalElements:0,totalPages:0,page:0,unreadCount:105,snapshotId:10};
 const http={get:(url,options)=>{calls.push([url,options]);return rx.of(url.endsWith('unread-count')?{unreadCount:105}:inbox);},put:()=>rx.of(null)};
 const angular={Component:()=>c=>c,HostListener:()=>()=>{},DestroyRef,ElementRef,inject:token=>token===HttpClient?http:token===DestroyRef?{}:{nativeElement:{contains:()=>false,querySelector:()=>null}}};
 const source=fs.readFileSync(path.join(__dirname,'../src/app/shared/notification-bell.ts'),'utf8');const exports={};
 const output=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
 vm.runInNewContext(output,{exports,document:doc,console,require:name=>name==='@angular/core'?angular:name==='@angular/common/http'?{HttpClient}:name==='@angular/core/rxjs-interop'?{takeUntilDestroyed:()=>rx.takeUntil(end)}:name==='rxjs'?{...rx,forkJoin:arg=>rx.forkJoin(Object.fromEntries(Object.entries(arg))),timer:(a,b)=>rx.timer(a,b,scheduler)}:name.includes('api.config')?{API_BASE_URL:'/api'}:{}});
 const c=new exports.NotificationBell();return {c,calls,doc,listeners,end,flush:to=>{scheduler.maxFrames=to;scheduler.flush();}};
}
test('polls every 15 seconds, pauses when hidden, resumes immediately and cleans up on destroy',()=>{
 const f=fixture();f.c.ngOnInit();f.flush(0);assert.equal(f.calls.length,1);assert.equal(f.c.badge,'99+');
 f.flush(15000);assert.equal(f.calls.length,2);f.doc.hidden=true;f.listeners.visibilitychange();f.flush(60000);assert.equal(f.calls.length,2);
 f.doc.hidden=false;f.listeners.visibilitychange();f.flush(60000);assert.ok(f.calls.length>=3);const n=f.calls.length;f.end.next();f.flush(120000);assert.equal(f.calls.length,n);assert.equal(f.listeners.visibilitychange,undefined);
});
test('opening bell never marks all read; unread filtering and pagination carry only current user filters',()=>{
 const f=fixture();f.c.ngOnInit();f.flush(0);f.c.toggle();assert.equal(f.c.open,true);assert.ok(f.calls.some(([u])=>u==='/api/notifications'));assert.equal(f.c.inbox.snapshotId,10);
 f.c.setFilter(true);const last=f.calls.at(-1);assert.equal(last[1].params.read,false);assert.equal(last[1].params.page,0);assert.equal('recipientId' in last[1].params,false);
 f.end.next();
});
