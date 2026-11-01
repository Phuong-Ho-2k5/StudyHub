import { test, expect } from '@playwright/test'
import { Buffer } from 'node:buffer'

const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url')
const token = `${encode({ alg: 'HS256' })}.${encode({ sub: 'history@example.test', exp: Math.floor(Date.now() / 1000) + 3600 })}.test`
const date = '2026-11-01T00:00:00Z'
const session = (id = 23, title = 'Ôn tập chương 1', courseId = 7) => ({ id, courseId, title, createdAt: date, updatedAt: date })
const citation = { documentId: 9, chunkId: 12, documentTitle: 'Database PDF', chunkIndex: 0, pageStart: null, pageEnd: null, quote: 'ACID protects transactions.', sourceUrl: '/api/courses/7/documents/9/chunks/12/source', fileUrl: null }
const user = (id = 1, sessionId = 23) => ({ id, sessionId, role: 'USER', content: `Question ${id}`, status: null, model: null, promptVersion: null, citations: [], createdAt: date })
const assistant = (id = 2, sessionId = 23) => ({ id, sessionId, role: 'ASSISTANT', content: `Answer ${id}`, status: 'ANSWERED', model: null, promptVersion: 'v1', citations: [citation], createdAt: date })
const paged = (content, number = 0, totalPages = 1, totalElements = content.length, size = 20) => ({ content, number, size, totalPages, totalElements, last: number >= totalPages - 1 })
const answer = { courseId: 7, question: 'ACID?', status: 'ANSWERED', answer: 'Saved answer', model: null, promptVersion: 'v1', citations: [citation], sessionId: 23, userMessageId: 101, assistantMessageId: 102 }

async function open(page) {
  await page.goto('/courses/7?tab=ai')
  await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
}
async function select(page, name = 'Ôn tập chương 1') {
  await page.getByRole('button', { name, exact: true }).click()
}
async function send(page) {
  await page.getByRole('textbox', { name: 'Câu hỏi', exact: true }).fill('ACID?')
  await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).click()
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(value => localStorage.setItem('studyhub_access_token', value), token)
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    if (/\/courses\/\d+$/.test(path)) return route.fulfill({ json: { id: Number(path.split('/').at(-1)), name: 'Database course' } })
    if (path.endsWith('/chat-sessions')) return route.fulfill({ json: paged([session()], 0, 1, 1, 10) })
    if (path.endsWith('/messages')) return route.fulfill({ json: paged([user(), assistant()]) })
    if (path.endsWith('/ai/chat')) return route.fulfill({ json: answer })
    if (path.endsWith('/source')) return route.fulfill({ status: 409, json: {} })
    if (path.endsWith('/progress')) return route.fulfill({ json: { progressPercentage: 0, totalConcepts: 0, masteryPercentage: 0 } })
    if (path.endsWith('/active')) return route.fulfill({ status: 204 })
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { unreadCount: 0 } })
    return route.fulfill({ json: [] })
  })
})

test('creates a trimmed title, reads saved citations after reload without generating', async ({ page }, testInfo) => {
  let created = false
  let posts = 0
  await page.route('**/chat-sessions?*', route => route.fulfill({ json: paged(created ? [session()] : [], 0, created ? 1 : 0, created ? 1 : 0, 10) }))
  await page.route('**/chat-sessions', route => {
    expect(route.request().postDataJSON()).toEqual({ title: 'Ôn tập chương 1' })
    created = true
    return route.fulfill({ status: 201, json: session() })
  })
  await page.route('**/ai/chat', route => { posts++; return route.fulfill({ json: answer }) })
  await open(page)
  await expect(page.getByText('Chưa có hội thoại nào.')).toBeVisible()
  await page.getByRole('button', { name: 'Hội thoại mới', exact: true }).click()
  await page.getByRole('textbox', { name: 'Tiêu đề hội thoại' }).fill('   ')
  await page.getByRole('button', { name: 'Tạo hội thoại', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('1–150')
  await page.getByRole('textbox', { name: 'Tiêu đề hội thoại' }).fill('x'.repeat(151))
  await page.getByRole('button', { name: 'Tạo hội thoại', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('1–150')
  await page.getByRole('textbox', { name: 'Tiêu đề hội thoại' }).fill('  Ôn tập chương 1  ')
  await page.getByRole('button', { name: 'Tạo hội thoại', exact: true }).click()
  await expect(page.getByTestId('chat-answer')).toHaveText('Answer 2')
  await page.reload()
  await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
  await select(page)
  await expect(page.getByText(citation.quote, { exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('history-desktop.png'), fullPage: true })
  expect(posts).toBe(0)
})

test('paginates sessions and messages in ascending order before enabling send', async ({ page }) => {
  await page.route('**/chat-sessions?*', route => {
    const n = Number(new URL(route.request().url()).searchParams.get('page'))
    return route.fulfill({ json: paged([session(n ? 24 : 23, n ? 'Trang hai' : 'Ôn tập chương 1')], n, 2, 11, 10) })
  })
  await page.route('**/messages?*', route => {
    const n = Number(new URL(route.request().url()).searchParams.get('page'))
    const messages = Array.from({ length: n ? 2 : 20 }, (_, i) => user(n * 20 + i + 1, 24))
    return route.fulfill({ json: paged(messages, n, 2, 22) })
  })
  await open(page)
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click()
  await select(page, 'Trang hai')
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeDisabled()
  await page.getByRole('button', { name: 'Tải thêm tin nhắn' }).click()
  await expect(page.getByTestId('chat-message')).toHaveCount(22)
  await expect(page.getByTestId('chat-message').first()).toContainText('Question 1')
  await expect(page.getByTestId('chat-message').last()).toContainText('Question 22')
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeEnabled()
})

test('late responses from an old session cannot overwrite the active transcript', async ({ page }) => {
  let release
  const barrier = new Promise(resolve => { release = resolve })
  await page.route('**/chat-sessions?*', route => route.fulfill({ json: paged([session(), session(24, 'Session B')], 0, 1, 2, 10) }))
  await page.route('**/23/messages?*', async route => { await barrier; await route.fulfill({ json: paged([user()]) }) })
  await page.route('**/24/messages?*', route => route.fulfill({ json: paged([user(50, 24)]) }))
  await open(page)
  await select(page)
  await select(page, 'Session B')
  await expect(page.getByTestId('chat-message')).toContainText('Question 50')
  const late = page.waitForResponse('**/23/messages?*')
  release()
  await (await late).finished()
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  await expect(page.getByTestId('chat-message')).toHaveCount(1)
  await expect(page.getByTestId('chat-message')).toContainText('Question 50')
})

test('creating a session locks the existing composer until creation finishes', async ({ page }) => {
  let release
  const barrier = new Promise(resolve => { release = resolve })
  await page.route('**/chat-sessions', async route => { await barrier; await route.fulfill({ status: 201, json: session(24, 'New session') }) })
  await open(page)
  await select(page)
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeEnabled()
  await page.getByRole('button', { name: 'Hội thoại mới', exact: true }).click()
  await page.getByRole('textbox', { name: 'Tiêu đề hội thoại' }).fill('New session')
  await page.getByRole('button', { name: 'Tạo hội thoại', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeDisabled()
  release()
  await expect(page.getByRole('region', { name: 'Hội thoại đang mở' })).toContainText('New session')
})

test('retrying a failed session page retries that page rather than the previous one', async ({ page }) => {
  let secondPageCalls = 0
  await page.route('**/chat-sessions?*', route => {
    const n = Number(new URL(route.request().url()).searchParams.get('page'))
    if (n && ++secondPageCalls === 1) return route.fulfill({ status: 503, json: {} })
    return route.fulfill({ json: paged([session(n ? 24 : 23, n ? 'Recovered page' : 'Ôn tập chương 1')], n, 2, 11, 10) })
  })
  await open(page)
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await page.getByRole('button', { name: 'Tải lại danh sách' }).click()
  await expect(page.getByRole('button', { name: 'Recovered page', exact: true })).toBeVisible()
})

test('sends exactly once into the session and retains the saved turn when list refresh fails', async ({ page }) => {
  let calls = 0
  let release
  const barrier = new Promise(resolve => { release = resolve })
  await page.route('**/ai/chat', async route => {
    calls++
    expect(route.request().postDataJSON()).toEqual({ question: 'ACID?', topK: 5, sessionId: 23 })
    expect(route.request().headers().authorization).toBe(`Bearer ${token}`)
    await barrier
    await route.fulfill({ json: answer })
  })
  await open(page)
  await select(page)
  await page.getByRole('textbox', { name: 'Câu hỏi', exact: true }).fill('ACID?')
  await page.getByRole('button', { name: 'Gửi câu hỏi' }).evaluate(button => { button.click(); button.click() })
  await expect(page.getByRole('button', { name: 'Đang trả lời…' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Hội thoại mới', exact: true })).toBeDisabled()
  await page.route('**/chat-sessions?*', route => route.fulfill({ status: 503, json: {} }))
  release()
  await expect(page.getByTestId('chat-answer').last()).toHaveText('Saved answer')
  await expect(page.getByRole('alert')).toBeVisible()
  expect(calls).toBe(1)
})

for (const status of [409, 422, 502, 503, 504]) {
  test(`chat ${status} preserves draft and never invents a saved message`, async ({ page }) => {
    await page.route('**/ai/chat', route => route.fulfill({ status, json: {} }))
    await open(page)
    await select(page)
    await send(page)
    await expect(page.getByRole('alert')).toBeVisible()
    if (status === 504) await expect(page.getByRole('alert')).not.toContainText('Bạn có thể gửi lại yêu cầu')
    await expect(page.getByTestId('chat-message')).toHaveCount(2)
    await expect(page.getByRole('textbox', { name: 'Câu hỏi', exact: true })).toHaveValue('ACID?')
  })
}

test('ambiguous network failure requires a history reload before another send', async ({ page }) => {
  let calls = 0
  await page.route('**/ai/chat', route => { calls++; return route.abort('failed') })
  await open(page)
  await select(page)
  await send(page)
  await expect(page.getByRole('alert')).toContainText('Chưa xác định')
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeDisabled()
  await page.getByRole('button', { name: 'Tải lại lịch sử' }).click()
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeEnabled()
  expect(calls).toBe(1)
})

test('insufficient context is a saved turn and expired sources leave history visible', async ({ page }) => {
  await page.route('**/ai/chat', route => route.fulfill({ json: { ...answer, status: 'INSUFFICIENT_CONTEXT', answer: 'Không đủ bằng chứng.', citations: [] } }))
  await open(page)
  await select(page)
  await page.getByRole('button', { name: 'Xem nguồn' }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('đã thay đổi')
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('chat-answer')).toHaveText('Answer 2')
  await send(page)
  await expect(page.getByText('Chưa đủ thông tin', { exact: true })).toBeVisible()
  await expect(page.getByTestId('chat-message')).toHaveCount(4)
})

test('mobile history wraps long titles and treats message HTML as text', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/chat-sessions?*', route => route.fulfill({ json: paged([session(23, 'Dài'.repeat(50))], 0, 1, 1, 10) }))
  await page.route('**/messages?*', route => route.fulfill({ json: paged([{ ...user(), content: '<script>window.bad = true</script>' }]) }))
  await open(page)
  await select(page, 'Dài'.repeat(50))
  await expect(page.getByTestId('chat-message')).toContainText('<script>window.bad = true</script>')
  expect(await page.evaluate(() => window.bad)).toBeUndefined()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('history-mobile.png'), fullPage: true })
})

test('failed message pagination retains earlier messages and retries the failed page', async ({ page }) => {
  let reads = 0
  await page.route('**/messages?*', route => {
    const n = Number(new URL(route.request().url()).searchParams.get('page'))
    if (n && ++reads === 1) return route.fulfill({ status: 503, json: {} })
    return route.fulfill({ json: paged(n ? [user(21), assistant(22)] : Array.from({ length: 20 }, (_, i) => user(i + 1)), n, 2, 22) })
  })
  await open(page)
  await select(page)
  await page.getByRole('button', { name: 'Tải thêm tin nhắn' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByTestId('chat-message')).toHaveCount(20)
  await page.getByRole('button', { name: 'Tải thêm tin nhắn' }).click()
  await expect(page.getByTestId('chat-message')).toHaveCount(22)
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeEnabled()
})

for (const status of [401, 404]) {
  test(`history ${status} preserves auth and scope boundaries`, async ({ page }) => {
    await page.route('**/messages?*', route => route.fulfill({ status, json: {} }))
    await open(page)
    await select(page)
    if (status === 401) await expect(page.getByRole('textbox', { name: 'Email' })).toBeVisible()
    else {
      await expect(page.getByRole('alert')).toContainText('không có quyền')
      await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeDisabled()
    }
    await expect(page.getByTestId('chat-message')).toHaveCount(0)
  })
}

test('foreign message response is rejected and deleted source does not erase snapshots', async ({ page }) => {
  await page.route('**/messages?*', route => route.fulfill({ json: paged([assistant(2, 24)]) }))
  await open(page)
  await select(page)
  await expect(page.getByRole('alert')).toContainText('không hợp lệ')
  await expect(page.getByTestId('chat-message')).toHaveCount(0)
  await page.route('**/messages?*', route => route.fulfill({ json: paged([user(), assistant()]) }))
  await page.getByRole('button', { name: 'Tải lại lịch sử' }).click()
  await page.route('**/source', route => route.fulfill({ status: 404, json: {} }))
  await page.getByRole('button', { name: 'Xem nguồn' }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('không có quyền')
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('chat-answer')).toHaveText('Answer 2')
  await expect(page.getByText(citation.quote, { exact: true })).toBeVisible()
})

test('ambiguous create never retries and requires a successful list refresh', async ({ page }) => {
  let calls = 0
  await page.route('**/chat-sessions', route => { calls++; return route.abort('failed') })
  await open(page)
  await page.getByRole('button', { name: 'Hội thoại mới', exact: true }).click()
  await page.getByRole('textbox', { name: 'Tiêu đề hội thoại' }).fill('New title')
  await page.getByRole('button', { name: 'Tạo hội thoại', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Chưa xác định')
  await expect(page.getByRole('button', { name: 'Tạo hội thoại', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Tải lại danh sách' }).click()
  await expect(page.getByRole('button', { name: 'Tạo hội thoại', exact: true })).toBeEnabled()
  expect(calls).toBe(1)
})

test('ambiguous create from an older list page recovers on the newest page', async ({ page }) => {
  let created = false
  await page.route('**/chat-sessions?*', route => {
    const n = Number(new URL(route.request().url()).searchParams.get('page'))
    return route.fulfill({ json: paged([n ? session(24, 'Old session') : created ? session(25, 'Possibly saved session') : session()], n, 2, 11, 10) })
  })
  await page.route('**/chat-sessions', route => { created = true; return route.abort('failed') })
  await open(page)
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Old session', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Hội thoại mới', exact: true }).click()
  await page.getByRole('textbox', { name: 'Tiêu đề hội thoại' }).fill('Possibly saved session')
  await page.getByRole('button', { name: 'Tạo hội thoại', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Chưa xác định')
  await page.getByRole('button', { name: 'Tải lại danh sách' }).click()
  await expect(page.getByRole('button', { name: 'Possibly saved session', exact: true })).toBeVisible()
})
