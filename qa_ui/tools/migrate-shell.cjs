const fs=require('node:fs');const path=require('node:path');const postcss=require('../../frontend/node_modules/postcss');const root=path.resolve(__dirname,'../..');const read=p=>fs.readFileSync(path.join(root,p),'utf8');const write=(p,s)=>fs.writeFileSync(path.join(root,p),s);
const file='frontend/src/app/layout/admin-layout.css';const ast=postcss.parse(read(file));
const structural=/\.app-shell|\.sidebar|\.brand|\.nav-|\bnav\b|\.topbar|\.workspace|\.page-content|\bfooter\b|\.menu-|\.mobile-close|\.account-actions|\.theme-toggle|\.language-|\.chevron|\.flyout/;
ast.walkRules(rule=>{if(structural.test(rule.selector))rule.remove();});ast.walkAtRules(rule=>{if(rule.nodes&&!rule.nodes.length)rule.remove();});
const shell=`
/* Shell geometry is defined once; profile/password dialogs above keep their behavior. */
.app-shell{display:flex;min-height:100dvh;background:var(--hrm-canvas)}
.workspace{flex:1;min-width:0;display:flex;flex-direction:column;--app-topbar-height:var(--hrm-topbar)}
.sidebar-rail{position:sticky;top:0;flex:0 0 var(--hrm-sidebar);width:var(--hrm-sidebar);height:100dvh;z-index:var(--hrm-z-dropdown);transition:width var(--hrm-motion-base),flex-basis var(--hrm-motion-base)}
.sidebar{height:100%;padding:var(--hrm-space-5) var(--hrm-space-4);overflow-y:auto;overscroll-behavior:contain;scrollbar-gutter:stable;background:var(--hrm-surface);border-right:var(--hrm-border)}
.brand{display:flex;align-items:center;gap:var(--hrm-space-3);padding-right:var(--hrm-space-6);margin-bottom:var(--hrm-space-6);text-decoration:none;color:var(--hrm-text);font-weight:600;font-size:var(--hrm-font-section)}
.brand-mark{display:grid;place-items:center;flex:0 0 var(--hrm-control-md);width:var(--hrm-control-md);height:var(--hrm-control-md);border-radius:var(--hrm-radius-md);background:var(--hrm-primary);color:var(--hrm-on-primary)}
.brand small{display:block;font-size:var(--hrm-font-xs);font-weight:400;color:var(--hrm-muted);margin-top:var(--hrm-space-1)}
.nav-heading{font-size:var(--hrm-font-xs);font-weight:600;color:var(--hrm-muted);padding-inline:var(--hrm-space-2);margin-bottom:var(--hrm-space-3)}
nav{display:grid;gap:var(--hrm-space-1)}
nav a,.nav-group-toggle{display:flex;align-items:center;gap:var(--hrm-space-2);min-height:var(--hrm-control-md);width:100%;padding:var(--hrm-space-2);border:0;border-radius:var(--hrm-radius-md);background:transparent;color:var(--hrm-text);font:400 var(--hrm-font-sm)/1.54 var(--hrm-font);text-decoration:none;text-align:left}
.nav-group-toggle{font-weight:600;cursor:pointer}.nav-label{flex:1;min-width:0}.nav-icon{width:1.25rem;height:1.25rem;flex-shrink:0;display:block}
nav a:hover,.nav-group-toggle:hover,.nav-group-toggle.group-active{background:var(--hrm-subtle);color:var(--hrm-primary)}
nav a.active{background:var(--hrm-selected);color:var(--hrm-primary);box-shadow:inset 3px 0 var(--hrm-primary);font-weight:600}
.nav-children{display:grid;gap:var(--hrm-space-1);margin:var(--hrm-space-1) 0 var(--hrm-space-2) var(--hrm-space-4);padding-inline-start:var(--hrm-space-2);border-inline-start:var(--hrm-border)}.nav-children[hidden]{display:none}
.chevron{width:.45rem;height:.45rem;flex-shrink:0;margin-inline-end:var(--hrm-space-1);border-right:1.5px solid currentColor;border-bottom:1.5px solid currentColor;transform:rotate(-45deg)}.chevron.expanded{transform:rotate(45deg)}
.sidebar-note{padding:var(--hrm-space-4) var(--hrm-space-2) 0;margin-top:var(--hrm-space-5);border-top:var(--hrm-border);color:var(--hrm-muted);font-size:var(--hrm-font-xs)}.sidebar-note span{display:block}
.sidebar-handle{display:grid;place-items:center;position:absolute;right:var(--hrm-space-3);top:var(--hrm-space-5);width:var(--hrm-control-sm);height:var(--hrm-control-sm);padding:0;border:var(--hrm-border);border-radius:var(--hrm-radius-md);background:var(--hrm-surface);color:var(--hrm-primary);cursor:pointer}.sidebar-handle .points-right{transform:rotate(180deg)}
.topbar{position:sticky;top:0;z-index:var(--hrm-z-sticky);display:flex;align-items:center;justify-content:space-between;gap:var(--hrm-space-4);min-height:var(--hrm-topbar);padding:var(--hrm-space-2) var(--hrm-space-5);background:var(--hrm-surface);border-bottom:var(--hrm-border);font-size:var(--hrm-font-sm)}
.topbar>div:first-child{min-width:0}.topbar-title{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--hrm-muted)}
.account-actions{display:flex;align-items:center;gap:var(--hrm-space-2);flex-shrink:0}.profile-anchor{min-width:0}.account-name{max-width:10rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:var(--hrm-text)}
.theme-toggle,.language-toggle{display:inline-grid;place-items:center;flex:0 0 var(--hrm-control-md);width:var(--hrm-control-md);height:var(--hrm-control-md);padding:0;border:var(--hrm-border);border-radius:var(--hrm-radius-md);background:var(--hrm-surface);color:var(--hrm-primary);font-size:var(--hrm-font-sm);cursor:pointer}
.language-thumb{display:none}.language-option{display:none}.language-option.is-active{display:inline;font-weight:600}
.page-content{flex:1;width:100%;max-width:1600px;min-width:0;margin-inline:auto;padding:var(--hrm-space-5)}
footer{padding:var(--hrm-space-4) var(--hrm-space-5);color:var(--hrm-muted);font-size:var(--hrm-font-xs)}
.menu-button,.mobile-close,.menu-backdrop,.sidebar-flyout{display:none}
@media(min-width:1024px){
 .sidebar-collapsed .sidebar-rail{width:var(--hrm-sidebar-compact);flex-basis:var(--hrm-sidebar-compact)}
 .sidebar-collapsed .sidebar{padding:var(--hrm-space-5) var(--hrm-space-2);scrollbar-width:none}
 .sidebar-collapsed .brand{padding:0;justify-content:center;margin-bottom:var(--hrm-space-8)}
 .sidebar-collapsed :is(.brand-text,.nav-heading,.sidebar-note,.nav-label,.chevron,.nav-children){display:none}
 .sidebar-collapsed :is(.nav-group-toggle,.nav-single){justify-content:center;padding:var(--hrm-space-3)}
 .sidebar-collapsed .sidebar-handle{top:calc(var(--hrm-space-8) + var(--hrm-space-2));right:calc((var(--hrm-sidebar-compact) - var(--hrm-control-sm))/2)}
 .sidebar-collapsed .sidebar-flyout{display:grid;gap:var(--hrm-space-1);position:fixed;left:var(--hrm-sidebar-compact);width:var(--hrm-sidebar);padding:var(--hrm-space-3);overflow-y:auto;max-height:calc(100dvh - var(--hrm-space-5));border:var(--hrm-border);border-radius:var(--hrm-radius-md);background:var(--hrm-surface);box-shadow:var(--hrm-shadow-2)}
 .flyout-heading{font-size:var(--hrm-font-sm);font-weight:600;padding:var(--hrm-space-2);border-bottom:var(--hrm-border)}
}
@media(max-width:1023px){
 .app-shell{display:block}.sidebar-rail{position:fixed;inset:0 auto 0 0;width:min(320px,calc(100dvw - var(--hrm-space-7)));height:100dvh;z-index:var(--hrm-z-drawer);transform:translateX(-100%);visibility:hidden;pointer-events:none}
 .mobile-menu-open .sidebar-rail{transform:none;visibility:visible;pointer-events:auto}.sidebar{width:100%;padding-bottom:calc(var(--hrm-space-5) + env(safe-area-inset-bottom))}
 .sidebar-handle{display:none}.menu-button{display:inline-flex}.topbar-title{display:none}
 .mobile-close{display:grid;place-items:center;position:absolute;right:var(--hrm-space-4);top:var(--hrm-space-5);width:var(--hrm-control-md);height:var(--hrm-control-md);border:var(--hrm-border);border-radius:var(--hrm-radius-md);background:var(--hrm-surface);color:var(--hrm-text)}
 .menu-backdrop{display:block;position:fixed;inset:0;border:0;z-index:calc(var(--hrm-z-drawer) - 1);background:color-mix(in srgb,var(--hrm-text) 35%,transparent)}
 .brand{padding-right:var(--hrm-space-7)}.page-content{padding:var(--hrm-space-4)}.topbar{padding-inline:var(--hrm-space-4)}
}
@media(max-width:767px){.account-name,.profile-trigger-arrow,.logout-label{display:none}.account-actions{gap:var(--hrm-space-1)}.topbar{gap:var(--hrm-space-2)}.profile-trigger{padding:0}.profile-avatar{width:var(--hrm-control-md);height:var(--hrm-control-md)}.logout-trigger{width:var(--hrm-control-md);padding:0}.menu-button span{display:none}}
@media(prefers-reduced-motion:reduce){.sidebar-rail{transition:none}}
`;
write(file,ast.toString()+shell);
// Use internal SVG for the visible shell utilities; permission/auth methods remain untouched.
let ts=read('frontend/src/app/layout/admin-layout.ts');ts="import {HrmIcon} from '../shared/ui/icon';\n"+ts.replace('imports: [DatePipe','imports: [HrmIcon, DatePipe');write('frontend/src/app/layout/admin-layout.ts',ts);
let html=read('frontend/src/app/layout/admin-layout.html').replace('@if (darkMode) { <span aria-hidden="true">☀</span> } @else { <span aria-hidden="true">☾</span> }','<hrm-icon [name]="darkMode?\'sun\':\'moon\'"/>');write('frontend/src/app/layout/admin-layout.html',html);
// Login uses the same control scale; recovery API and form behavior remain unchanged.
let login=read('frontend/src/app/pages/login/login.html').replaceAll('class="submit-button"','class="submit-button" hrmBtn="primary" hrmSize="lg"');write('frontend/src/app/pages/login/login.html',login);
let lt=read('frontend/src/app/pages/login/login.ts');lt="import {HrmButton} from '../../shared/ui/button';\n"+lt.replace('standalone: true,','standalone: true,\n  imports:[HrmButton],');write('frontend/src/app/pages/login/login.ts',lt);
console.log('Shell styles deduplicated; login control styling migrated.');
