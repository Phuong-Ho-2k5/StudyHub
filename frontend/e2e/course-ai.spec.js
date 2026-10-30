import { test, expect } from '@playwright/test'
import { Buffer } from 'node:buffer'

const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url')
const token = `${encode({ alg: 'HS256' })}.${encode({ sub: 'student@example.test', exp: Math.floor(Date.now() / 1000) + 3600 })}.test`
const documentRow = { id: 9, courseId: 7, title: 'Database PDF', fileName: 'db.pdf', fileType: 'PDF', status: 'READY', managedFile: true, ingestionStatus: 'INDEXED', publishedIngestionId: 'v1' }
const source = { courseId: 7, documentId: 9, chunkId: 12, chunkIndex: 0, documentTitle: 'Database PDF', content: 'Before. ACID protects transactions. After.', pageStart: 2, pageEnd: 3, fileUrl: '/api/documents/9/file' }
const hit = { ...source, score: 0.8 }
const citation = { ...source, quote: 'ACID protects transactions.', sourceUrl: '/api/courses/7/documents/9/chunks/12/source' }
const answered = { courseId: 7, question: 'ACID?', status: 'ANSWERED', answer: 'ACID protects transactions.', citations: [citation] }

test.beforeEach(async ({ page }) => {
  await page.addInitScript(value => localStorage.setItem('studyhub_access_token', value), token)
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    if (/\/courses\/\d+$/.test(path)) return route.fulfill({ json: { id: Number(path.split('/').at(-1)), name: 'Database course' } })
    if (path === '/api/documents') return route.fulfill({ json: { content: [documentRow], totalPages: 1 } })
    if (path === '/api/documents/9') return route.fulfill({ json: documentRow })
    if (path.endsWith('/progress')) return route.fulfill({ json: { progressPercentage: 0, totalConcepts: 0, masteryPercentage: 0 } })
    if (path.endsWith('/active')) return route.fulfill({ status: 204 })
    if (path.endsWith('/search')) return route.fulfill({ json: { courseId: 7, query: 'ACID', items: [hit] } })
    if (path.endsWith('/ai/chat')) return route.fulfill({ json: answered })
    if (path.endsWith('/source')) return route.fulfill({ json: source })
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { unreadCount: 0 } })
    if (path.includes('/admin')) return route.fulfill({ status: 403, json: { message: 'Forbidden' } })
    return route.fulfill({ json: [] })
  })
})

async function search(page) {
  await page.goto('/courses/7?tab=ai')
  await page.getByRole('textbox', { name: 'Tìm trong tài liệu' }).fill('ACID')
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click()
}

async function chat(page) {
  await page.goto('/courses/7?tab=ai')
  await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
  await page.getByRole('textbox', { name: 'Câu hỏi' }).fill('ACID?')
  await page.getByRole('button', { name: 'Gửi câu hỏi' }).click()
}

test('search sends scoped payload and opens fresh source with nullable metadata', async ({ page }) => {
  let request
  await page.route('**/api/courses/7/search', route => { request = route.request(); return route.fulfill({ json: { courseId: 7, query: 'ACID', items: [hit] } }) })
  await page.route('**/source', route => route.fulfill({ json: { ...source, fileUrl: null, pageStart: null, pageEnd: null } }))
  await search(page)
  await page.getByRole('button', { name: 'Xem nguồn' }).click()
  await expect(page.getByRole('dialog')).toContainText(source.content)
  await expect(page.getByRole('dialog')).toContainText('Chưa xác định trang')
  await expect(page.getByRole('button', { name: 'Xem PDF' })).toHaveCount(0)
  expect(request.postDataJSON()).toEqual({ query: 'ACID', topK: 5 })
  expect(request.headers().authorization).toBe(`Bearer ${token}`)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Xem nguồn' })).toBeFocused()
})

test('chat renders citations and highlights exact quote without rerunning generation', async ({ page }) => {
  let calls = 0
  await page.route('**/ai/chat', route => { calls++; return route.fulfill({ json: answered }) })
  await chat(page)
  await page.getByRole('button', { name: 'Xem nguồn' }).click()
  await expect(page.getByRole('dialog').locator('mark')).toHaveText(citation.quote)
  expect(calls).toBe(1)
})

test('empty search, token limit and insufficient context have distinct states', async ({ page }) => {
  await page.route('**/search', route => route.fulfill({ json: { courseId: 7, query: 'ACID', items: [] } }))
  await search(page)
  await expect(page.getByText('Không tìm thấy đoạn phù hợp.')).toBeVisible()
  await page.route('**/search', route => route.fulfill({ status: 422, json: {} }))
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('rút ngắn')
  await page.route('**/ai/chat', route => route.fulfill({ json: { ...answered, status: 'INSUFFICIENT_CONTEXT', answer: 'Không đủ thông tin trong tài liệu.', citations: [] } }))
  await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
  await page.getByRole('textbox', { name: 'Câu hỏi' }).fill('Unknown?')
  await page.getByRole('button', { name: 'Gửi câu hỏi' }).click()
  await expect(page.getByText('Không đủ thông tin trong tài liệu.', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xem nguồn' })).toHaveCount(0)
})

test('stale source never loads PDF and does not erase the answer', async ({ page }) => {
  let pdfCalls = 0
  await page.route('**/source', route => route.fulfill({ status: 409, json: {} }))
  await page.route('**/file', route => { pdfCalls++; return route.fulfill({ status: 404 }) })
  await chat(page)
  await page.getByRole('button', { name: 'Xem nguồn' }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('đã thay đổi')
  expect(pdfCalls).toBe(0)
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('chat-answer')).toContainText(answered.answer)
})

test('failed rebuild retains published availability and only one ingest is submitted', async ({ page }) => {
  const failed = { ...documentRow, ingestionStatus: 'FAILED', ingestionError: 'provider unavailable' }
  await page.route('**/api/documents?*', route => route.fulfill({ json: { content: [failed], totalPages: 1 } }))
  let release
  const barrier = new Promise(resolve => { release = resolve })
  let calls = 0
  await page.route('**/ingest', async route => { calls++; await barrier; await route.fulfill({ json: {} }) })
  await page.goto('/courses/7')
  await expect(page.getByText('Đã có bản dùng cho AI', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Thử lại', exact: true }).evaluate(button => { button.click(); button.click() })
  await expect(page.getByRole('button', { name: 'Đang xử lý…' })).toBeDisabled()
  expect(calls).toBe(1)
  release()
  await expect(page.getByText('Đã xử lý', { exact: true })).toBeVisible()
})

function pdfFixture() {
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R 5 0 R 7 0 R] /Count 3 >>']
  for (const [index, color] of ['1 0 0', '0 1 0', '0 0 1'].entries()) {
    const stream = `${color} rg 0 0 120 120 re f`
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 120 120] /Resources << >> /Contents ${4 + index * 2} 0 R >>`)
    objects.push(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`)
  }
  let body = '%PDF-1.4\n'
  const offsets = [0]
  objects.forEach((object, i) => { offsets.push(Buffer.byteLength(body)); body += `${i + 1} 0 obj\n${object}\nendobj\n` })
  const xref = Buffer.byteLength(body)
  body += `xref\n0 ${offsets.length}\n0000000000 65535 f \n`
  for (const offset of offsets.slice(1)) body += `${String(offset).padStart(10, '0')} 00000 n \n`
  return Buffer.from(`${body}trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`)
}

async function trackBlobs(page) {
  await page.addInitScript(() => {
    window.blobLifecycle = { created: [], revoked: [] }
    const create = URL.createObjectURL.bind(URL)
    const revoke = URL.revokeObjectURL.bind(URL)
    URL.createObjectURL = blob => { const url = create(blob); window.blobLifecycle.created.push(url); return url }
    URL.revokeObjectURL = url => { window.blobLifecycle.revoked.push(url); revoke(url) }
  })
}

test('PDF canvas actually renders the cited second page and revokes its authenticated Blob', async ({ page }, testInfo) => {
  await trackBlobs(page)
  let authorization
  await page.route('**/api/documents/9/file', route => { authorization = route.request().headers().authorization; return route.fulfill({ contentType: 'application/pdf', body: pdfFixture() }) })
  await search(page)
  await page.getByRole('button', { name: 'Xem nguồn' }).click()
  await page.getByRole('button', { name: 'Xem PDF', exact: true }).click()
  const canvas = page.locator('canvas[data-rendered-page="2"]')
  await expect(canvas).toBeVisible()
  expect(await canvas.evaluate(node => [...node.getContext('2d').getImageData(5, 5, 1, 1).data])).toEqual([0, 255, 0, 255])
  expect(authorization).toBe(`Bearer ${token}`)
  await page.screenshot({ path: testInfo.outputPath('source-viewer.png') })
  await page.getByRole('button', { name: 'Trang sau' }).click()
  await expect(page.locator('canvas[data-rendered-page="3"]')).toBeVisible()
  await page.keyboard.press('Escape')
  expect(await page.evaluate(() => window.blobLifecycle.created.every(url => window.blobLifecycle.revoked.includes(url)))).toBe(true)
})

test('closing a source while its PDF is delayed never creates a stale Blob URL', async ({ page }) => {
  await trackBlobs(page)
  let release
  const barrier = new Promise(resolve => { release = resolve })
  let reached
  const requested = new Promise(resolve => { reached = resolve })
  await page.route('**/api/documents/9/file', async route => { reached(); await barrier; await route.fulfill({ contentType: 'application/pdf', body: pdfFixture() }).catch(() => {}) })
  await search(page)
  await page.getByRole('button', { name: 'Xem nguồn' }).click()
  await page.getByRole('button', { name: 'Xem PDF', exact: true }).click()
  await requested
  await page.keyboard.press('Escape')
  release()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(await page.evaluate(() => window.blobLifecycle.created)).toEqual([])
})

for (const status of [503, 504]) {
  test(`chat ${status} is an error and is never silently retried`, async ({ page }) => {
    let calls = 0
    await page.route('**/ai/chat', route => { calls++; return route.fulfill({ status, json: {} }) })
    await chat(page)
    await expect(page.getByRole('alert')).toContainText(status === 503 ? 'chưa sẵn sàng' : 'Quá thời gian')
    expect(calls).toBe(1)
    await expect(page.getByTestId('chat-answer')).toHaveCount(0)
    await expect(page.getByRole('textbox', { name: 'Câu hỏi' })).toHaveValue('ACID?')
  })
}

test('chat rejects malformed evidence and renders model HTML as plain text', async ({ page }) => {
  await page.route('**/ai/chat', route => route.fulfill({ json: { ...answered, citations: [] } }))
  await chat(page)
  await expect(page.getByRole('alert')).toContainText('không hợp lệ')
  const html = '<img src=x onerror="window.injected=true">'
  await page.route('**/ai/chat', route => route.fulfill({ json: { ...answered, answer: html } }))
  await page.getByRole('button', { name: 'Gửi câu hỏi' }).click()
  await expect(page.getByTestId('chat-answer')).toHaveText(html)
  expect(await page.evaluate(() => window.injected)).toBeUndefined()
  await expect(page.getByTestId('chat-answer').locator('img')).toHaveCount(0)
})

test('double submit is locked and a late answer cannot cross Course boundaries', async ({ page }) => {
  let release
  const barrier = new Promise(resolve => { release = resolve })
  let calls = 0
  await page.route('**/api/courses/7/ai/chat', async route => { calls++; await barrier; await route.fulfill({ json: answered }) })
  await page.goto('/courses/7?tab=ai')
  await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
  const input = page.getByRole('textbox', { name: 'Câu hỏi' })
  await input.fill('ACID?')
  const pending = page.waitForRequest('**/api/courses/7/ai/chat')
  await input.evaluate(node => { node.form.requestSubmit(); node.form.requestSubmit() })
  await pending
  expect(calls).toBe(1)
  await page.evaluate(() => { history.pushState({}, '', '/courses/8?tab=ai'); dispatchEvent(new PopStateEvent('popstate')) })
  await expect(page.getByRole('textbox', { name: 'Tìm trong tài liệu' })).toBeVisible()
  const response = page.waitForResponse('**/api/courses/7/ai/chat')
  release()
  await response
  await expect(page.getByTestId('chat-answer')).toHaveCount(0)
  await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'Câu hỏi' })).toHaveValue('')
})

test('a delayed source cannot replace a newly selected source', async ({ page }) => {
  let release
  const barrier = new Promise(resolve => { release = resolve })
  let reached
  const requested = new Promise(resolve => { reached = resolve })
  await page.route('**/chunks/12/source', async route => { reached(); await barrier; await route.fulfill({ json: source }) })
  await page.route('**/search', route => route.fulfill({ json: { courseId: 7, query: 'ACID', items: [hit, { ...hit, chunkId: 13 }] } }))
  await page.route('**/chunks/13/source', route => route.fulfill({ json: { ...source, chunkId: 13, content: 'NEW SOURCE', fileUrl: null } }))
  await search(page)
  await page.getByRole('button', { name: 'Xem nguồn' }).nth(0).click()
  await requested
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'Xem nguồn' }).nth(1).click()
  await expect(page.getByRole('dialog')).toContainText('NEW SOURCE')
  const response = page.waitForResponse('**/chunks/12/source')
  release(); await response
  await expect(page.getByRole('dialog')).toContainText('NEW SOURCE')
  await expect(page.getByRole('dialog')).not.toContainText(source.content)
})

test('polling stops at INDEXED and does not poll documents on AI tab', async ({ page }) => {
  await page.clock.install()
  await page.route('**/api/documents?*', route => route.fulfill({ json: { content: [{ ...documentRow, ingestionStatus: 'PENDING', publishedIngestionId: null }], totalPages: 1 } }))
  let polls = 0
  await page.route('**/api/documents/9', route => { polls++; return route.fulfill({ json: documentRow }) })
  await page.goto('/courses/7')
  await expect(page.getByText('Đang chờ xử lý', { exact: true })).toBeVisible()
  await page.clock.fastForward(3100)
  await expect(page.getByText('Đã xử lý', { exact: true })).toBeVisible()
  await page.clock.fastForward(10000)
  expect(polls).toBe(1)
  await page.getByRole('tab', { name: 'Trợ lý AI' }).click()
  await page.clock.fastForward(10000)
  expect(polls).toBe(1)
})

test('polling two documents never overlaps a slow second request when the first finishes', async ({ page }) => {
  await page.clock.install()
  const second = { ...documentRow, id: 10, title: 'Second PDF' }
  await page.route('**/api/documents?*', route => route.fulfill({ json: { content: [documentRow, second].map(row => ({ ...row, ingestionStatus: 'PENDING' })), totalPages: 1 } }))
  let calls = 0
  let release
  const barrier = new Promise(resolve => { release = resolve })
  await page.route('**/api/documents/10', async route => { calls++; await barrier; await route.fulfill({ json: second }) })
  await page.goto('/courses/7')
  await expect(page.getByText('Đang chờ xử lý', { exact: true })).toHaveCount(2)
  await page.clock.fastForward(3100)
  await expect.poll(() => calls).toBe(1)
  await page.clock.runFor(100)
  await page.clock.fastForward(3100)
  await page.clock.runFor(100)
  expect(calls).toBe(1)
  release()
  await expect(page.getByText('Đã xử lý', { exact: true })).toHaveCount(2)
})

test('pending polling pauses when hidden, stops on errors and supports manual refresh', async ({ page }) => {
  await page.clock.install()
  await page.route('**/api/documents?*', route => route.fulfill({ json: { content: [{ ...documentRow, ingestionStatus: 'PROCESSING' }], totalPages: 1 } }))
  let polls = 0
  await page.route('**/api/documents/9', route => { polls++; return route.fulfill({ status: 503, json: {} }) })
  await page.goto('/courses/7')
  await expect(page.getByText('Đang xử lý', { exact: true })).toBeVisible()
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' }); document.dispatchEvent(new Event('visibilitychange')) })
  await page.clock.fastForward(10000)
  expect(polls).toBe(0)
  await page.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' }); document.dispatchEvent(new Event('visibilitychange')) })
  await expect(page.getByRole('button', { name: 'Cập nhật trạng thái' })).toBeVisible()
  await page.clock.fastForward(10000)
  expect(polls).toBe(1)
  await page.route('**/api/documents/9', route => { polls++; return route.fulfill({ json: documentRow }) })
  await page.getByRole('button', { name: 'Cập nhật trạng thái' }).click()
  await page.clock.runFor(100)
  await expect(page.getByText('Đã xử lý', { exact: true })).toBeVisible()
  expect(polls).toBe(2)
})

test('leaving the documents tab cancels a pending poll and discards its response', async ({ page }) => {
  await page.clock.install()
  await page.route('**/api/documents?*', route => route.fulfill({ json: { content: [{ ...documentRow, ingestionStatus: 'PROCESSING' }], totalPages: 1 } }))
  let release
  const barrier = new Promise(resolve => { release = resolve })
  let polls = 0
  await page.route('**/api/documents/9', async route => { polls++; await barrier; await route.fulfill({ json: documentRow }).catch(() => {}) })
  await page.goto('/courses/7')
  await expect(page.getByText('Đang xử lý', { exact: true })).toBeVisible()
  await page.clock.fastForward(3100)
  await expect.poll(() => polls).toBe(1)
  await page.getByRole('tab', { name: 'Trợ lý AI' }).click()
  release()
  await page.clock.fastForward(10000)
  expect(polls).toBe(1)
  await expect(page.getByRole('textbox', { name: 'Tìm trong tài liệu' })).toBeVisible()
  await expect(page.getByText('Đã xử lý', { exact: true })).toHaveCount(0)
})

test('logout during a pending chat prevents late answer disclosure', async ({ page }) => {
  let release
  const barrier = new Promise(resolve => { release = resolve })
  let reached
  const requested = new Promise(resolve => { reached = resolve })
  await page.route('**/ai/chat', async route => { reached(); await barrier; await route.fulfill({ json: answered }) })
  await chat(page)
  await requested
  await page.getByRole('button', { name: 'Đăng xuất' }).click()
  await expect(page.getByRole('textbox', { name: 'Email' })).toBeVisible()
  const response = page.waitForResponse('**/ai/chat')
  release(); await response
  await expect(page.getByTestId('chat-answer')).toHaveCount(0)
  expect(await page.evaluate(() => localStorage.getItem('studyhub_access_token'))).toBeNull()
})

test('PDF upload uses existing automatic ingestion and never sends another ingest POST', async ({ page }) => {
  let uploaded = false
  let ingestCalls = 0
  await page.route('**/api/documents?*', route => route.fulfill({ json: { content: uploaded ? [{ ...documentRow, ingestionStatus: 'PENDING', publishedIngestionId: null }] : [], totalPages: 1 } }))
  await page.route('**/upload', route => { uploaded = true; return route.fulfill({ status: 201, json: { ...documentRow, ingestionStatus: 'PENDING' } }) })
  await page.route('**/ingest', route => { ingestCalls++; return route.fulfill({ json: {} }) })
  await page.goto('/courses/7')
  await page.getByRole('button', { name: '+ Thêm tài liệu' }).click()
  await page.getByLabel('Tệp PDF').setInputFiles({ name: 'test.pdf', mimeType: 'application/pdf', buffer: pdfFixture() })
  await page.getByRole('button', { name: 'Tải lên', exact: true }).click()
  await expect(page.getByText('Đang chờ xử lý', { exact: true })).toBeVisible()
  expect(ingestCalls).toBe(0)
})

test('source viewer fits a mobile screen and keeps keyboard focus inside the modal', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await search(page)
  await page.getByRole('button', { name: 'Xem nguồn' }).click()
  await expect(page.getByRole('dialog')).toContainText(source.content)
  await page.keyboard.press('Shift+Tab')
  expect(await page.evaluate(() => document.querySelector('dialog').contains(document.activeElement))).toBe(true)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('mobile-source.png') })
})

for (const endpoint of ['source', 'file']) {
  test(`${endpoint} 401 expires the session through the existing auth flow`, async ({ page }) => {
    await page.route(`**/${endpoint}`, route => route.fulfill({ status: 401, json: {} }))
    await search(page)
    await page.getByRole('button', { name: 'Xem nguồn' }).click()
    if (endpoint === 'file') await page.getByRole('button', { name: 'Xem PDF', exact: true }).click()
    await expect(page.getByRole('textbox', { name: 'Email' })).toBeVisible()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(await page.evaluate(() => localStorage.getItem('studyhub_access_token'))).toBeNull()
  })
}

test('late search cannot populate another Course after navigation', async ({ page }) => {
  let release
  const barrier = new Promise(resolve => { release = resolve })
  let reached
  const requested = new Promise(resolve => { reached = resolve })
  await page.route('**/api/courses/7/search', async route => { reached(); await barrier; await route.fulfill({ json: { courseId: 7, query: 'old query', items: [hit] } }) })
  await search(page)
  await requested
  await page.evaluate(() => { history.pushState({}, '', '/courses/8?tab=ai'); dispatchEvent(new PopStateEvent('popstate')) })
  await expect(page.getByRole('textbox', { name: 'Tìm trong tài liệu' })).toHaveValue('')
  const response = page.waitForResponse('**/api/courses/7/search')
  release(); await response
  await expect(page.getByRole('button', { name: 'Xem nguồn' })).toHaveCount(0)
})

test('pagination ignores a late polling response from the previous documents page', async ({ page }) => {
  await page.clock.install()
  const second = { ...documentRow, id: 10, title: 'PAGE TWO' }
  await page.route('**/api/documents?*', route => {
    const pageNumber = new URL(route.request().url()).searchParams.get('page')
    return route.fulfill({ json: { content: pageNumber === '1' ? [second] : [{ ...documentRow, ingestionStatus: 'PENDING' }], totalPages: 2 } })
  })
  let release
  const barrier = new Promise(resolve => { release = resolve })
  let reached
  const requested = new Promise(resolve => { reached = resolve })
  await page.route('**/api/documents/9', async route => { reached(); await barrier; await route.fulfill({ json: { ...documentRow, title: 'STALE PAGE ONE' } }).catch(() => {}) })
  await page.goto('/courses/7')
  await expect(page.getByText('Đang chờ xử lý', { exact: true })).toBeVisible()
  await page.clock.fastForward(3100)
  await requested
  await page.getByRole('button', { name: 'Sau', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'PAGE TWO' })).toBeVisible()
  release()
  await page.clock.runFor(100)
  await expect(page.getByRole('heading', { name: 'PAGE TWO' })).toBeVisible()
  await expect(page.getByText('STALE PAGE ONE')).toHaveCount(0)
})
