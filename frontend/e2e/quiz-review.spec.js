import { test, expect } from '@playwright/test'
import { Buffer } from 'node:buffer'

const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url')
const token = `${encode({ alg: 'HS256' })}.${encode({ sub: 'review@example.test', exp: Math.floor(Date.now() / 1000) + 3600 })}.test`
const date = '2026-11-04T00:00:00Z'
const citation = { documentId: 9, chunkId: 12, documentTitle: 'Database PDF', pageStart: 1, pageEnd: 1, quote: 'ACID protects transactions.', sourceUrl: '/api/courses/7/documents/9/chunks/12/source', fileUrl: null }
const question = id => ({ id, quizId: 4, text: `Câu hỏi ${id}`, type: 'MULTIPLE_CHOICE', conceptId: null, referenceAnswer: null,
  options: ['Atomicity', 'Consistency', 'Isolation', 'Durability'].map((text, i) => ({ id: id * 10 + i, text, correct: i === 0 })) })

async function fixture(page, { status = 'DRAFT', manual = false, reviewed = false, sourceCurrent = true, empty = false } = {}) {
  const state = {
    quiz: { id: 4, courseId: 7, title: 'Ôn tập giao dịch', status, sourceType: manual ? 'MANUAL' : 'AI_GENERATED' },
    contentRevision: 0, sourceCurrent, confirmedBy: null, confirmedAt: null, publishedAt: null,
    questions: empty ? [] : [1, 2].map(id => ({ question: question(id), reviewedBy: reviewed ? 1 : null, reviewedAt: reviewed ? date : null,
      evidenceOrigin: 'ORIGINAL_GENERATION', editedSinceGeneration: false, citations: [citation] })),
  }
  const calls = []
  let reviewError = 0
  let writeError = 0
  let failReload = false
  function response() {
    const count = state.questions.filter(e => e.reviewedAt).length
    const complete = state.questions.length > 0 && count === state.questions.length
    return { ...state, totalQuestions: state.questions.length, reviewedQuestions: count,
      canConfirm: state.quiz.status === 'DRAFT' && complete, canPublish: state.quiz.status === 'CONFIRMED' && complete }
  }
  await page.addInitScript(value => localStorage.setItem('studyhub_access_token', value), token)
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const req = route.request(); const url = new URL(req.url()); const path = url.pathname
    const method = req.method(); const body = req.postData() ? req.postDataJSON() : undefined
    calls.push({ path, method, body, query: url.search })
    if (method !== 'GET' && writeError) return route.fulfill({ status: writeError, json: { message: 'Request rejected' } })
    if (path === '/api/quizzes/4/review') {
      if (reviewError || failReload) return route.fulfill({ status: reviewError || 500, json: { message: 'Read failed' } })
      return route.fulfill({ json: response() })
    }
    if (path.endsWith('/source')) return route.fulfill({ status: 409, json: { message: 'Source changed' } })
    if (path === '/api/quizzes/4' && method === 'GET') return route.fulfill({ json: state.quiz })
    if (path === '/api/courses/7') return route.fulfill({ json: { id: 7, name: 'Database course', workspaceId: 1 } })
    if (path === '/api/courses/7/quizzes') return route.fulfill({ json: [state.quiz] })
    if (path === '/api/quizzes/4/questions' && method === 'GET') return route.fulfill({ json: state.questions.map(e => e.question) })
    if (/\/questions\/\d+\/(unreview|review)$/.test(path)) {
      const id = Number(path.split('/').at(-2)); const entry = state.questions.find(e => e.question.id === id)
      if (body.expectedRevision !== state.contentRevision) return route.fulfill({ status: 409, json: {} })
      const unreview = path.endsWith('/unreview')
      entry.reviewedAt = unreview ? null : date; entry.reviewedBy = unreview ? null : 1
      if (unreview) { state.contentRevision++; state.quiz.status = 'DRAFT' }
      return route.fulfill({ json: response() })
    }
    if (/\/quizzes\/4\/(confirm|publish|archive)$/.test(path)) {
      if (body.expectedRevision !== state.contentRevision) return route.fulfill({ status: 409, json: {} })
      state.quiz.status = path.endsWith('/confirm') ? 'CONFIRMED' : path.endsWith('/publish') ? 'PUBLISHED' : 'ARCHIVED'
      if (state.quiz.status === 'CONFIRMED') { state.confirmedAt = date; state.confirmedBy = 1 }
      if (state.quiz.status === 'PUBLISHED') state.publishedAt = date
      return route.fulfill({ json: response() })
    }
    if ((path === '/api/quizzes/4' && method === 'PUT') || (path === '/api/quizzes/4/questions' && method === 'POST') || /^\/api\/questions\/\d+$/.test(path)) {
      if (!manual) {
        const revision = method === 'DELETE' ? Number(url.searchParams.get('expectedRevision')) : body.expectedRevision
        if (revision !== state.contentRevision) return route.fulfill({ status: 409, json: {} })
        state.contentRevision++; state.quiz.status = 'DRAFT'; state.confirmedAt = null; state.confirmedBy = null
      }
      if (path === '/api/quizzes/4') { Object.assign(state.quiz, { title: body.title, ...(manual ? { status: body.status } : {}) }); return route.fulfill({ json: state.quiz }) }
      const id = Number(path.split('/').at(-1)); const index = state.questions.findIndex(e => e.question.id === id)
      if (method === 'DELETE') { state.questions.splice(index, 1); return route.fulfill({ status: 204 }) }
      const q = { ...body, id: method === 'POST' ? 3 : id, quizId: 4, options: body.options.map((o, i) => ({ ...o, id: 100 + i })) }
      const entry = { question: q, reviewedAt: null, reviewedBy: null, evidenceOrigin: method === 'POST' ? 'NONE' : 'ORIGINAL_GENERATION', editedSinceGeneration: true, citations: method === 'POST' ? [] : [citation] }
      if (method === 'POST') state.questions.push(entry); else state.questions[index] = entry
      return route.fulfill({ status: method === 'POST' ? 201 : 200, json: q })
    }
    if (path.endsWith('/active')) return route.fulfill({ status: 204 })
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { unreadCount: 0 } })
    if (path.endsWith('/progress')) return route.fulfill({ json: { progressPercentage: 0, totalConcepts: 0, masteryPercentage: 0 } })
    return route.fulfill({ json: [] })
  })
  return { state, calls, response, setReviewError: n => { reviewError = n }, setWriteError: n => { writeError = n }, failNextReload: () => { failReload = true } }
}

const open = page => page.goto('/courses/7/quizzes/4')
const card = (page, n = 1) => page.getByRole('article').filter({ has: page.getByRole('heading', { name: `Câu hỏi ${n}`, exact: true }) })

test('approves all questions, confirms separately, then explicitly publishes and persists after reload', async ({ page }) => {
  const f = await fixture(page)
  page.on('dialog', d => d.accept())
  await open(page)
  await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toBeDisabled()
  await card(page).getByRole('button', { name: 'Duyệt câu', exact: true }).click()
  await expect(page.getByText('1 / 2 câu đã duyệt', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toBeDisabled()
  await card(page, 2).getByRole('button', { name: 'Duyệt câu', exact: true }).click()
  await page.getByRole('button', { name: 'Xác nhận Quiz', exact: true }).click()
  await expect(page.getByText('Đã xác nhận, chưa xuất bản.', { exact: true })).toBeVisible()
  expect(f.calls.filter(c => c.path.endsWith('/publish'))).toHaveLength(0)
  await expect(page.getByRole('button', { name: 'Nộp bài' })).toHaveCount(0)
  await page.getByRole('button', { name: 'Xuất bản Quiz', exact: true }).click()
  await expect(page.getByText('Đã xuất bản', { exact: true }).first()).toBeVisible()
  await page.reload()
  await page.getByRole('tab', { name: 'Làm bài', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Nộp bài' })).toBeVisible()
  expect(f.calls.filter(c => c.path.endsWith('/confirm') || c.path.endsWith('/publish')).map(c => c.body)).toEqual([{ expectedRevision: 0 }, { expectedRevision: 0 }])
  expect(f.calls.filter(c => c.path === '/api/quizzes/4' && c.method === 'PUT')).toHaveLength(0)
})

test('editing confirmed content resets confirmation, reloads revision, and preserves other approvals', async ({ page }) => {
  const f = await fixture(page, { status: 'CONFIRMED', reviewed: true })
  page.on('dialog', d => d.accept())
  await open(page)
  await card(page).getByRole('button', { name: 'Sửa', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel('Loại câu hỏi')).toBeDisabled()
  await expect(dialog.getByRole('radio')).toHaveCount(4)
  await expect(dialog.getByRole('button', { name: '+ Thêm lựa chọn' })).toHaveCount(0)
  await dialog.getByLabel('Nội dung câu hỏi', { exact: true }).fill('Câu đã chỉnh sửa')
  await dialog.getByRole('button', { name: 'Lưu câu hỏi', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByText('1 / 2 câu đã duyệt', { exact: true })).toBeVisible()
  await expect(page.getByText('Đã chỉnh sửa so với bản AI gốc', { exact: true })).toBeVisible()
  expect(f.calls.find(c => c.path === '/api/questions/1' && c.method === 'PUT').body.expectedRevision).toBe(0)
  await page.getByRole('article').filter({ hasText: 'Câu đã chỉnh sửa' }).getByRole('button', { name: 'Duyệt câu', exact: true }).click()
  expect(f.calls.filter(c => c.path.endsWith('/questions/1/review')).at(-1).body).toEqual({ expectedRevision: 1 })
})

test('stale revision blocks writes until an explicit reload without losing a draft silently', async ({ page }) => {
  const f = await fixture(page)
  page.on('dialog', d => d.accept())
  await open(page)
  await card(page).getByRole('button', { name: 'Sửa', exact: true }).click()
  await page.getByLabel('Nội dung câu hỏi', { exact: true }).fill('Bản đang nhập')
  f.state.contentRevision = 1
  await page.getByRole('button', { name: 'Lưu câu hỏi', exact: true }).click()
  await expect(page.getByLabel('Nội dung câu hỏi', { exact: true })).toHaveValue('Bản đang nhập')
  await expect(page.getByRole('button', { name: 'Lưu câu hỏi', exact: true })).toBeDisabled()
  expect(f.calls.filter(c => c.path === '/api/questions/1' && c.method === 'PUT')).toHaveLength(1)
  await page.getByRole('button', { name: 'Tải lại nội dung', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(card(page).getByRole('button', { name: 'Duyệt câu', exact: true })).toBeEnabled()
})

test('shows original evidence, missing metadata and stale source without blocking review', async ({ page }, testInfo) => {
  const f = await fixture(page, { sourceCurrent: false, reviewed: true })
  f.state.questions[1] = { ...f.state.questions[1], evidenceOrigin: 'NONE', editedSinceGeneration: true, citations: [] }
  await open(page)
  await expect(page.getByText('Không có nguồn AI gốc cho câu này.', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toBeEnabled()
  await page.getByRole('button', { name: 'Xem nguồn', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByRole('dialog').getByRole('alert')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: 'Xem nguồn', exact: true })).toBeFocused()
  await page.screenshot({ path: testInfo.outputPath('review-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('review-mobile.png'), fullPage: true })
})

test('deleting all questions prevents confirm and uses current revision for every delete', async ({ page }) => {
  const f = await fixture(page)
  page.on('dialog', d => d.accept())
  await open(page)
  await card(page).getByRole('button', { name: 'Xóa', exact: true }).click()
  await expect(page.getByText('0 / 1 câu đã duyệt', { exact: true })).toBeVisible()
  await card(page, 2).getByRole('button', { name: 'Xóa', exact: true }).click()
  await expect(page.getByText('Chưa có câu hỏi', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toBeDisabled()
  expect(f.calls.filter(c => c.method === 'DELETE').map(c => c.query)).toEqual(['?expectedRevision=0', '?expectedRevision=1'])
})

for (const status of ['PUBLISHED', 'ARCHIVED']) {
  test(`${status} has no editing controls or publish shortcut`, async ({ page }) => {
    await fixture(page, { status, reviewed: true })
    await open(page)
    for (const name of ['Sửa Quiz', 'Sửa', 'Xóa', '+ Thêm câu hỏi', 'Duyệt câu', 'Hủy duyệt', 'Xác nhận Quiz', 'Xuất bản Quiz']) {
      await expect(page.getByRole('button', { name, exact: true })).toHaveCount(0)
    }
    if (status === 'ARCHIVED') await expect(page.getByRole('tab', { name: 'Làm bài', exact: true })).toHaveCount(0)
  })
}

test('failed read can be retried and foreign review never displays private content', async ({ page }) => {
  const f = await fixture(page)
  f.setReviewError(500)
  await open(page)
  await expect(page.getByRole('alert')).toBeVisible()
  f.setReviewError(0)
  f.state.quiz.courseId = 8
  await page.getByRole('button', { name: 'Tải lại nội dung', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Câu hỏi 1', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toHaveCount(0)
})

test('failed refresh after a saved edit never offers to resend that edit', async ({ page }) => {
  const f = await fixture(page)
  await open(page)
  await card(page).getByRole('button', { name: 'Sửa', exact: true }).click()
  await page.getByLabel('Nội dung câu hỏi', { exact: true }).fill('Saved on server')
  f.failNextReload()
  await page.getByRole('button', { name: 'Lưu câu hỏi', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Tải lại nội dung', exact: true })).toBeVisible()
  expect(f.calls.filter(c => c.path === '/api/questions/1' && c.method === 'PUT')).toHaveLength(1)
})

test('manual quiz keeps its existing form and publish API without AI review', async ({ page }) => {
  const f = await fixture(page, { manual: true })
  await open(page)
  await page.getByRole('button', { name: 'Sửa Quiz', exact: true }).click()
  await expect(page.getByLabel('Trạng thái', { exact: true }).getByRole('option', { name: 'Đã xác nhận', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: 'Hủy', exact: true }).click()
  await card(page).getByRole('button', { name: 'Sửa', exact: true }).click()
  await expect(page.getByLabel('Loại câu hỏi').getByRole('option')).toHaveCount(3)
  await expect(page.getByRole('dialog').getByRole('checkbox')).toHaveCount(4)
  await page.getByRole('button', { name: 'Hủy', exact: true }).click()
  await page.getByRole('button', { name: 'Xuất bản', exact: true }).click()
  await expect(page.getByText('Đã xuất bản', { exact: true }).first()).toBeVisible()
  expect(f.calls.filter(c => c.path.endsWith('/review'))).toHaveLength(0)
  expect(f.calls.find(c => c.path === '/api/quizzes/4' && c.method === 'PUT').body).toEqual({ title: 'Ôn tập giao dịch', status: 'PUBLISHED' })
})

test('AI list entry routes to review rather than opening the revision-free editor', async ({ page }) => {
  await fixture(page, { status: 'CONFIRMED', reviewed: true })
  await page.goto('/courses/7?tab=quizzes')
  await expect(page.getByText('Đã xác nhận', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Duyệt Quiz AI', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Xuất bản Quiz', exact: true })).toBeEnabled()
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('title edits and added questions reload server review; unreview invalidates confirmation', async ({ page }) => {
  const f = await fixture(page, { status: 'CONFIRMED', reviewed: true })
  page.on('dialog', d => d.accept())
  await open(page)
  await page.getByRole('button', { name: 'Sửa Quiz', exact: true }).click()
  await expect(page.getByRole('dialog').getByLabel('Trạng thái', { exact: true })).toHaveCount(0)
  await page.getByRole('dialog').getByRole('textbox').fill('Tên đã sửa')
  await page.getByRole('button', { name: 'Lưu', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Tên đã sửa', exact: true })).toBeVisible()
  expect(f.calls.find(c => c.path === '/api/quizzes/4' && c.method === 'PUT').body).toEqual({ title: 'Tên đã sửa', status: 'CONFIRMED', expectedRevision: 0 })
  await page.getByRole('button', { name: '+ Thêm câu hỏi', exact: true }).click()
  await page.getByLabel('Nội dung câu hỏi', { exact: true }).fill('Câu mới')
  for (let i = 1; i <= 4; i++) await page.getByLabel(`Nội dung lựa chọn ${i}`, { exact: true }).fill(`Option ${i}`)
  await page.getByLabel('Lựa chọn 2 là đáp án đúng', { exact: true }).check()
  await page.getByRole('button', { name: 'Lưu câu hỏi', exact: true }).click()
  await expect(page.getByText('2 / 3 câu đã duyệt', { exact: true })).toBeVisible()
  await expect(page.getByText('Không có nguồn AI gốc cho câu này.', { exact: true })).toBeVisible()
  expect(f.calls.find(c => c.path === '/api/quizzes/4/questions' && c.method === 'POST').body.expectedRevision).toBe(1)
  await page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Câu mới', exact: true }) }).getByRole('button', { name: 'Duyệt câu', exact: true }).click()
  await page.getByRole('button', { name: 'Xác nhận Quiz', exact: true }).click()
  await card(page).getByRole('button', { name: 'Hủy duyệt', exact: true }).click()
  await expect(page.getByText('2 / 3 câu đã duyệt', { exact: true })).toBeVisible()
  expect(f.state.contentRevision).toBe(3)
  await expect(page.getByRole('button', { name: 'Xuất bản Quiz', exact: true })).toHaveCount(0)
})

test('archive from the attempt tab returns to readable review with no reopen path', async ({ page }) => {
  await fixture(page, { status: 'PUBLISHED', reviewed: true })
  page.on('dialog', d => d.accept())
  await open(page)
  await page.getByRole('tab', { name: 'Làm bài', exact: true }).click()
  await page.getByRole('button', { name: 'Lưu trữ Quiz', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Câu hỏi 1', exact: true })).toBeVisible()
  await expect(page.getByRole('tab', { name: 'Làm bài', exact: true })).toHaveCount(0)
})

for (const status of [409, 503]) {
  test(`failed confirm ${status} locks mutations until reloaded and never retries the write`, async ({ page }) => {
    const f = await fixture(page, { reviewed: true })
    await open(page)
    f.setWriteError(status)
    await page.getByRole('button', { name: 'Xác nhận Quiz', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toBeDisabled()
    await expect(page.getByRole('button', { name: 'Tải lại nội dung', exact: true })).toBeVisible()
    expect(f.calls.filter(c => c.path.endsWith('/confirm'))).toHaveLength(1)
    f.setWriteError(0)
    await page.getByRole('button', { name: 'Tải lại nội dung', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toBeEnabled()
  })
}

test('a pending review prevents duplicate writes and mutations while its response is delayed', async ({ page }) => {
  const f = await fixture(page)
  let release
  const barrier = new Promise(resolve => { release = resolve })
  let writes = 0
  await page.route('**/quizzes/4/questions/1/review', async route => {
    writes++
    await barrier
    await route.fallback()
  })
  await open(page)
  const approve = card(page).getByRole('button', { name: 'Duyệt câu', exact: true })
  await approve.evaluate(node => { node.click(); node.click() })
  await expect(page.getByRole('button', { name: 'Sửa Quiz', exact: true })).toBeDisabled()
  expect(writes).toBe(1)
  release()
  await expect(page.getByText('1 / 2 câu đã duyệt', { exact: true })).toBeVisible()
  expect(f.calls.filter(c => c.path.endsWith('/questions/1/review'))).toHaveLength(1)
})

for (const operation of ['read', 'write']) {
  test(`late review ${operation} cannot repopulate a page after navigation`, async ({ page }) => {
    await fixture(page)
    let release
    let entered
    const started = new Promise(resolve => { entered = resolve })
    const barrier = new Promise(resolve => { release = resolve })
    await page.route(operation === 'read' ? '**/quizzes/4/review' : '**/quizzes/4/questions/1/review', async route => {
      entered(); await barrier; await route.fallback()
    })
    await open(page)
    if (operation === 'write') await card(page).getByRole('button', { name: 'Duyệt câu', exact: true }).click()
    await started
    await page.getByRole('link', { name: '← Khóa học', exact: true }).click()
    await expect(page).toHaveURL(/\/courses\/7\?tab=quizzes$/)
    const response = page.waitForResponse(r => new URL(r.url()).pathname === (operation === 'read' ? '/api/quizzes/4/review' : '/api/quizzes/4/questions/1/review'))
    release()
    await response
    await expect(page.getByRole('region', { name: 'Duyệt Quiz AI' })).toHaveCount(0)
    await expect(page.getByRole('heading', { name: 'Câu hỏi 1', exact: true })).toHaveCount(0)
    await expect(page.getByRole('link', { name: 'Duyệt Quiz AI', exact: true })).toBeVisible()
  })
}

test('logout discards a delayed review mutation response', async ({ page }) => {
  await fixture(page)
  let release
  const barrier = new Promise(resolve => { release = resolve })
  await page.route('**/quizzes/4/questions/1/review', async route => { await barrier; await route.fallback() })
  await open(page)
  await card(page).getByRole('button', { name: 'Duyệt câu', exact: true }).click()
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  const response = page.waitForResponse(r => new URL(r.url()).pathname === '/api/quizzes/4/questions/1/review')
  release()
  await response
  await expect(page.getByRole('heading', { name: 'Câu hỏi 1', exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => localStorage.getItem('studyhub_access_token'))).toBeNull()
})

test('missing generation metadata is a visible warning and does not certify a source', async ({ page }) => {
  await fixture(page, { sourceCurrent: null, reviewed: true })
  await open(page)
  await expect(page.getByText('Không có thông tin nguồn AI gốc để đối chiếu.', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Xác nhận Quiz', exact: true })).toBeEnabled()
})

for (const status of [401, 404]) {
  test(`workflow ${status} removes protected review content`, async ({ page }) => {
    const f = await fixture(page, { reviewed: true })
    await open(page)
    f.setWriteError(status)
    await page.getByRole('button', { name: 'Xác nhận Quiz', exact: true }).click()
    if (status === 401) await expect(page).toHaveURL(/\/login$/)
    else await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Câu hỏi 1', exact: true })).toHaveCount(0)
  })
}

test('server validation error keeps the question form editable with its draft', async ({ page }) => {
  const f = await fixture(page)
  await open(page)
  await card(page).getByRole('button', { name: 'Sửa', exact: true }).click()
  f.setWriteError(400)
  await page.getByLabel('Nội dung câu hỏi', { exact: true }).fill('Nội dung đang sửa')
  await page.getByRole('button', { name: 'Lưu câu hỏi', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toBeVisible()
  await expect(page.getByLabel('Nội dung câu hỏi', { exact: true })).toHaveValue('Nội dung đang sửa')
  await expect(page.getByRole('button', { name: 'Lưu câu hỏi', exact: true })).toBeEnabled()
  expect(f.calls.filter(c => c.path === '/api/questions/1' && c.method === 'PUT')).toHaveLength(1)
})
