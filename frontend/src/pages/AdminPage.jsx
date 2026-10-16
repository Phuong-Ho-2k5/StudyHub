import { useEffect, useRef, useState } from 'react'
import { getAdminStatistics, getUser, listUsers, updateUserRole } from '../api/adminApi'
import { errorMessage } from '../api/learningDisplay'
import { useAuth } from '../auth/AuthContext'
import { useLearning } from '../context/LearningContext'
import { useRemoteData } from '../hooks/useRemoteData'
import { AppHeader } from '../components/AppHeader'
import { Pagination } from '../components/Pagination'
import { RemoteState } from '../components/RemoteState'
import { EditorDialog } from '../components/quiz/EditorDialog'

function UserDetails({ id, onClose, onDenied }) {
  const { token } = useAuth()
  const state = useRemoteData(() => getUser(token, id), [token, id])
  useEffect(() => { if (state.error?.status === 403) onDenied() }, [state.error, onDenied])
  return <EditorDialog title="Chi tiết người dùng" busy={false} onClose={onClose}>
    <RemoteState {...state} onRetry={state.reload}>{state.data && <dl className="user-details"><dt>Mã người dùng</dt><dd>#{state.data.id}</dd><dt>Họ tên</dt><dd>{state.data.name}</dd><dt>Email</dt><dd>{state.data.email}</dd><dt>Quyền</dt><dd>{state.data.role}</dd></dl>}</RemoteState>
  </EditorDialog>
}

function AdminContent() {
  const { token, user } = useAuth()
  const { adminState, denyAdmin } = useLearning()
  const [page, setPage] = useState(0)
  const [detailId, setDetailId] = useState(null)
  const [change, setChange] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const lock = useRef(false)
  const state = useRemoteData(async () => {
    const statistics = await getAdminStatistics(token)
    const users = await listUsers(token, page)
    return { statistics, users }
  }, [token, page])
  const denied = adminState === 'denied' || state.error?.status === 403
  useEffect(() => { if (state.error?.status === 403) denyAdmin() }, [state.error, denyAdmin])
  async function saveRole() {
    if (lock.current) return
    lock.current = true; setBusy(true); setError('')
    try { await updateUserRole(token, change.item.id, change.role); setChange(null); state.reload() }
    catch (caught) { if (caught.status === 403) denyAdmin(); setError(errorMessage(caught)) }
    finally { lock.current = false; setBusy(false) }
  }
  return <main className="dashboard-page"><AppHeader /><div className="detail-shell">
    <div className="page-heading"><div><p className="section-kicker">Quản trị hệ thống</p><h1>Người dùng và thống kê</h1><p>Quản lý quyền truy cập StudyHub.</p></div>{!denied && <button className="secondary-button" type="button" onClick={state.reload} disabled={state.loading || busy} aria-label="Làm mới dữ liệu quản trị">Làm mới</button>}</div>
    {denied ? <p className="form-message error" role="alert">Bạn không có quyền quản trị.</p> : <RemoteState {...state} onRetry={state.reload}>{state.data && <>
      <div className="stat-grid admin-stat-grid">{[['Người dùng', state.data.statistics.totalUsers], ['Không gian', state.data.statistics.totalWorkspaces], ['Khóa học', state.data.statistics.totalCourses]].map(([label, value]) => <article className="stat-card" key={label}><span>{label}</span><strong>{value}</strong></article>)}</div>
      <section className="course-panel learning-section"><h2>Danh sách người dùng</h2>
        {!state.data.users.content.length ? <p className="empty-state">Chưa có người dùng.</p> : <div className="table-scroll"><table className="data-table"><thead><tr><th>Mã</th><th>Họ tên</th><th>Email</th><th>Quyền</th><th>Thao tác</th></tr></thead><tbody>{state.data.users.content.map((item) => <tr key={item.id}>
          <td>#{item.id}</td><td>{item.name}</td><td>{item.email}</td><td><span className="status-badge">{item.role}</span></td><td><div className="course-actions">
            <button type="button" onClick={() => setDetailId(item.id)} disabled={busy} aria-label={`Chi tiết người dùng ${item.id}`}>Chi tiết</button>
            <button type="button" disabled={busy || item.email === user.email} onClick={() => { setError(''); setChange({ item, role: item.role === 'ADMIN' ? 'USER' : 'ADMIN' }) }} aria-label={`Đổi quyền người dùng ${item.id}`}>Đổi quyền</button>
          </div></td></tr>)}</tbody></table></div>}
        <Pagination page={page} totalPages={state.data.users.totalPages} busy={state.loading || busy} onChange={setPage} />
      </section>
    </>}</RemoteState>}
    {!denied && detailId !== null && <UserDetails id={detailId} onClose={() => setDetailId(null)} onDenied={denyAdmin} />}
    {!denied && change && <EditorDialog title="Xác nhận đổi quyền" busy={busy} onClose={() => setChange(null)}>
      <div className="editor-form"><p>Đổi quyền của <strong>{change.item.email}</strong> từ {change.item.role} sang <strong>{change.role}</strong>?</p><p className="muted">Quyền mới có hiệu lực từ yêu cầu tiếp theo.</p>
        {error && <p className="form-message error" role="alert">{error}</p>}
        <div className="dialog-actions"><button className="secondary-button" type="button" disabled={busy} onClick={() => setChange(null)}>Hủy</button><button className="primary-button" type="button" disabled={busy} onClick={saveRole}>{busy ? 'Đang lưu…' : 'Xác nhận đổi quyền'}</button></div>
      </div></EditorDialog>}
  </div></main>
}

export function AdminPage() {
  const { adminState, checkAdmin } = useLearning()
  // Unmount protected page/dialog state on denial, rather than merely hiding cached data.
  if (adminState === 'allowed') return <AdminContent />
  return <main className="dashboard-page"><AppHeader /><div className="detail-shell">
    <div className="page-heading"><div><p className="section-kicker">Quản trị hệ thống</p><h1>Người dùng và thống kê</h1></div></div>
    {adminState === 'denied' ? <p className="form-message error" role="alert">Bạn không có quyền quản trị.</p>
      : adminState === 'error' ? <div className="remote-error"><p className="form-message error" role="alert">Không thể kiểm tra quyền quản trị.</p><button className="secondary-button" type="button" onClick={checkAdmin}>Thử lại</button></div>
        : <p className="list-state" role="status">Đang kiểm tra quyền quản trị…</p>}
  </div></main>
}
