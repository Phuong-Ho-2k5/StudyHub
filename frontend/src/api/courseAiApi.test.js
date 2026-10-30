import test from 'node:test'
import assert from 'node:assert/strict'
import * as api from './courseAiApi.js'
import { fetchDocumentPdf } from './documentFiles.js'
import { validateAiInput, formatSourcePages, ingestionDisplay, validateChatResponse } from '../components/ai/aiDisplay.js'

test('AI requests preserve scope, payload, JWT and server status without retries', async () => {
  const original = globalThis.fetch
  try {
    for (const [name, args, path, method, body] of [
      ['searchCourse', [7, { query: 'transaction' }], '/api/courses/7/search', 'POST', { query: 'transaction', topK: 5 }],
      ['chatCourse', [7, { question: 'ACID?' }], '/api/courses/7/ai/chat', 'POST', { question: 'ACID?', topK: 5 }],
      ['ingestDocument', [9], '/api/documents/9/ingest', 'POST', undefined],
      ['getDocument', [9], '/api/documents/9', 'GET', undefined],
      ['getSource', [7, 9, 12], '/api/courses/7/documents/9/chunks/12/source', 'GET', undefined],
    ]) {
      globalThis.fetch = async (url, options) => {
        assert.equal(url, path)
        assert.equal(options.method, method)
        assert.equal(options.headers.Authorization, 'Bearer test')
        assert.deepEqual(options.body ? JSON.parse(options.body) : undefined, body)
        return Response.json({ ok: true })
      }
      assert.deepEqual(await api[name]('test', ...args), { ok: true })
    }
    for (const status of [409, 422, 503, 504]) {
      let calls = 0
      globalThis.fetch = async () => { calls++; return Response.json({ message: 'error' }, { status }) }
      await assert.rejects(api.chatCourse('test', 7, { question: 'ACID?' }), e => e.status === status)
      assert.equal(calls, 1)
    }
  } finally { globalThis.fetch = original }
})

test('PDF reads binary, preserves errors, cancellation and rejects non-PDF responses', async () => {
  const original = globalThis.fetch
  try {
    const controller = new AbortController()
    globalThis.fetch = async (url, options) => {
      assert.equal(url, '/api/documents/9/file')
      assert.equal(options.headers.Authorization, 'Bearer test')
      assert.equal(options.signal, controller.signal)
      return new Response('%PDF-test', { headers: { 'Content-Type': 'application/pdf; charset=binary' } })
    }
    assert.equal(await (await fetchDocumentPdf('test', 9, { signal: controller.signal })).text(), '%PDF-test')
    for (const status of [404, 502]) {
      globalThis.fetch = async () => new Response('<html>failure</html>', { status })
      await assert.rejects(fetchDocumentPdf('test', 9), e => e.status === status)
    }
    globalThis.fetch = async () => Response.json({ ok: true })
    await assert.rejects(fetchDocumentPdf('test', 9), e => e.status === 502)
    globalThis.fetch = async () => { throw new DOMException('Aborted', 'AbortError') }
    await assert.rejects(fetchDocumentPdf('test', 9), e => e.name === 'AbortError')
  } finally { globalThis.fetch = original }
})

test('AI presentation preserves nullable provenance and published versions', () => {
  assert.notEqual(validateAiInput(' \n '), '')
  assert.equal(validateAiInput('a'.repeat(16384)), '')
  assert.notEqual(validateAiInput('a'.repeat(16385)), '')
  assert.equal(formatSourcePages(null, null), 'Chưa xác định trang')
  assert.equal(formatSourcePages(2, 2), 'Trang 2')
  assert.equal(formatSourcePages(2, 4), 'Trang 2–4')
  assert.deepEqual(ingestionDisplay({ ingestionStatus: 'FAILED', publishedIngestionId: 'old', managedFile: true }),
    { label: 'Xử lý thất bại', published: true, pending: false, action: 'Thử lại' })
  for (const status of ['PENDING', 'PROCESSING']) assert.equal(ingestionDisplay({ ingestionStatus: status }).pending, true)
  assert.equal(ingestionDisplay({ ingestionStatus: 'NOT_REQUESTED', managedFile: false }).action, null)
  assert.equal(validateChatResponse({ status: 'ANSWERED', answer: 'x', citations: [] }), false)
  assert.equal(validateChatResponse({ status: 'INSUFFICIENT_CONTEXT', answer: 'x', citations: [] }), true)
})

test('source and binary PDF 401 dispatch unauthorized for the initiating token', async () => {
  const originalFetch = globalThis.fetch
  const originalWindow = globalThis.window
  const events = []
  globalThis.window = { dispatchEvent: event => events.push([event.type, event.detail]) }
  globalThis.fetch = async () => Response.json({}, { status: 401 })
  try {
    await assert.rejects(api.getSource('source-token', 7, 9, 12), error => error.status === 401)
    await assert.rejects(fetchDocumentPdf('pdf-token', 9), error => error.status === 401)
    assert.deepEqual(events, [['studyhub:unauthorized', 'Bearer source-token'], ['studyhub:unauthorized', 'Bearer pdf-token']])
  } finally {
    globalThis.fetch = originalFetch
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
  }
})
