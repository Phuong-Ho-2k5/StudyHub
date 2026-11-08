import { useCallback, useEffect, useRef, useState } from 'react'
import { chatCourse, listChatMessages } from '../../api/courseAiApi.js'
import { ApiError } from '../../api/authApi.js'
import { aiErrorMessage, validateAiInput, validateChatResponse } from './aiDisplay.js'
import { mergeMessages, validHistoryId, validateMessagePage } from './chatHistory.js'
import { ChatMessageList } from './ChatMessageList.jsx'

export function ChatSessionPanel({ courseId, token, session, busy, onBusyChange, onTurnSaved, onOpenSource, onScopeChange }) {
  const [messages, setMessages] = useState([])
  const [paging, setPaging] = useState({ next: 0, last: false })
  const [loading, setLoading] = useState(true)
  const [readError, setReadError] = useState('')
  const [chatError, setChatError] = useState('')
  const [question, setQuestion] = useState('')
  const [pending, setPending] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const sequence = useRef(0)
  const lock = useRef(false)

  const load = useCallback(async (page, replace = false) => {
    if (lock.current) return
    const generation = ++sequence.current
    setLoading(true)
    setReadError('')
    if (replace) { setMessages([]); setLoaded(false); setPaging({ next: 0, last: false }) }
    try {
      const data = await listChatMessages(token, courseId, session.id, { page })
      if (!validateMessagePage(data, session.id) || data.number !== page) throw new ApiError('Invalid messages', 502)
      if (generation !== sequence.current) return
      setMessages(current => mergeMessages(replace ? [] : current, data.content))
      setPaging({ next: page + 1, last: data.last })
      setLoaded(true)
      setUncertain(false)
      setChatError('')
    } catch (caught) {
      if (generation === sequence.current) setReadError(aiErrorMessage(caught, 'history'))
    } finally {
      if (generation === sequence.current) setLoading(false)
    }
  }, [token, courseId, session.id])

  useEffect(() => {
    const requests = sequence
    const timer = setTimeout(() => load(0, true), 0)
    return () => { clearTimeout(timer); requests.current++ }
  }, [load])

  async function submit(event) {
    event.preventDefault()
    if (lock.current || loading || !loaded || !paging.last || uncertain || readError) return
    const validation = validateAiInput(question)
    setChatError(validation)
    if (validation) return
    if (!onBusyChange(true)) return
    const submitted = question.trim()
    const generation = sequence.current
    lock.current = true
    setPending(true)
    try {
      const data = await chatCourse(token, courseId, { question: submitted, sessionId: session.id })
      if (String(data.courseId) !== String(courseId) || data.sessionId !== session.id || !validateChatResponse(data) ||
        !validHistoryId(data.userMessageId) || !validHistoryId(data.assistantMessageId) || data.userMessageId >= data.assistantMessageId) {
        throw new ApiError('Invalid saved turn', 502)
      }
      if (generation !== sequence.current) return
      const common = { sessionId: session.id, createdAt: null, feedback: null }
      setMessages(current => mergeMessages(current, [
        { ...common, id: data.userMessageId, role: 'USER', content: submitted, status: null, model: null, promptVersion: null, citations: [] },
        { ...common, id: data.assistantMessageId, role: 'ASSISTANT', content: data.answer, status: data.status, model: data.model, promptVersion: data.promptVersion, citations: data.citations },
      ]))
      setQuestion('')
      // All server pages were read before sending; append cannot leave an unloaded gap.
      onTurnSaved({ sessionId: session.id, userMessageId: data.userMessageId, assistantMessageId: data.assistantMessageId })
    } catch (caught) {
      if (generation === sequence.current) {
        const unknown = !caught.status || caught.status >= 500
        setUncertain(unknown)
        const reason = caught.status === 504 ? 'Quá thời gian chờ phản hồi.' : aiErrorMessage(caught)
        setChatError(unknown ? `${reason} Chưa xác định câu hỏi đã được lưu hay chưa. Hãy tải lại lịch sử trước khi gửi lại.` : reason)
      }
    } finally {
      lock.current = false
      if (generation === sequence.current) { setPending(false); onBusyChange(false) }
    }
  }

  return <section className="chat-session-panel" aria-label="Hội thoại đang mở">
    <div className="chat-session-heading"><h4>{session.title}</h4>
      <button type="button" className="secondary-button" disabled={busy || loading} onClick={() => { onScopeChange(); load(0, true) }}>Tải lại lịch sử</button>
    </div>
    {loading && <p role="status">Đang tải tin nhắn…</p>}
    {readError && <p role="alert" className="form-message error">{readError}</p>}
    <ChatMessageList messages={messages} courseId={courseId} token={token} onOpenSource={onOpenSource} />
    {loaded && !loading && !readError && messages.length === 0 && <p>Hội thoại chưa có tin nhắn.</p>}
    {!paging.last && <button type="button" className="secondary-button" disabled={busy || loading} onClick={() => load(paging.next)}>Tải thêm tin nhắn</button>}
    {!paging.last && <p className="inline-help">Tải hết tin nhắn để tiếp tục hội thoại.</p>}
    <form className="ai-form" onSubmit={submit}>
      <label className="field"><span className="field-label">Câu hỏi</span><textarea rows={4} value={question} disabled={busy} onChange={event => setQuestion(event.target.value)} maxLength={16384} /></label>
      <button className="primary-button" disabled={busy || loading || !loaded || !paging.last || uncertain || !!readError} type="submit">{pending ? 'Đang trả lời…' : 'Gửi câu hỏi'}</button>
    </form>
    {chatError && <p className="form-message error" role="alert">{chatError}</p>}
    {pending && <p role="status">Đang đọc tài liệu và soạn câu trả lời…</p>}
  </section>
}
