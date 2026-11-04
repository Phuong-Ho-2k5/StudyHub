import test from 'node:test'
import assert from 'node:assert/strict'
import * as api from './quizReviewApi.js'
import { deleteQuestion } from './quizApi.js'
import { buildQuestionPayload } from '../components/quiz/quizForms.js'
import { ApiError } from './authApi.js'

test('review workflow sends the viewed revision, JWT and only the intended action', async () => {
  const original = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, ...options })
    return new Response(JSON.stringify({ contentRevision: 0 }))
  }
  try {
    await api.getQuizReview('jwt', 4)
    await api.reviewQuizQuestion('jwt', 4, 8, 0)
    await api.unreviewQuizQuestion('jwt', 4, 8, 7)
    await api.confirmAiQuiz('jwt', 4, 7)
    await api.publishAiQuiz('jwt', 4, 7)
    await api.archiveAiQuiz('jwt', 4, 7)
    assert.deepEqual(calls.map(c => [c.url, c.method ?? 'GET', c.body && JSON.parse(c.body)]), [
      ['/api/quizzes/4/review', 'GET', undefined],
      ['/api/quizzes/4/questions/8/review', 'POST', { expectedRevision: 0 }],
      ['/api/quizzes/4/questions/8/unreview', 'POST', { expectedRevision: 7 }],
      ['/api/quizzes/4/confirm', 'POST', { expectedRevision: 7 }],
      ['/api/quizzes/4/publish', 'POST', { expectedRevision: 7 }],
      ['/api/quizzes/4/archive', 'POST', { expectedRevision: 7 }],
    ])
    assert.ok(calls.every(c => c.headers.Authorization === 'Bearer jwt'))
  } finally { globalThis.fetch = original }
})

test('conflicting publish is not retried and preserves the HTTP status', async () => {
  const original = globalThis.fetch
  let calls = 0
  globalThis.fetch = async () => { calls++; return new Response('{}', { status: 409 }) }
  try {
    await assert.rejects(api.publishAiQuiz('jwt', 4, 0), e => e instanceof ApiError && e.status === 409)
    assert.equal(calls, 1)
  } finally { globalThis.fetch = original }
})

test('AI question deletion carries revision zero while MANUAL keeps its URL', async () => {
  const original = globalThis.fetch
  const urls = []
  globalThis.fetch = async url => { urls.push(url); return new Response(null, { status: 204 }) }
  try {
    await deleteQuestion('jwt', 8, 0)
    await deleteQuestion('jwt', 8, 7)
    await deleteQuestion('jwt', 8)
    assert.deepEqual(urls, ['/api/questions/8?expectedRevision=0', '/api/questions/8?expectedRevision=7', '/api/questions/8'])
  } finally { globalThis.fetch = original }
})

const draft = () => ({ text: 'ACID?', type: 'MULTIPLE_CHOICE', conceptId: '',
  options: ['Atomicity', 'Consistency', 'Isolation', 'Durability'].map((text, i) => ({ text, correct: i === 0 })) })

test('AI question form requires exactly four distinct options and one correct answer', () => {
  const ai = { aiGenerated: true }
  assert.equal(buildQuestionPayload(draft(), ai).referenceAnswer, null)
  for (const mutate of [
    d => { d.type = 'SHORT_ANSWER'; d.referenceAnswer = 'answer' },
    d => { d.type = 'TRUE_FALSE'; d.options = d.options.slice(0, 2) },
    d => { d.options.pop() },
    d => { d.options.push({ text: 'Other', correct: false }) },
    d => { d.options[0].correct = false },
    d => { d.options[1].correct = true },
    d => { d.options[1].text = ' ATOMICITY\u00a0' },
    d => { d.options[1].text = 'Ａｔｏｍｉｃｉｔｙ' },
    d => { d.options[1].text = '\u00a0' },
  ]) {
    const d = draft(); mutate(d)
    assert.throws(() => buildQuestionPayload(d, ai))
  }
  const manual = draft(); manual.options[1].correct = true
  assert.equal(buildQuestionPayload(manual).options.filter(o => o.correct).length, 2)
})
