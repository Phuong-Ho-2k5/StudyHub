import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/authApi'
import { createCourse, createWorkspace, deleteCourse, deleteWorkspace, listCourses, listWorkspaces, updateCourse, updateWorkspace } from '../api/studyApi'
import { useAuth } from '../auth/AuthContext'

const STATUSES = [['PLANNED', 'Dự định'], ['IN_PROGRESS', 'Đang học'], ['COMPLETED', 'Hoàn thành'], ['ARCHIVED', 'Lưu trữ']]
const STATUS_LABELS = Object.fromEntries(STATUSES)

function errorMessage(error) {
  if (error instanceof ApiError && error.status === 409) return 'Tên này đã được sử dụng. Hãy chọn tên khác.'
  return error instanceof ApiError ? error.message : 'Có lỗi xảy ra. Vui lòng thử lại.'
}

function Editor({ kind, item, workspaceId, token, onClose, onSaved }) {
  const isWorkspace = kind === 'workspace'
  const [name, setName] = useState(item?.name ?? '')
  const [description, setDescription] = useState(item?.description ?? '')
  const [status, setStatus] = useState(item?.status ?? 'PLANNED')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const noun = isWorkspace ? 'không gian' : 'khóa học'

  async function save(event) {
    event.preventDefault()
    const data = { name: name.trim(), description: description.trim() }
    if (!data.name || !data.description) return setError('Vui lòng nhập tên và mô tả.')
    setSaving(true)
    setError('')
    try {
      let saved
      if (isWorkspace) {
        if (item) saved = await updateWorkspace(token, item.id, data)
        else saved = await createWorkspace(token, data)
      } else if (item) saved = await updateCourse(token, item.id, { ...data, status })
      else saved = await createCourse(token, workspaceId, data)
      onSaved(isWorkspace && !item ? saved.id : null)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setSaving(false)
    }
  }

  return <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <section className="editor-dialog" role="dialog" aria-modal="true" aria-labelledby="editor-title">
      <div className="dialog-heading"><div><p className="section-kicker">{item ? 'Chỉnh sửa' : 'Tạo mới'}</p><h2 id="editor-title">{item ? 'Sửa' : 'Thêm'} {noun}</h2></div><button className="icon-button" type="button" onClick={onClose} aria-label="Đóng" disabled={saving}>×</button></div>
      <form className="editor-form" onSubmit={save}>
        {error && <p className="form-message error" role="alert">{error}</p>}
        <label className="field"><span className="field-label">Tên {noun}</span><input value={name} onChange={(event) => setName(event.target.value)} maxLength={isWorkspace ? 100 : 150} required autoFocus disabled={saving} /></label>
        <label className="field"><span className="field-label">Mô tả</span><textarea value={description} onChange={(event) => setDescription(event.target.value)} maxLength={isWorkspace ? 500 : 1000} rows="4" required disabled={saving} /></label>
        {!isWorkspace && item && <label className="field"><span className="field-label">Trạng thái</span><select value={status} onChange={(event) => setStatus(event.target.value)} disabled={saving}>{STATUSES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>}
        <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose} disabled={saving}>Hủy</button><button className="primary-button" type="submit" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu'}</button></div>
      </form>
    </section>
  </div>
}

export function DashboardPage() {
  const navigate = useNavigate()
  const { user, token, logout } = useAuth()
  const [workspaces, setWorkspaces] = useState([])
  const [workspaceId, setWorkspaceId] = useState('')
  const [workspaceLoading, setWorkspaceLoading] = useState(true)
  const [workspaceError, setWorkspaceError] = useState('')
  const [courses, setCourses] = useState([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [coursesLoading, setCoursesLoading] = useState(true)
  const [coursesError, setCoursesError] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [editor, setEditor] = useState(null)
  const [actionError, setActionError] = useState('')
  const [deletingId, setDeletingId] = useState(null)
  const selectedWorkspace = workspaces.find((space) => String(space.id) === workspaceId)

  useEffect(() => {
    let active = true
    listWorkspaces(token).then((data) => { if (active) { setWorkspaces(data); setWorkspaceError('') } })
      .catch((error) => { if (active) setWorkspaceError(errorMessage(error)) })
      .finally(() => { if (active) setWorkspaceLoading(false) })
    return () => { active = false }
  }, [token, revision])

  useEffect(() => {
    let active = true
    listCourses(token, { workspaceId, status, q: query, page }).then((data) => {
      if (active) { setCourses(data.content ?? []); setTotalElements(data.totalElements ?? 0); setTotalPages(data.totalPages ?? 0); setCoursesError('') }
    }).catch((error) => { if (active) setCoursesError(errorMessage(error)) })
      .finally(() => { if (active) setCoursesLoading(false) })
    return () => { active = false }
  }, [token, workspaceId, status, query, page, revision])

  function refresh(newWorkspaceId = null) {
    if (newWorkspaceId) { setWorkspaceId(String(newWorkspaceId)); setPage(0) }
    setRevision((value) => value + 1)
    setEditor(null)
    setActionError('')
    setCoursesLoading(true)
  }
  function selectWorkspace(id) { if (id !== workspaceId || page !== 0) { setWorkspaceId(id); setPage(0); setCoursesLoading(true) } }
  function selectStatus(value) { if (value !== status || page !== 0) { setStatus(value); setPage(0); setCoursesLoading(true) } }
  function search(event) { event.preventDefault(); const next = searchInput.trim(); if (next !== query || page !== 0) { setQuery(next); setPage(0); setCoursesLoading(true) } }

  async function remove(kind, item) {
    const detail = kind === 'workspace' ? ' và toàn bộ khóa học trong đó' : ''
    if (!window.confirm(`Xóa “${item.name}”${detail}? Hành động này không thể hoàn tác.`)) return
    setDeletingId(`${kind}-${item.id}`)
    setActionError('')
    try {
      if (kind === 'workspace') { await deleteWorkspace(token, item.id); if (workspaceId === String(item.id)) selectWorkspace('') }
      else { await deleteCourse(token, item.id); if (courses.length === 1 && page > 0) setPage((value) => value - 1) }
      refresh()
    } catch (error) { setActionError(errorMessage(error)) }
    finally { setDeletingId(null) }
  }

  function signOut() { logout(); navigate('/login', { replace: true }) }

  return <main className="dashboard-page">
    <header className="dashboard-header"><div className="brand"><span className="brand-mark" aria-hidden="true">S</span><span>StudyHub</span></div><div className="header-account"><span title={user.email}>{user.email}</span><button className="secondary-button" type="button" onClick={signOut}>Đăng xuất</button></div></header>
    <div className="dashboard-shell">
      <aside className="workspace-sidebar" aria-label="Không gian học tập">
        <div className="sidebar-heading"><div><p className="section-kicker">Tổ chức việc học</p><h2>Không gian</h2></div><button className="small-add" type="button" onClick={() => setEditor({ kind: 'workspace' })} aria-label="Thêm không gian">+</button></div>
        {workspaceLoading && <p className="muted">Đang tải không gian…</p>}
        {workspaceError && <p className="form-message error" role="alert">{workspaceError}</p>}
        {!workspaceLoading && !workspaceError && <nav className="workspace-list" aria-label="Chọn không gian">
          <button className={`workspace-item ${workspaceId === '' ? 'active' : ''}`} type="button" onClick={() => selectWorkspace('')}>Tất cả khóa học</button>
          {workspaces.map((space) => <div className={`workspace-row ${workspaceId === String(space.id) ? 'active' : ''}`} key={space.id}>
            <button className="workspace-item" type="button" onClick={() => selectWorkspace(String(space.id))} title={space.description}>{space.name}</button>
            <div className="row-actions"><button type="button" onClick={() => setEditor({ kind: 'workspace', item: space })} aria-label={`Sửa ${space.name}`}>Sửa</button><button type="button" onClick={() => remove('workspace', space)} aria-label={`Xóa ${space.name}`} disabled={deletingId === `workspace-${space.id}`}>Xóa</button></div>
          </div>)}</nav>}
        {!workspaceLoading && !workspaceError && workspaces.length === 0 && <p className="sidebar-hint">Tạo không gian đầu tiên để nhóm các khóa học của bạn.</p>}
      </aside>
      <section className="dashboard-main">
        <div className="page-heading"><div><p className="section-kicker">Bảng học tập</p><h1>{selectedWorkspace?.name ?? 'Khóa học của bạn'}</h1><p>{selectedWorkspace?.description ?? 'Theo dõi các khóa học trong mọi không gian.'}</p></div><button className="primary-button add-course" type="button" onClick={() => setEditor({ kind: 'course' })} disabled={!workspaceId}>+ Thêm khóa học</button></div>
        {!workspaceId && workspaces.length > 0 && <p className="inline-note">Chọn một không gian bên trái để thêm khóa học mới.</p>}
        {actionError && <p className="form-message error" role="alert">{actionError}</p>}
        <div className="course-panel"><div className="course-panel-top"><div><p className="section-kicker">Danh sách</p><h2>Khóa học <span className="count-pill">{totalElements}</span></h2></div></div>
          <div className="course-filters"><form onSubmit={search} className="search-form" role="search"><label className="sr-only" htmlFor="course-search">Tìm khóa học</label><input id="course-search" type="search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Tìm theo tên khóa học…" /><button className="secondary-button" type="submit">Tìm</button></form><label className="status-filter"><span className="sr-only">Lọc theo trạng thái</span><select value={status} onChange={(event) => selectStatus(event.target.value)}><option value="">Mọi trạng thái</option>{STATUSES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
          {coursesError && <p className="form-message error" role="alert">{coursesError}</p>}
          {coursesLoading && <p className="list-state">Đang tải khóa học…</p>}
          {!coursesLoading && !coursesError && courses.length === 0 && <div className="empty-state"><div className="empty-icon" aria-hidden="true">✦</div><h3>Chưa có khóa học nào</h3><p>{query || status ? 'Thử thay đổi từ khóa hoặc bộ lọc.' : workspaces.length === 0 ? 'Tạo không gian đầu tiên để bắt đầu.' : 'Chọn một không gian và thêm khóa học đầu tiên.'}</p></div>}
          {!coursesLoading && !coursesError && courses.length > 0 && <div className="course-list">{courses.map((course) => <article className="course-card" key={course.id}><div className="course-mark" aria-hidden="true">{course.name.charAt(0).toUpperCase()}</div><div className="course-copy"><div className="course-title-line"><h3>{course.name}</h3><span className={`status-badge status-${course.status.toLowerCase()}`}>{STATUS_LABELS[course.status] ?? course.status}</span></div><p>{course.description}</p></div><div className="course-actions"><button type="button" onClick={() => setEditor({ kind: 'course', item: course })}>Sửa</button><button type="button" onClick={() => remove('course', course)} disabled={deletingId === `course-${course.id}`}>Xóa</button></div></article>)}</div>}
          {!coursesLoading && !coursesError && totalPages > 1 && <div className="pagination"><span>Trang {page + 1} / {totalPages}</span><div><button className="secondary-button" type="button" disabled={page === 0} onClick={() => { setPage((value) => value - 1); setCoursesLoading(true) }}>Trước</button><button className="secondary-button" type="button" disabled={page + 1 >= totalPages} onClick={() => { setPage((value) => value + 1); setCoursesLoading(true) }}>Sau</button></div></div>}
        </div>
      </section>
    </div>
    {editor && <Editor key={`${editor.kind}-${editor.item?.id ?? 'new'}`} {...editor} workspaceId={workspaceId} token={token} onClose={() => setEditor(null)} onSaved={refresh} />}
  </main>
}
