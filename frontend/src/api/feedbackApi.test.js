import { test } from 'node:test'
import assert from 'node:assert/strict'
import { saveMessageFeedback } from './courseAiApi.js'
import { validateFeedback } from '../components/ai/feedbackDisplay.js'
import { validateMessagePage } from '../components/ai/chatHistory.js'

const date = '2026-11-07T00:00:00Z'
const feedback = { id: 1, messageId: 2, rating: 'HELPFUL', note: null, createdAt: date, updatedAt: date }
test('posts full replacement once with JWT and parses create/update', async () => {
  const original = globalThis.fetch
  try {
    for (const status of [201, 200, 503]) {
      let calls = 0
      globalThis.fetch = async (url, options) => {
        calls++
        assert.equal(url, '/api/ai/messages/2/feedback')
        assert.equal(options.method, 'POST')
        assert.equal(options.headers.Authorization, 'Bearer token')
        assert.deepEqual(JSON.parse(options.body), { rating: 'HELPFUL', note: null })
        return new Response(JSON.stringify(feedback), { status, headers: { 'Content-Type': 'application/json' } })
      }
      if (status === 503) await assert.rejects(saveMessageFeedback('token', 2, { rating: 'HELPFUL', note: null }))
      else assert.deepEqual(await saveMessageFeedback('token', 2, { rating: 'HELPFUL', note: null }), feedback)
      assert.equal(calls, 1)
    }
  } finally { globalThis.fetch = original }
})
test('rejects feedback with wrong identity, enum, note or timestamps', () => {
  assert.equal(validateFeedback(feedback, 2), true)
  for (const change of [{ id: 0 }, { messageId: 3 }, { rating: 'helpful' }, { note: 1 }, { note: 'x'.repeat(2001) }, { createdAt: 'bad' }, { updatedAt: null }]) {
    assert.equal(validateFeedback({ ...feedback, ...change }, 2), false)
  }
})
test('history validates feedback scope and rejects feedback on USER', () => {
  const item = { id: 2, sessionId: 23, role: 'ASSISTANT', content: 'No context', status: 'INSUFFICIENT_CONTEXT', model: null, promptVersion: 'v1', citations: [], createdAt: date, feedback }
  const page = value => ({ content: [value], number: 0, size: 20, totalPages: 1, totalElements: 1, last: true })
  assert.equal(validateMessagePage(page(item), 23), true)
  assert.equal(validateMessagePage(page({ ...item, feedback: { ...feedback, messageId: 9 } }), 23), false)
  assert.equal(validateMessagePage(page({ ...item, role: 'USER', status: null, promptVersion: null }), 23), false)
})
