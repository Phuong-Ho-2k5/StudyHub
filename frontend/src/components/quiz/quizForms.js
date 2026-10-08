export const QUESTION_TYPES = [
  ['MULTIPLE_CHOICE', 'Trắc nghiệm'], ['TRUE_FALSE', 'Đúng / Sai'], ['SHORT_ANSWER', 'Tự luận ngắn'],
]
export const QUESTION_LABELS = Object.fromEntries(QUESTION_TYPES)
export const QUIZ_STATUSES = [['DRAFT', 'Bản nháp'], ['PUBLISHED', 'Đã xuất bản'], ['ARCHIVED', 'Đã lưu trữ']]
export const QUIZ_LABELS = Object.fromEntries(QUIZ_STATUSES)

export function buildQuestionPayload(draft) {
  const text = draft.text.trim()
  if (!text || text.length > 500) throw new Error('Nội dung câu hỏi phải có từ 1 đến 500 ký tự.')
  if (!QUESTION_LABELS[draft.type]) throw new Error('Loại câu hỏi không hợp lệ.')
  const conceptId = draft.conceptId === '' || draft.conceptId == null ? null : Number(draft.conceptId)
  if (conceptId !== null && (!Number.isSafeInteger(conceptId) || conceptId < 1)) {
    throw new Error('Khái niệm không hợp lệ.')
  }
  if (draft.type === 'SHORT_ANSWER') {
    const referenceAnswer = (draft.referenceAnswer ?? '').trim()
    if (!referenceAnswer || referenceAnswer.length > 4000) throw new Error('Nhập đáp án tham chiếu từ 1 đến 4.000 ký tự.')
    return { text, type: draft.type, conceptId, referenceAnswer, options: [] }
  }
  const options = (draft.options ?? []).map((item) => ({ text: item.text.trim(), correct: Boolean(item.correct) }))
  if (options.some((item) => !item.text || item.text.length > 500)) throw new Error('Mỗi lựa chọn phải có từ 1 đến 500 ký tự.')
  const correctCount = options.filter((item) => item.correct).length
  if (draft.type === 'MULTIPLE_CHOICE' && (options.length < 4 || correctCount < 1)) {
    throw new Error('Trắc nghiệm cần ít nhất 4 lựa chọn và ít nhất 1 đáp án đúng.')
  }
  if (draft.type === 'TRUE_FALSE' && (options.length !== 2 || correctCount !== 1)) {
    throw new Error('Đúng / Sai cần 2 lựa chọn và đúng 1 đáp án đúng.')
  }
  return { text, type: draft.type, conceptId, referenceAnswer: null, options }
}

export function buildSubmission(questions, answers) {
  if (!questions.length) throw new Error('Bài kiểm tra chưa có câu hỏi.')
  return { answers: questions.map((question, index) => {
    const value = answers[question.id]
    const fail = () => { throw new Error(`Vui lòng trả lời hợp lệ câu ${index + 1} trước khi nộp bài.`) }
    if (question.type === 'SHORT_ANSWER') {
      if (typeof value !== 'string' || !value.trim() || value.trim().length > 4000) fail()
      return { questionId: question.id, answerText: value.trim() }
    }
    if (!Array.isArray(value) || !value.length || new Set(value).size !== value.length
      || value.some((id) => !question.options.some((option) => option.id === id))
      || question.type === 'TRUE_FALSE' && value.length !== 1) fail()
    return { questionId: question.id, answerOptionIds: [...value] }
  }) }
}
