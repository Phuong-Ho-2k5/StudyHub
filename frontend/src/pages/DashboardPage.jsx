import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/authApi'
import { createCourse, createWorkspace, deleteCourse, deleteWorkspace, listCourses, listWorkspaces, updateCourse, updateWorkspace } from '../api/studyApi'
import { useAuth } from '../auth/AuthContext'
import { AppHeader } from '../components/AppHeader'
import { Icon } from '../components/Icon'
import { StatisticsPanel } from '../components/learning/StatisticsPanel'
import { QuizHistoryPanel } from '../components/learning/QuizHistoryPanel'

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
  const { token } = useAuth()
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

  return <main className="dashboard-page">
    <AppHeader courses={courses} />
    <div className="dashboard-shell" id="main-content" tabIndex={-1}>
      <section className="dashboard-main">
        <div className="page-heading dashboard-greeting"><div><h1>Hôm nay, học một chút nhé.</h1><p>Một nền tảng vững bắt đầu từ những điều nhỏ.</p></div><button className="secondary-button" type="button" onClick={() => setEditor({ kind: 'workspace' })}><Icon name="plus" size={16} /> Tạo không gian</button></div>
        <StatisticsPanel />
        <div className="dashboard-content-grid">
          <section className="course-panel dashboard-course-panel" id="courses">
            <div className="section-heading"><div><p className="section-kicker">TỔ CHỨC VIỆC HỌC</p><h2>Không gian học tập</h2></div><span className="count-pill">{totalElements} khóa học</span></div>
            <div className="workspace-picker">
              <span className="space-icon"><Icon name="folder" size={20} /></span>
              <div><label className="field-label" htmlFor="workspace-select">Không gian của bạn</label><select id="workspace-select" value={workspaceId} onChange={(event) => selectWorkspace(event.target.value)} disabled={workspaceLoading}><option value="">Tất cả khóa học</option>{workspaces.map((space) => <option key={space.id} value={String(space.id)}>{space.name}</option>)}</select></div>
              {selectedWorkspace && <div className="workspace-manage"><button type="button" onClick={() => setEditor({ kind: 'workspace', item: selectedWorkspace })}>Sửa</button><button type="button" onClick={() => remove('workspace', selectedWorkspace)} disabled={deletingId === `workspace-${selectedWorkspace.id}`}>Xóa</button></div>}
            </div>
            {workspaceLoading && <p className="inline-help" role="status">Đang tải không gian…</p>}
            {workspaceError && <p className="form-message error" role="alert">{workspaceError}</p>}
            {selectedWorkspace && <p className="workspace-description">{selectedWorkspace.description}</p>}
            {!workspaceLoading && !workspaceError && workspaces.length === 0 && <div className="getting-started"><Icon name="folder" size={22} /><div><strong>Một không gian cho những điều muốn học</strong><p>Tạo không gian đầu tiên, sau đó thêm khóa học của bạn.</p></div><button className="secondary-button" type="button" onClick={() => setEditor({ kind: 'workspace' })}>Tạo không gian</button></div>}
            <div className="course-list-heading"><h3>{selectedWorkspace?.name ?? 'Khóa học của bạn'}</h3>{selectedWorkspace && <button className="primary-button detail-add" type="button" onClick={() => setEditor({ kind: 'course' })}><Icon name="plus" size={16} /> Thêm khóa học</button>}</div>
            {!workspaceId && workspaces.length > 0 && <p className="inline-help">Chọn một không gian ở trên để thêm khóa học mới.</p>}
            {actionError && <p className="form-message error" role="alert">{actionError}</p>}
            <div className="course-filters"><form onSubmit={search} className="search-form" role="search"><label className="sr-only" htmlFor="course-search">Tìm khóa học</label><div className="search-input-wrap"><Icon name="search" size={17} /><input id="course-search" type="search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Tìm theo tên khóa học…" /></div><button className="secondary-button" type="submit">Tìm</button></form><label className="status-filter"><span className="sr-only">Lọc theo trạng thái</span><select value={status} onChange={(event) => selectStatus(event.target.value)}><option value="">Mọi trạng thái</option>{STATUSES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
            {coursesError && <p className="form-message error" role="alert">{coursesError}</p>}
            {coursesLoading && <p className="list-state" role="status">Đang tải khóa học…</p>}
            {!coursesLoading && !coursesError && courses.length === 0 && <div className="empty-state"><span className="empty-icon"><Icon name="book" size={26} /></span><h3>Hành trình bắt đầu từ đây</h3><p>{query || status ? 'Chưa tìm thấy khóa học phù hợp. Thử thay đổi từ khóa hoặc bộ lọc.' : workspaces.length === 0 ? 'Tạo không gian đầu tiên để bắt đầu.' : 'Thêm một khóa học và dành chỗ cho những kiến thức mới.'}</p></div>}
            {!coursesLoading && !coursesError && courses.length > 0 && <div className="course-list">{courses.map((course, index) => <article className="course-card" key={course.id}>
              <div className="course-card-top"><span className={`course-mark course-color-${index % 3}`}><Icon name="book" size={22} /></span><span className={`status-badge status-${course.status.toLowerCase()}`}>{STATUS_LABELS[course.status] ?? course.status}</span></div>
              <div className="course-copy"><h3><Link to={`/courses/${course.id}`}>{course.name}</Link></h3><p>{course.description}</p></div>
              <div className="course-card-footer"><Link className="course-open" to={`/courses/${course.id}`}>Mở khóa học <Icon name="arrow" size={16} /></Link><div className="course-actions"><button type="button" onClick={() => setEditor({ kind: 'course', item: course })} aria-label={`Sửa ${course.name}`}>Sửa</button><button type="button" onClick={() => remove('course', course)} disabled={deletingId === `course-${course.id}`} aria-label={`Xóa ${course.name}`}>Xóa</button></div></div>
            </article>)}</div>}
            {!coursesLoading && !coursesError && totalPages > 1 && <div className="pagination"><span>Trang {page + 1} / {totalPages}</span><div><button className="secondary-button" type="button" disabled={page === 0} onClick={() => { setPage((value) => value - 1); setCoursesLoading(true) }}>Trước</button><button className="secondary-button" type="button" disabled={page + 1 >= totalPages} onClick={() => { setPage((value) => value + 1); setCoursesLoading(true) }}>Sau</button></div></div>}
          </section>
          <aside className="dashboard-aside" aria-label="Góc học tập">
            <section className="learning-note"><p className="section-kicker"><Icon name="spark" size={16} /> MỖI NGÀY MỘT CHÚT</p><h2>Học chắc nền tảng.<br />Tiến xa từng bước.</h2><p>Một khái niệm đã hiểu rõ hôm nay sẽ mở đường cho điều bạn muốn học ngày mai.</p><div className="note-divider" /><span className="note-caption"><Icon name="leaf" size={17} /> Một phiên học nhỏ cũng là tiến bộ.</span></section>
            <section className="course-panel learning-roadmap"><p className="section-kicker">MẠCH HỌC CỦA BẠN</p><h2>Từ sắp xếp đến hiểu sâu</h2><ol><li><span>01</span><div><strong>Tập hợp tài liệu</strong><p>Giữ nội dung theo từng khóa học.</p></div></li><li><span>02</span><div><strong>Kết nối khái niệm</strong><p>Hiểu điều cần học trước và sau.</p></div></li><li><span>03</span><div><strong>Luyện tập & nhìn lại</strong><p>Kiểm tra kiến thức, củng cố nền tảng.</p></div></li></ol></section>
          </aside>
        </div>
        <div className="encouragement"><Icon name="leaf" size={19} /><p>Giữ nhịp đều đặn. Một khoảng thời gian tập trung hôm nay sẽ giúp việc học ngày mai nhẹ nhàng hơn.</p></div>
        <div id="learning-history"><QuizHistoryPanel /></div>
        <footer className="app-footer"><span>StudyHub · Không gian học tập có chủ đích</span><span>Học theo nhịp của bạn.</span></footer>
      </section>
    </div>
    {editor && <Editor key={`${editor.kind}-${editor.item?.id ?? 'new'}`} {...editor} workspaceId={workspaceId} token={token} onClose={() => setEditor(null)} onSaved={refresh} />}
  </main>
}
