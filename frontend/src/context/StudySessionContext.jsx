/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { startSession, finishSession } from '../api/learningApi'
import { readSession, saveSession, clearSession, sessionKey, withSessionLock } from '../api/sessionStorage'
import { errorMessage } from '../api/learningDisplay'
import { useAuth } from '../auth/AuthContext'
import { useLearning } from './LearningContext'

const StudySessionContext = createContext(null)

export function StudySessionProvider({ children }) {
  const { token, user } = useAuth()
  const { invalidateLearning } = useLearning()
  const [session, setSession] = useState(() => readSession(localStorage, user.email))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [unavailable, setUnavailable] = useState(false)
  const lock = useRef(false)
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    const sync = (event) => {
      if (event.key === sessionKey(user.email) || event.key === null) {
        setSession(readSession(localStorage, user.email)); setUnavailable(false); setError('')
      }
    }
    window.addEventListener('storage', sync)
    return () => { mounted.current = false; window.removeEventListener('storage', sync) }
  }, [user.email])

  function forget(expectedId = session?.id) {
    const current = readSession(localStorage, user.email)
    setSession(current && current.id !== expectedId ? current : null); setUnavailable(false)
    try { clearSession(localStorage, user.email, expectedId) }
    catch { setError('Đã kết thúc theo dõi. Trình duyệt chưa xóa được bản lưu phiên; hãy kiểm tra quyền lưu trữ trước khi tải lại trang.') }
  }
  async function start(courseId) {
    if (lock.current || session) return
    lock.current = true; setBusy(true); setError('')
    try {
      await withSessionLock(user.email, async () => {
        if (!mounted.current) return
        const stored = readSession(localStorage, user.email)
        if (stored) { setSession(stored); return }
        const data = await startSession(token, courseId)
        // Persist for the initiating account even when it logged out during the request.
        try { saveSession(localStorage, user.email, data) }
        catch { if (mounted.current) setError('Phiên đã bắt đầu nhưng trình duyệt không lưu được. Hãy kết thúc phiên trước khi đóng trang.') }
        if (mounted.current) setSession(data)
      })
    } catch (caught) {
      if (mounted.current) setError(caught.status === 0 || caught.status >= 500
        ? 'Chưa xác định được phiên đã bắt đầu hay chưa. Thao tác lại có thể tạo phiên mới. Hãy kiểm tra kết nối trước khi thử lại.' : errorMessage(caught))
    } finally { lock.current = false; if (mounted.current) setBusy(false) }
  }
  async function finish() {
    if (lock.current || !session) return
    lock.current = true; setBusy(true); setError('')
    try {
      await withSessionLock(user.email, async () => {
        await finishSession(token, session.id)
        if (mounted.current) { forget(session.id); invalidateLearning() }
        else { try { clearSession(localStorage, user.email, session.id) } catch { /* retain tracking for next login */ } }
      })
    } catch (caught) {
      if (!mounted.current) return
      if (caught.status === 409) { forget(session.id); invalidateLearning(); setError('Phiên này đã được kết thúc trước đó.') }
      else { setError(errorMessage(caught)); setUnavailable(caught.status === 404) }
    } finally { lock.current = false; if (mounted.current) setBusy(false) }
  }
  return <StudySessionContext.Provider value={{ session, busy, error, start, finish, unavailable, dismissUnavailable: () => forget() }}>{children}</StudySessionContext.Provider>
}
export const useStudySession = () => useContext(StudySessionContext)
