import { useState } from 'react'
import { chatCourse } from '../../api/courseAiApi.js'
import { ApiError } from '../../api/authApi.js'
import { useAiRequest } from '../../hooks/useAiRequest.js'
import { validateAiInput, validateChatResponse } from './aiDisplay.js'
import { CitationList } from './CitationList.jsx'

export function CourseChatPanel({ courseId, token, onOpenSource }) {
  const [question, setQuestion] = useState('')
  const [validation, setValidation] = useState('')
  const state = useAiRequest('chat')
  async function submit(event) {
    event.preventDefault()
    const error = validateAiInput(question)
    setValidation(error)
    if (error) return
    const submitted = question.trim()
    await state.run(async () => {
      const response = await chatCourse(token, courseId, { question: submitted })
      if (String(response.courseId) !== String(courseId) || !validateChatResponse(response)) throw new ApiError('Invalid chat response', 502)
      return { ...response, question: submitted }
    })
  }
  return <section aria-label="RAG Chat" className="ai-section">
    <h3>Hỏi đáp theo tài liệu khóa học</h3>
    <p className="inline-help">Mỗi câu hỏi được xử lý độc lập. Câu hỏi trước không được gửi kèm và chưa có lịch sử hội thoại.</p>
    <form className="ai-form" onSubmit={submit}>
      <label className="field"><span className="field-label">Câu hỏi</span><textarea rows={4} value={question} onChange={event => setQuestion(event.target.value)} maxLength={16384} /></label>
      <button className="primary-button" disabled={state.pending} type="submit">{state.pending ? 'Đang trả lời…' : 'Gửi câu hỏi'}</button>
    </form>
    {(validation || state.error) && <p className="form-message error" role="alert">{validation || state.error}</p>}
    {state.pending && <p role="status">Đang đọc tài liệu và soạn câu trả lời…</p>}
    {state.data && <article className="ai-answer" aria-live="polite">
      <p className="ai-question"><strong>Câu hỏi:</strong> {state.data.question}</p>
      <h4>{state.data.status === 'ANSWERED' ? 'Câu trả lời' : 'Chưa đủ thông tin'}</h4>
      <p className="ai-text" data-testid="chat-answer">{state.data.answer}</p>
      {state.data.citations.length > 0 && <CitationList courseId={courseId} citations={state.data.citations} onOpenSource={onOpenSource} />}
    </article>}
  </section>
}
