import { test, expect } from '@playwright/test'
import { Buffer } from 'node:buffer'

const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url')
const token = `${encode({ alg: 'HS256' })}.${encode({ sub: 'feedback@test.local', exp: Math.floor(Date.now() / 1000) + 3600 })}.test`
const date = '2026-11-07T00:00:00Z'
const paged = (content, size = 20) => ({ content, number: 0, size, totalPages: 1, totalElements: content.length, last: true })
const feedback = (rating = 'HELPFUL', note = null, messageId = 2) => ({ id: 8, messageId, rating, note, createdAt: date, updatedAt: date })
let saved
test.beforeEach(async ({ page }) => {
  saved = null
  await page.addInitScript(value => localStorage.setItem('studyhub_access_token', value), token)
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (/\/courses\/\d+$/.test(path)) return route.fulfill({ json: { id: Number(path.split('/').at(-1)), name: 'Course' } })
    if (path.endsWith('/chat-sessions')) return route.fulfill({ json: paged([23, 24].map(id => ({ id, courseId: 7, title: `Session ${id}`, createdAt: date, updatedAt: date })), 10) })
    if (path.endsWith('/messages')) {
      const sessionId = Number(path.split('/').at(-2))
      return route.fulfill({ json: paged([
        { id: sessionId === 23 ? 1 : 10, sessionId, role: 'USER', content: 'Question', status: null, model: null, promptVersion: null, citations: [], createdAt: date, feedback: null },
        { id: sessionId === 23 ? 2 : 12, sessionId, role: 'ASSISTANT', content: 'No context', status: 'INSUFFICIENT_CONTEXT', model: null, promptVersion: 'v1', citations: [], createdAt: date, feedback: sessionId === 23 ? saved : null },
      ]) })
    }
    if (path.endsWith('/feedback')) {
      const data = route.request().postDataJSON()
      saved = feedback(data.rating, data.note)
      return route.fulfill({ status: 201, json: saved })
    }
    if (path.endsWith('/progress')) return route.fulfill({ json: { progressPercentage: 0, totalConcepts: 0, masteryPercentage: 0 } })
    if (path.endsWith('/active')) return route.fulfill({ status: 204 })
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { unreadCount: 0 } })
    if (path.endsWith('/quiz-history')) return route.fulfill({ json: paged([]) })
    if (path.endsWith('/statistics')) return route.fulfill({ json: { totalStudySeconds: 0, completedStudySessions: 0, quizAttemptCount: 0, averageQuizScore: null } })
    return route.fulfill({ json: [] })
  })
})
async function open(page) {
  await page.goto('/courses/7?tab=ai')
  await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
  await page.getByRole('button', { name: 'Session 23', exact: true }).click()
}
const article = page => page.getByTestId('chat-message').last()
test('saves and updates feedback, restoring it after reload', async ({ page }, testInfo) => {
  await open(page)
  await expect(page.getByTestId('chat-message').first().getByRole('button', { name: 'Hữu ích', exact: true })).toHaveCount(0)
  const answer = article(page)
  await answer.getByRole('button', { name: 'Hữu ích', exact: true }).click()
  await expect(answer.getByText('Đã lưu phản hồi', { exact: true })).toHaveCount(0)
  await answer.getByRole('button', { name: 'Thêm ghi chú' }).click()
  await answer.getByRole('textbox', { name: 'Ghi chú' }).fill('Please explain')
  await answer.getByRole('button', { name: 'Gửi phản hồi' }).click()
  await expect(answer.getByRole('status')).toHaveText('Đã lưu phản hồi')
  await page.reload()
  await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
  await page.getByRole('button', { name: 'Session 23', exact: true }).click()
  await expect(answer.getByRole('button', { name: 'Hữu ích', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(answer.getByRole('textbox', { name: 'Ghi chú' })).toHaveValue('Please explain')
  await answer.getByRole('button', { name: 'Chưa hữu ích', exact: true }).click()
  await answer.getByRole('button', { name: 'Cập nhật phản hồi' }).click()
  await expect(answer.getByRole('status')).toHaveText('Đã lưu phản hồi')
  expect(saved.rating).toBe('NOT_HELPFUL')
  await page.screenshot({ path: testInfo.outputPath('feedback-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(answer.getByRole('button', { name: 'Cập nhật phản hồi' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('feedback-mobile.png'), fullPage: true })
})
test('double click sends once, disables only feedback and retains draft on failure', async ({ page }) => {
  let release, calls = 0
  const barrier = new Promise(resolve => { release = resolve })
  await page.route('**/feedback', async route => { calls++; await barrier; await route.fulfill({ status: 503, json: {} }) })
  await open(page)
  const answer = article(page)
  await answer.getByRole('button', { name: 'Hữu ích', exact: true }).click()
  await answer.getByRole('button', { name: 'Thêm ghi chú' }).click()
  await answer.getByRole('textbox', { name: 'Ghi chú' }).fill('Draft')
  await answer.getByRole('button', { name: 'Gửi phản hồi' }).evaluate(button => { button.click(); button.click() })
  await expect(answer.getByRole('button', { name: 'Đang lưu…' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Gửi câu hỏi', exact: true })).toBeEnabled()
  release()
  await expect(answer.getByRole('alert')).toBeVisible()
  await expect(answer.getByRole('textbox', { name: 'Ghi chú' })).toHaveValue('Draft')
  await expect(answer.getByRole('status')).toHaveCount(0)
  expect(calls).toBe(1)
})
for (const invalid of [{}, feedback('HELPFUL', null, 999)]) {
  test(`rejects malformed response ${JSON.stringify(invalid)}`, async ({ page }) => {
    await page.route('**/feedback', route => route.fulfill({ json: invalid }))
    await open(page)
    await article(page).getByRole('button', { name: 'Hữu ích', exact: true }).click()
    await article(page).getByRole('button', { name: 'Gửi phản hồi' }).click()
    await expect(article(page).getByRole('alert')).toBeVisible()
    await expect(article(page).getByRole('status')).toHaveCount(0)
  })
}
test('late response from previous Session cannot affect current message', async ({ page }) => {
  let release
  const barrier = new Promise(resolve => { release = resolve })
  await page.route('**/feedback', async route => { await barrier; await route.fulfill({ json: feedback() }) })
  await open(page)
  await article(page).getByRole('button', { name: 'Hữu ích', exact: true }).click()
  await article(page).getByRole('button', { name: 'Gửi phản hồi' }).click()
  await expect(article(page).getByRole('button', { name: 'Đang lưu…' })).toBeDisabled()
  await page.getByRole('button', { name: 'Session 24', exact: true }).click()
  const response = page.waitForResponse('**/feedback')
  release()
  await (await response).finished()
  await expect(article(page).getByRole('button', { name: 'Hữu ích', exact: true })).toHaveAttribute('aria-pressed', 'false')
  await expect(article(page).getByRole('status')).toHaveCount(0)
})

test('failed update preserves draft and previously saved feedback on reload', async ({ page }) => {
  saved = feedback('HELPFUL', 'Original')
  await page.route('**/feedback', route => route.fulfill({ status: 503, json: {} }))
  await open(page)
  const answer = article(page)
  await answer.getByRole('button', { name: 'Chưa hữu ích', exact: true }).click()
  await answer.getByRole('textbox', { name: 'Ghi chú' }).fill('Unsaved draft')
  await answer.getByRole('button', { name: 'Cập nhật phản hồi' }).click()
  await expect(answer.getByRole('alert')).toBeVisible()
  await expect(answer.getByRole('textbox', { name: 'Ghi chú' })).toHaveValue('Unsaved draft')
  await page.getByRole('button', { name: 'Tải lại lịch sử', exact: true }).click()
  await expect(answer.getByRole('textbox', { name: 'Ghi chú' })).toHaveValue('Original')
  await expect(answer.getByRole('button', { name: 'Hữu ích', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

test('ANSWERED form supports keyboard selection and submitting', async ({ page }) => {
  const citation = { documentId: 9, chunkId: 12, documentTitle: 'PDF', chunkIndex: 0, pageStart: null, pageEnd: null, quote: 'Source', sourceUrl: '/api/courses/7/documents/9/chunks/12/source', fileUrl: null }
  await page.route('**/messages?*', route => route.fulfill({ json: paged([
    { id: 2, sessionId: 23, role: 'ASSISTANT', content: 'Answer', status: 'ANSWERED', model: null, promptVersion: 'v1', citations: [citation], createdAt: date, feedback: null },
  ]) }))
  await open(page)
  const useful = article(page).getByRole('button', { name: 'Hữu ích', exact: true })
  await useful.focus()
  await page.keyboard.press('Space')
  await expect(useful).toHaveAttribute('aria-pressed', 'true')
  await article(page).getByRole('button', { name: 'Gửi phản hồi' }).focus()
  await page.keyboard.press('Enter')
  await expect(article(page).getByRole('status')).toHaveText('Đã lưu phản hồi')
  await expect(article(page).getByText('Source', { exact: true })).toBeVisible()
})

test('changing Course during save cannot carry feedback into new Course', async ({ page }) => {
  let release
  const barrier = new Promise(resolve => { release = resolve })
  await page.route('**/feedback', async route => { await barrier; await route.fulfill({ json: feedback() }).catch(() => {}) })
  await page.route('**/courses/8/ai/chat-sessions?*', route => route.fulfill({ json: paged([
    { id: 25, courseId: 8, title: 'Other Course session', createdAt: date, updatedAt: date },
  ], 10) }))
  await open(page)
  await article(page).getByRole('button', { name: 'Hữu ích', exact: true }).click()
  await article(page).getByRole('button', { name: 'Gửi phản hồi' }).click()
  await expect(article(page).getByRole('button', { name: 'Đang lưu…' })).toBeDisabled()
  await page.goto('/courses/8?tab=ai')
  await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
  await page.getByRole('button', { name: 'Other Course session', exact: true }).click()
  release()
  await expect(article(page).getByRole('button', { name: 'Hữu ích', exact: true })).toHaveAttribute('aria-pressed', 'false')
  await expect(article(page).getByRole('status')).toHaveCount(0)
})

for (const status of [200, 401]) {
  test(`switching account ignores old feedback ${status} response`, async ({ page }) => {
    const otherToken = `${encode({ alg: 'HS256' })}.${encode({ sub: 'other@test.local', exp: Math.floor(Date.now() / 1000) + 3600 })}.test`
    let release
    const barrier = new Promise(resolve => { release = resolve })
    await page.route('**/feedback', async route => {
      expect(route.request().headers().authorization).toBe(`Bearer ${token}`)
      await barrier
      await route.fulfill({ status, json: status === 200 ? feedback('HELPFUL', 'Old account note') : {} })
    })
    await page.route('**/api/auth/login', route => route.fulfill({ json: { accessToken: otherToken } }))
    await page.route('**/api/courses?*', route => route.fulfill({ json: paged([{ id: 7, name: 'Course', status: 'ACTIVE', description: 'Study', workspaceId: 1 }]) }))
    await open(page)
    await article(page).getByRole('button', { name: 'Hữu ích', exact: true }).click()
    await article(page).getByRole('button', { name: 'Gửi phản hồi' }).click()
    await expect(article(page).getByRole('button', { name: 'Đang lưu…' })).toBeDisabled()
    await page.getByRole('button', { name: 'Đăng xuất' }).click()
    await page.getByRole('textbox', { name: 'Email', exact: true }).fill('other@test.local')
    await page.getByLabel('Mật khẩu', { exact: true }).fill('password123')
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
    await expect(page).toHaveURL(/\/welcome$/)
    await expect(page.getByRole('link', { name: /Mở khóa học/ })).toBeVisible()
    await page.getByRole('link', { name: /Mở khóa học/ }).click()
    await page.getByRole('tab', { name: 'Trợ lý AI', exact: true }).click()
    await page.getByRole('button', { name: 'Hỏi tài liệu', exact: true }).click()
    await page.getByRole('button', { name: 'Session 23', exact: true }).click()
    await expect(article(page).getByRole('button', { name: 'Hữu ích', exact: true })).toHaveAttribute('aria-pressed', 'false')
    const response = page.waitForResponse('**/feedback')
    release()
    await (await response).finished()
    await expect(page).toHaveURL(/\/courses\/7/)
    await expect(article(page).getByRole('button', { name: 'Hữu ích', exact: true })).toHaveAttribute('aria-pressed', 'false')
    expect(await page.evaluate(() => localStorage.getItem('studyhub_access_token'))).toBe(otherToken)
    await expect(page.getByText('Old account note', { exact: true })).toHaveCount(0)
    await expect(page.getByText('Đã lưu phản hồi', { exact: true })).toHaveCount(0)
  })
}
