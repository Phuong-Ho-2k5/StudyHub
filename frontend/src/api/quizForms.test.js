import test from 'node:test'
import assert from 'node:assert/strict'
import { buildQuestionPayload, buildSubmission } from '../components/quiz/quizForms.js'

const option = (text, correct = false) => ({ text, correct })

test('multiple choice requires four nonblank options and at least one correct answer', () => {
  const draft = { text: '  Chọn số chẵn  ', type: 'MULTIPLE_CHOICE', conceptId: '9',
    referenceAnswer: 'stale', options: [option('  2  ', true), option('3'), option('4', true), option('5')] }
  assert.deepEqual(buildQuestionPayload(draft), {
    text: 'Chọn số chẵn', type: 'MULTIPLE_CHOICE', conceptId: 9, referenceAnswer: null,
    options: [option('2', true), option('3'), option('4', true), option('5')],
  })
  assert.throws(() => buildQuestionPayload({ ...draft, options: draft.options.slice(0, 3) }))
  assert.throws(() => buildQuestionPayload({ ...draft, options: draft.options.map((item) => ({ ...item, correct: false })) }))
  assert.throws(() => buildQuestionPayload({ ...draft, options: [...draft.options.slice(0, 3), option('  ')] }))
})

test('true false accepts exactly two choices with exactly one correct answer', () => {
  const draft = { text: 'Đúng?', type: 'TRUE_FALSE', options: [option('Đúng', true), option('Sai')] }
  assert.equal(buildQuestionPayload(draft).conceptId, null)
  assert.throws(() => buildQuestionPayload({ ...draft, options: [option('Đúng', true), option('Sai', true)] }))
  assert.throws(() => buildQuestionPayload({ ...draft, options: [...draft.options, option('Khác')] }))
})

test('short answer preserves the reference exactly and clears stale option answers', () => {
  assert.deepEqual(buildQuestionPayload({ text: ' Giải thích ', type: 'SHORT_ANSWER',
    referenceAnswer: ' Lời giải ', options: [option('stale', true)], conceptId: '' }), {
    text: 'Giải thích', type: 'SHORT_ANSWER', referenceAnswer: ' Lời giải ', options: [], conceptId: null,
  })
  assert.throws(() => buildQuestionPayload({ text: 'Giải thích', type: 'SHORT_ANSWER', referenceAnswer: ' ' }))
})

test('question editor rejects blank text, oversized fields, invalid types and concept ids', () => {
  const draft = { text: 'Câu hỏi', type: 'SHORT_ANSWER', referenceAnswer: 'Đáp án' }
  assert.throws(() => buildQuestionPayload({ ...draft, text: ' ' }))
  assert.throws(() => buildQuestionPayload({ ...draft, text: 'x'.repeat(501) }))
  assert.throws(() => buildQuestionPayload({ ...draft, referenceAnswer: 'x'.repeat(4001) }))
  assert.throws(() => buildQuestionPayload({ ...draft, type: 'UNKNOWN' }))
  assert.throws(() => buildQuestionPayload({ ...draft, conceptId: -1 }))
})

const questions = [
  { id: 1, type: 'MULTIPLE_CHOICE', options: [{ id: 11 }, { id: 12 }, { id: 13 }, { id: 14 }] },
  { id: 2, type: 'TRUE_FALSE', options: [{ id: 21 }, { id: 22 }] },
  { id: 3, type: 'SHORT_ANSWER', options: [] },
]

test('submission uses option ids for choice answers and only text for short answers', () => {
  assert.deepEqual(buildSubmission(questions, { 1: [11, 13], 2: [22], 3: '  Giải thích  ' }), {
    answers: [{ questionId: 1, answerOptionIds: [11, 13] }, { questionId: 2, answerOptionIds: [22] },
      { questionId: 3, answerText: '  Giải thích  ' }],
  })
})

test('short answer payloads preserve case, accents, repeated spaces and newlines', () => {
  const value = ' Hà  Nội\nViệt Nam '
  const question = buildQuestionPayload({ text: 'Thủ đô?', type: 'SHORT_ANSWER', referenceAnswer: value })
  assert.equal(question.referenceAnswer, value)
  assert.equal(buildSubmission([questions[2]], { 3: value }).answers[0].answerText, value)
})

test('short answer length validation counts leading and trailing whitespace', () => {
  const value = ` ${'x'.repeat(3999)} `
  assert.throws(() => buildQuestionPayload({ text: 'Câu hỏi', type: 'SHORT_ANSWER', referenceAnswer: value }))
  assert.throws(() => buildSubmission([questions[2]], { 3: value }))
  const limit = ` ${'x'.repeat(3998)} `
  assert.equal(buildQuestionPayload({ text: 'Câu hỏi', type: 'SHORT_ANSWER', referenceAnswer: limit }).referenceAnswer, limit)
  assert.equal(buildSubmission([questions[2]], { 3: limit }).answers[0].answerText, limit)
})

test('submission rejects missing answers, empty quizzes and blank or oversized free text', () => {
  assert.throws(() => buildSubmission([], {}))
  assert.throws(() => buildSubmission(questions, { 1: [11], 2: [21] }), /3/)
  assert.throws(() => buildSubmission(questions, { 1: [11], 2: [21], 3: ' ' }))
  assert.throws(() => buildSubmission(questions, { 1: [11], 2: [21], 3: 'x'.repeat(4001) }))
})

test('submission rejects foreign or duplicate options and multiple true false choices', () => {
  const answers = { 1: [11], 2: [21], 3: 'Lời giải' }
  assert.throws(() => buildSubmission(questions, { ...answers, 1: [21] }))
  assert.throws(() => buildSubmission(questions, { ...answers, 1: [11, 11] }))
  assert.throws(() => buildSubmission(questions, { ...answers, 2: [21, 22] }))
})
