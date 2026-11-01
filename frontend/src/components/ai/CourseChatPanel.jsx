import { useCallback, useEffect, useRef, useState } from 'react'
import { createChatSession, listChatSessions } from '../../api/courseAiApi.js'
import { ApiError } from '../../api/authApi.js'
import { aiErrorMessage } from './aiDisplay.js'
import { validateSession, validateSessionPage } from './chatHistory.js'
import { ChatSessionList } from './ChatSessionList.jsx'
import { ChatSessionPanel } from './ChatSessionPanel.jsx'

export function CourseChatPanel({ courseId, token, onOpenSource, onScopeChange }) {
  const [list, setList] = useState({ content: [], number: 0, totalPages: 0 })
  const [pending, setPending] = useState(true)
  const [error, setError] = useState('')
  const [createError, setCreateError] = useState('')
  const [uncertainCreate, setUncertainCreate] = useState(false)
  const [session, setSession] = useState(null)
  const [busy, setBusyState] = useState(false)
  const busyRef = useRef(false)
  const sequence = useRef(0)
  const life = useRef(0)
  const retryPage = useRef(0)
  const setBusy = useCallback(value => {
    if (value && busyRef.current) return false
    busyRef.current = value
    setBusyState(value)
    return true
  }, [])

  const load = useCallback(async page => {
    const request = ++sequence.current
    retryPage.current = page
    setPending(true)
    setError('')
    try {
      const data = await listChatSessions(token, courseId, { page })
      if (!validateSessionPage(data, courseId) || data.number !== page) throw new ApiError('Invalid history response', 502)
      if (request !== sequence.current) return
      setList(data)
      if (page === 0) setUncertainCreate(false)
    } catch (caught) {
      if (request === sequence.current) setError(aiErrorMessage(caught, 'history'))
    } finally {
      if (request === sequence.current) setPending(false)
    }
  }, [courseId, token])

  useEffect(() => {
    const requests = sequence
    const lifecycle = life
    const timer = setTimeout(() => load(0), 0)
    return () => { clearTimeout(timer); requests.current++; lifecycle.current++ }
  }, [load])

  function select(item) {
    if (busyRef.current || session?.id === item.id) return
    onScopeChange()
    setSession(item)
  }

  async function create(title) {
    if (busyRef.current || uncertainCreate) return false
    const generation = life.current
    setBusy(true)
    // An older list read must not clear an ambiguous create result.
    sequence.current++
    setPending(false)
    setCreateError('')
    try {
      const data = await createChatSession(token, courseId, { title })
      if (!validateSession(data, courseId)) throw new ApiError('Invalid created session', 502)
      if (generation !== life.current) return false
      onScopeChange()
      setSession(data)
      load(0)
      return true
    } catch (caught) {
      if (generation === life.current) {
        const uncertain = !caught.status || caught.status >= 500
        setUncertainCreate(uncertain)
        setCreateError(uncertain ? 'Chưa xác định hội thoại đã được tạo hay chưa. Hãy tải lại danh sách trước khi tạo lại.' : aiErrorMessage(caught, 'history'))
      }
      return false
    } finally {
      if (generation === life.current) setBusy(false)
    }
  }
  return <section aria-label="RAG Chat" className="ai-section">
    <h3>Hỏi đáp theo tài liệu khóa học</h3>
    <p className="inline-help">Lịch sử được lưu. Mỗi câu hỏi vẫn được xử lý độc lập; AI chưa dùng nội dung các lượt trước.</p>
    <div className="chat-history-layout">
      <ChatSessionList sessions={list.content} activeSessionId={session?.id} page={list.number} totalPages={list.totalPages}
        pending={pending} error={error} createError={createError} busy={busy} uncertainCreate={uncertainCreate}
        onPageChange={load} onRetry={() => { setCreateError(''); load(uncertainCreate ? 0 : retryPage.current) }} onSelect={select} onCreate={create} />
      {session ? <ChatSessionPanel key={session.id} courseId={courseId} token={token} session={session}
        busy={busy} onBusyChange={setBusy} onTurnSaved={() => load(0)} onOpenSource={onOpenSource} onScopeChange={onScopeChange} />
        : <p className="chat-empty">Chọn một hội thoại hoặc tạo hội thoại mới để bắt đầu.</p>}
    </div>
  </section>
}
