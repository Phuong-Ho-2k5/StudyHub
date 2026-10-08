import test from 'node:test'
import assert from 'node:assert/strict'
import * as api from './quizApi.js'
import { ApiError } from './authApi.js'

test('quiz and question requests use backend routes, verbs, JWT and JSON payloads', async (t) => {
  const quiz = { title: 'Ôn tập', status: 'PUBLISHED' }
  const question = { text: 'Đúng?', type: 'TRUE_FALSE', conceptId: 5, referenceAnswer: null,
    options: [{ text: 'Đúng', correct: true }, { text: 'Sai', correct: false }] }
  const submission = { answers: [{ questionId: 7, answerOptionIds: [9] }] }
  const fixtures = [
    ['listQuizzes', [3], '/api/courses/3/quizzes', 'GET'],
    ['createQuiz', [3, { title: 'Ôn tập' }], '/api/courses/3/quizzes', 'POST', { title: 'Ôn tập' }],
    ['getQuiz', [4], '/api/quizzes/4', 'GET'],
    ['updateQuiz', [4, quiz], '/api/quizzes/4', 'PUT', quiz],
    ['deleteQuiz', [4], '/api/quizzes/4', 'DELETE'],
    ['listQuestions', [4], '/api/quizzes/4/questions', 'GET'],
    ['createQuestion', [4, question], '/api/quizzes/4/questions', 'POST', question],
    ['updateQuestion', [7, question], '/api/questions/7', 'PUT', question],
    ['deleteQuestion', [7], '/api/questions/7', 'DELETE'],
    ['submitQuiz', [4, submission], '/api/quizzes/4/submit', 'POST', submission],
  ]
  for (const [name, args, path, method, body] of fixtures) {
    await t.test(name, async () => {
      const originalFetch = globalThis.fetch
      let sent
      globalThis.fetch = async (url, options) => {
        sent = { url, ...options }
        return new Response(method === 'DELETE' ? null : JSON.stringify({ id: 4 }),
          { status: method === 'DELETE' ? 204 : 200 })
      }
      try {
        assert.deepEqual(await api[name]('test-jwt', ...args), method === 'DELETE' ? {} : { id: 4 })
        assert.equal(sent.url, path)
        assert.equal(sent.method ?? 'GET', method)
        assert.equal(sent.headers.Authorization, 'Bearer test-jwt')
        assert.deepEqual(sent.body ? JSON.parse(sent.body) : undefined, body)
      } finally { globalThis.fetch = originalFetch }
    })
  }
})

test('failed quiz submission preserves the server error and never retries the write', async () => {
  const originalFetch = globalThis.fetch
  let count = 0
  globalThis.fetch = async () => {
    count++
    return new Response(JSON.stringify({ message: 'Quiz is not published' }), { status: 400 })
  }
  try {
    await assert.rejects(api.submitQuiz('test-jwt', 4, { answers: [] }), (error) => {
      assert.ok(error instanceof ApiError)
      assert.equal(error.status, 400)
      assert.equal(error.message, 'Quiz is not published')
      return true
    })
    assert.equal(count, 1)
  } finally { globalThis.fetch = originalFetch }
})
