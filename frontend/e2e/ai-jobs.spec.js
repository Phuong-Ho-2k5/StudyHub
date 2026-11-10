import { test, expect } from '@playwright/test'
import { Buffer } from 'node:buffer'
const encode = v => Buffer.from(JSON.stringify(v)).toString('base64url')
const token = `${encode({alg:'HS256'})}.${encode({sub:'jobs@test.local',exp:Math.floor(Date.now()/1000)+3600})}.test`
const id = '5d0b41ee-9f4d-42ec-94ed-216d29cbdbca'
async function fixture(page, status = 'PENDING') {
 const state = {status, posts:0, reads:0, readError:0}
 await page.addInitScript(value=>localStorage.setItem('studyhub_access_token',value),token)
 await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
  const path=new URL(route.request().url()).pathname
  if(path===`/api/ai/jobs/${id}`){state.reads++;return route.fulfill({status:state.readError||200,json:state.readError?{message:'Read failed'}:{id,type:'GENERATE_QUIZ',status:state.status,courseId:7,documentId:9,result:state.status==='COMPLETED'?{type:'QUIZ',id:'4'}:null,errorCode:state.status==='FAILED'?'AI_UNAVAILABLE':null}})}
  if(path.endsWith('/ai/generate-quiz')) {state.posts++;return route.fulfill({status:202,json:{jobId:id,statusUrl:`/api/ai/jobs/${id}`}})}
  if(path==='/api/quizzes/4')return route.fulfill({json:{id:4,courseId:7,title:'Draft',status:'DRAFT'}})
  if(path==='/api/courses/7')return route.fulfill({json:{id:7,name:'Database course'}})
  if(path==='/api/documents')return route.fulfill({json:{content:[{id:9,courseId:7,title:'Database PDF',fileName:'db.pdf',managedFile:true,status:'READY',ingestionStatus:'INDEXED',publishedIngestionId:'v1'}],totalPages:1}})
  if(path.endsWith('/progress'))return route.fulfill({json:{progressPercentage:0,totalConcepts:0}})
  if(path.endsWith('/active'))return route.fulfill({status:204})
  if(path.endsWith('/unread-count'))return route.fulfill({json:{unreadCount:0}})
  return route.fulfill({json:[]})
 })
 return state
}
test('submit returns job, follows all states, opens quiz and resumes after refresh',async({page})=>{
 const state=await fixture(page)
 await page.goto('/courses/7')
 await page.getByRole('button',{name:'Sinh Quiz bằng AI',exact:true}).click()
 await page.getByRole('button',{name:'Bắt đầu',exact:true}).click()
 await expect(page.getByText('Đang chờ xử lý',{exact:true})).toBeVisible()
 expect(state.posts).toBe(1);await expect(page).toHaveURL(/job=/)
 state.status='PROCESSING';await expect(page.getByText('AI đang xử lý',{exact:true})).toBeVisible({timeout:6000})
 state.status='COMPLETED';await expect(page.getByRole('link',{name:'Mở Quiz để duyệt'})).toHaveAttribute('href','/courses/7/quizzes/4',{timeout:6000})
 await page.reload();await expect(page.getByRole('link',{name:'Mở Quiz để duyệt'})).toBeVisible();expect(state.posts).toBe(1)
 const reads=state.reads;await page.waitForTimeout(3300);expect(state.reads).toBe(reads)
})
test('read errors can refresh status without another POST; failed job shows safe error',async({page})=>{
 const state=await fixture(page);state.readError=503
 await page.goto(`/courses/7?job=${id}`)
 await expect(page.getByRole('button',{name:'Cập nhật trạng thái',exact:true})).toBeVisible()
 state.readError=0;state.status='FAILED'
 await page.getByRole('button',{name:'Cập nhật trạng thái',exact:true}).click()
 await expect(page.getByText('Dịch vụ AI hiện không sẵn sàng.')).toBeVisible()
 expect(state.posts).toBe(0)
})
test('foreign course job is never rendered as a usable result',async({page})=>{
 await fixture(page,'COMPLETED')
 await page.route(`**/api/ai/jobs/${id}`,route=>route.fulfill({json:{id,type:'GENERATE_QUIZ',status:'COMPLETED',courseId:8,result:{type:'QUIZ',id:'4'}}}))
 await page.goto(`/courses/7?job=${id}`)
 await expect(page.getByRole('link',{name:'Mở Quiz để duyệt'})).toHaveCount(0)
 await expect(page.getByText('Không thể truy cập tác vụ này.')).toBeVisible()
})
test('concept result keeps confirmation explicit and renders evidence',async({page})=>{
 const state=await fixture(page,'COMPLETED');let confirmations=0
 await page.route(`**/api/ai/jobs/${id}`,route=>route.fulfill({json:{id,type:'EXTRACT_CONCEPTS',status:'COMPLETED',courseId:7,documentId:9,result:{type:'CONCEPT_SUGGESTION_BATCH',id:'batch-1'}}}))
 await page.route('**/concept-suggestions/batch-1',route=>route.fulfill({json:{id:'batch-1',courseId:7,documentId:9,sourceCurrent:true,suggestions:[{id:11,name:'Atomicity',description:'All or nothing',state:'PENDING',citations:[{chunkId:12,documentId:9,documentTitle:'Database PDF',quote:'All or nothing',pageStart:1,pageEnd:1}]}]}}))
 await page.route('**/items/11/confirm',route=>{confirmations++;expect(route.request().postDataJSON()).toEqual({name:'Atomicity',description:'All or nothing'});return route.fulfill({status:201,json:{concept:{id:21}}})})
 await page.goto(`/courses/7?job=${id}`)
 await expect(page.getByRole('heading',{name:'Atomicity',exact:true})).toBeVisible()
 await expect(page.getByRole('blockquote')).toHaveText('All or nothing')
 expect(confirmations).toBe(0);expect(state.posts).toBe(0)
 await page.getByRole('button',{name:'Xác nhận thêm khái niệm'}).click()
 await expect(page.getByText('Đã thêm khái niệm',{exact:true})).toBeVisible();expect(confirmations).toBe(1)
})
test('completed ingestion can reload its document after a read failure without re-ingestion',async({page})=>{
 await fixture(page,'COMPLETED');let failed=true,posts=0
 const doc={id:9,courseId:7,title:'Database PDF',fileName:'db.pdf',managedFile:true,status:'READY',ingestionStatus:'PENDING',ingestionJobId:id,publishedIngestionId:null}
 await page.route('**/api/documents?**',route=>route.fulfill({json:{content:[doc],totalPages:1}}))
 await page.route(`**/api/ai/jobs/${id}`,route=>route.fulfill({json:{id,type:'INGEST_DOCUMENT',status:'COMPLETED',courseId:7,documentId:9,result:{type:'DOCUMENT_INDEX',id:'index-1'}}}))
 await page.route('**/api/documents/9',route=>route.fulfill({status:failed?503:200,json:failed?{}:{...doc,ingestionStatus:'INDEXED',publishedIngestionId:'index-1'}}))
 await page.route('**/api/documents/9/ingest',route=>{posts++;return route.fulfill({status:202,json:{jobId:id}})})
 await page.goto('/courses/7')
 await expect(page.getByRole('button',{name:'Tải lại tài liệu'})).toBeVisible()
 failed=false;await page.getByRole('button',{name:'Tải lại tài liệu'}).click()
 await expect(page.getByText('Đã có bản dùng cho AI',{exact:true})).toBeVisible();expect(posts).toBe(0)
})
test('a deleted result retries only its GET while preserving completed status',async({page})=>{
 const state=await fixture(page,'COMPLETED');let failed=true
 await page.route('**/api/quizzes/4',route=>route.fulfill({status:failed?404:200,json:failed?{}:{id:4,courseId:7,status:'DRAFT'}}))
 await page.goto(`/courses/7?job=${id}`)
 await expect(page.getByRole('button',{name:'Tải lại kết quả'})).toBeVisible()
 await expect(page.getByText('Đã hoàn tất',{exact:true})).toBeVisible()
 failed=false;await page.getByRole('button',{name:'Tải lại kết quả'}).click()
 await expect(page.getByRole('link',{name:'Mở Quiz để duyệt'})).toBeVisible();expect(state.posts).toBe(0)
 await page.screenshot({path:'../.superpowers/sdd/m8-async/ai-jobs-desktop.png',fullPage:true})
 await page.setViewportSize({width:390,height:844})
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true)
 await page.screenshot({path:'../.superpowers/sdd/m8-async/ai-jobs-mobile.png',fullPage:true})
})
test('a delayed generation acceptance cannot navigate a different Course',async({page})=>{
 await fixture(page);let release,started
 const entered=new Promise(resolve=>{started=resolve})
 await page.route('**/api/documents/9/ai/generate-quiz',async route=>{started();await new Promise(resolve=>{release=resolve});await route.fulfill({status:202,json:{jobId:id,statusUrl:`/api/ai/jobs/${id}`}})})
 await page.goto('/courses/7')
 await page.getByRole('button',{name:'Sinh Quiz bằng AI',exact:true}).click()
 await page.getByRole('button',{name:'Bắt đầu',exact:true}).click();await entered
 await page.evaluate(()=>{history.pushState({},'','/courses/8');window.dispatchEvent(new PopStateEvent('popstate'))})
 await expect(page).toHaveURL(/\/courses\/8$/)
 release();await page.waitForTimeout(500)
 await expect(page).toHaveURL(/\/courses\/8$/)
 await expect(page.getByRole('region',{name:'Tác vụ AI'})).toHaveCount(0)
})

test('malformed completed concept result is a retryable read error rather than a page crash',async({page})=>{
 await fixture(page,'COMPLETED');const crashes=[];page.on('pageerror',error=>crashes.push(error.message))
 await page.route(`**/api/ai/jobs/${id}`,route=>route.fulfill({json:{id,type:'EXTRACT_CONCEPTS',status:'COMPLETED',courseId:7,documentId:9,result:{type:'CONCEPT_SUGGESTION_BATCH',id:'batch-1'}}}))
 await page.route('**/concept-suggestions/batch-1',route=>route.fulfill({json:{id:'batch-1',courseId:7,documentId:9,sourceCurrent:true}}))
 await page.goto(`/courses/7?job=${id}`)
 await expect(page.getByRole('button',{name:'Tải lại kết quả'})).toBeVisible()
 expect(crashes).toEqual([])
})
