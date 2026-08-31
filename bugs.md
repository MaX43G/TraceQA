# DeepSec Security Report

Findings: **12**
critical: 2 | high: 10 | medium: 0 | low: 0 | info: 0

## [CRITICAL] Sensitive value is assigned a literal.

- Location: `D:\MyProjects\TraceQA\TraceQA\traceqa-user-service\src\main\java\edu\zjut\traceqa\userservice\config\DataInitializer.java:26`
- Rule: `hardcoded_secret_assignment`
- Layer: `L1`

Sensitive value is assigned a literal.

```text
DEFAULT_ADMIN_PASSWORD = admi...3456
```

Remediation: Read this value from a secret manager or environment variable.

## [CRITICAL] Sensitive value is assigned a literal.

- Location: `D:\MyProjects\TraceQA\TraceQA\traceqa-user-service\src\main\java\edu\zjut\traceqa\userservice\config\DataInitializer.java:27`
- Rule: `hardcoded_secret_assignment`
- Layer: `L1`

Sensitive value is assigned a literal.

```text
DEFAULT_USER_PASSWORD = user...3456
```

Remediation: Read this value from a secret manager or environment variable.

## [HIGH] HTML is assigned directly to the DOM.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\C-3T_078.js:2`
- Rule: `sast_xss_inner_html`
- Layer: `L2`

HTML is assigned directly to the DOM.

```text
.innerHTML=t.props.children,delete t.props.children),t.props.hid&&(t.key=t.props.hid,delete t.props.hid),t.props.vmid&&(t.key=t.props.vmid,delete t.props.vmid),`body`in t.props&&(t.props.body&&(t.tagPosition=`bodyClose`),delete t.props.body),t.props.renderPriority!=null&&(t.tagPriority=t.props.renderPriority,delete t.props.renderPriority)}}})
```

Remediation: Use textContent or sanitize trusted HTML.

## [HIGH] HTML is assigned directly to the DOM.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\CaehEYC_.js:36`
- Rule: `sast_xss_inner_html`
- Layer: `L2`

HTML is assigned directly to the DOM.

```text
.innerHTML=g,e.connectedBackgroundColor&&h.painter.setBackgroundColor(e.connectedBackgroundColor),h.refreshImmediately(),h.painter.toDataURL()}return e.connectedBackgroundColor&&h.add(new nh({shape:{x:0,y:0,width:f,height:p},style:{fill:e.connectedBackgroundColor}})),z(u,function(e){var t=new Jm({style:{x:e.left*d-o,y:e.top*d-s,image:e.dom}})
```

Remediation: Use textContent or sanitize trusted HTML.

## [HIGH] HTML is assigned directly to the DOM.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\CaehEYC_.js:38`
- Rule: `sast_xss_inner_html`
- Layer: `L2`

HTML is assigned directly to the DOM.

```text
.innerHTML=null),this._oldVNode=null},e.prototype.toDataURL=function(e){var t=this.renderToString(),n=`data:image/svg+xml
```

Remediation: Use textContent or sanitize trusted HTML.

## [HIGH] HTML is assigned directly to the DOM.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\CaehEYC_.js:57`
- Rule: `sast_xss_inner_html`
- Layer: `L2`

HTML is assigned directly to the DOM.

```text
.innerHTML=o[0]||r.get(`title`),a.style.cssText=`margin:10px 20px`,a.style.color=r.get(`textColor`)
```

Remediation: Use textContent or sanitize trusted HTML.

## [HIGH] document.write() can introduce XSS.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\CaehEYC_.js:40`
- Rule: `sast_xss_document_write`
- Layer: `L2`

document.write() can introduce XSS.

```text
document.write(
```

Remediation: Create DOM nodes safely or sanitize first.

## [HIGH] HTML is assigned directly to the DOM.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\CqWpMFBX.js:1`
- Rule: `sast_xss_inner_html`
- Layer: `L2`

HTML is assigned directly to the DOM.

```text
.innerHTML==`object`&&(n.innerHTML=JSON.stringify(n.innerHTML),n.props.type=n.props.type||`application/json`),Array.isArray(n.props.content)){let e=[]
```

Remediation: Use textContent or sanitize trusted HTML.

## [HIGH] HTML is assigned directly to the DOM.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\CxKUF2LA.js:19`
- Rule: `sast_xss_inner_html`
- Layer: `L2`

HTML is assigned directly to the DOM.

```text
.innerHTML=i.value,e.dataset.highlighted=`yes`,C(e,n,i.language),e.result={language:i.language,re:i.relevance,relevance:i.relevance},i.secondBest&&(e.secondBest={language:i.secondBest.language,relevance:i.secondBest.relevance}),ae(`after:highlightElement`,{el:e,result:i,text:r})}function T(e){l=Ne(l,e)}let E=()=>{ee(),be(`10.6.0`,`initHighlighting() deprecated.  Use highlightAll() now.`)}
```

Remediation: Use textContent or sanitize trusted HTML.

## [HIGH] HTML is assigned directly to the DOM.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\C_IeK1sS.js:2`
- Rule: `sast_xss_inner_html`
- Layer: `L2`

HTML is assigned directly to the DOM.

```text
.innerHTML=t.html,Ue(e,z)})},preview:function(){var e=this.imageData,t=this.canvasData,n=this.cropBoxData,r=n.width,i=n.height,a=e.width,o=e.height,s=n.left-t.left-e.left,c=n.top-t.top-e.top
```

Remediation: Use textContent or sanitize trusted HTML.

## [HIGH] HTML is assigned directly to the DOM.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\DUjqwEwH.js:3`
- Rule: `sast_xss_inner_html`
- Layer: `L2`

HTML is assigned directly to the DOM.

```text
.innerHTML==null||m.textContent&&h.textContent==null)&&p(l,``),d?te(e.dynamicChildren,d,l,n,r,Ja(t,i),o):s||le(e,t,l,null,n,r,Ja(t,i),o,!1),u>0){if(u&16)ne(l,m,h,n,i)
```

Remediation: Use textContent or sanitize trusted HTML.

## [HIGH] HTML is assigned directly to the DOM.

- Location: `D:\MyProjects\TraceQA\TraceQA\frontend\.output\public\_nuxt\DUjqwEwH.js:44`
- Rule: `sast_xss_inner_html`
- Layer: `L2`

HTML is assigned directly to the DOM.

```text
.innerHTML=e
```

Remediation: Use textContent or sanitize trusted HTML.
