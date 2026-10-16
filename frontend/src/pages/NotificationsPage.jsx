import { useEffect, useRef, useState } from 'react'
import { listNotifications, markRead } from '../api/notificationApi'
import { errorMessage, formatDate } from '../api/learningDisplay'
import { useAuth } from '../auth/AuthContext'
import { useLearning } from '../context/LearningContext'
import { useRemoteData } from '../hooks/useRemoteData'
import { AppHeader } from '../components/AppHeader'
import { Pagination } from '../components/Pagination'
import { RemoteState } from '../components/RemoteState'

export function NotificationsPage() {
  const { token } = useAuth()
  const { refreshNotifications, learningRevision } = useLearning()
  const [page, setPage] = useState(0)
  const [busyId, setBusyId] = useState(null)
  const [error, setError] = useState('')
  const lock = useRef(false)
  const state = useRemoteData(() => listNotifications(token, page), [token, page, learningRevision])
  useEffect(() => { refreshNotifications() }, [refreshNotifications])
  async function read(item) {
    if (lock.current) return
    lock.current = true; setBusyId(item.id); setError('')
    try { await markRead(token, item.id); state.reload(); refreshNotifications() }
    catch (caught) { setError(errorMessage(caught)) }
    finally { lock.current = false; setBusyId(null) }
  }
  function reload() { state.reload(); refreshNotifications() }
  return <main className="dashboard-page"><AppHeader /><div className="detail-shell">
    <div className="page-heading"><div><p className="section-kicker">Cập nhật học tập</p><h1>Thông báo</h1><p>Theo dõi kết quả và những cập nhật mới của bạn.</p></div><button className="secondary-button" type="button" onClick={reload} disabled={state.loading}>Làm mới</button></div>
    <section className="course-panel">
      {error && <p className="form-message error" role="alert">{error}</p>}
      <RemoteState {...state} onRetry={reload} empty={state.data?.content.length === 0 && 'Bạn chưa có thông báo nào.'}>
        <div className="notification-list">{state.data?.content.map((item) => <article className={`notification-card ${item.read ? '' : 'is-unread'}`} key={item.id}>
          <div><span className={`status-badge ${item.read ? '' : 'unread-badge'}`}>{item.read ? 'Đã đọc' : 'Chưa đọc'}</span><p className="prose-content">{item.content}</p><time className="muted" dateTime={item.createdAt}>{formatDate(item.createdAt)}</time></div>
          {!item.read && <button className="secondary-button" type="button" onClick={() => read(item)} disabled={busyId !== null}>{busyId === item.id ? 'Đang lưu…' : 'Đánh dấu đã đọc'}</button>}
        </article>)}</div>
      </RemoteState>
      <Pagination page={page} totalPages={state.data?.totalPages ?? 0} busy={state.loading || busyId !== null} onChange={setPage} />
    </section>
  </div></main>
}
