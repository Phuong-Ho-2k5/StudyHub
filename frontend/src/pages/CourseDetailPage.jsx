import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/authApi'
import {
  addPrerequisite, createConcept, createDocument, deleteConcept, deleteDocument,
  getCourse, listConcepts, listDocuments, listLowConfidenceConcepts,
  listPrerequisites, removePrerequisite, updateConcept, updateDocument,
} from '../api/studyApi'
import { useAuth } from '../auth/AuthContext'

const CONCEPT_STATUSES = [
  ['NEW', 'Mới'], ['LEARNING', 'Đang học'], ['UNDERSTOOD', 'Đã hiểu'], ['MASTERED', 'Thành thạo'],
]
const CONCEPT_LABELS = Object.fromEntries(CONCEPT_STATUSES)
const DOCUMENT_LABELS = { UPLOADING: 'Đang tải', READY: 'Sẵn sàng', PROCESSING: 'Đang xử lý', FAILED: 'Thất bại' }

function message(error) {
  if (error instanceof ApiError) {
    if (error.status === 404) return 'Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập.'
    if (error.details?.fieldErrors) return Object.values(error.details.fieldErrors).join('. ')
    return error.message
  }
  return 'Có lỗi xảy ra. Vui lòng thử lại.'
}

function ResourceDialog({ type, item, courseId, token, onClose, onSaved }) {
  const document = type === 'document'
  const [name, setName] = useState(document ? item?.title ?? '' : item?.name ?? '')
  const [description, setDescription] = useState(item?.description ?? '')
  const [fileName, setFileName] = useState(item?.fileName ?? '')
  const [fileType, setFileType] = useState(item?.fileType ?? '')
  const [storagePath, setStoragePath] = useState(item?.storagePath ?? '')
  const [confidence, setConfidence] = useState(item?.confidence ?? 0)
  const [status, setStatus] = useState(item?.status ?? 'NEW')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function save(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      if (document) {
        const data = { title: name.trim(), fileName: fileName.trim(), fileType: fileType.trim(), storagePath: storagePath.trim() }
        if (item) await updateDocument(token, item.id, data)
        else await createDocument(token, courseId, data)
      } else {
        const data = { name: name.trim(), description: description.trim(), confidence: Number(confidence), status }
        if (item) await updateConcept(token, item.id, data)
        else await createConcept(token, courseId, data)
      }
      onSaved()
    } catch (caught) {
      setError(message(caught))
    } finally {
      setSaving(false)
    }
  }

  return <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <section className="editor-dialog" role="dialog" aria-modal="true" aria-labelledby="resource-dialog-title">
      <div className="dialog-heading"><div><p className="section-kicker">{item ? 'Chỉnh sửa' : 'Tạo mới'}</p><h2 id="resource-dialog-title">{document ? 'Tài liệu' : 'Khái niệm'}</h2></div><button className="icon-button" type="button" onClick={onClose} disabled={saving} aria-label="Đóng">×</button></div>
      <form className="editor-form" onSubmit={save}>
        {error && <p className="form-message error" role="alert">{error}</p>}
        <label className="field"><span className="field-label">{document ? 'Tiêu đề' : 'Tên khái niệm'}</span><input value={name} onChange={(event) => setName(event.target.value)} maxLength={document ? 200 : 150} required autoFocus disabled={saving} /></label>
        {document ? <>
          <p className="inline-help">Backend hiện lưu thông tin tệp, chưa hỗ trợ tải tệp lên. Nhập đường dẫn đã được lưu từ nguồn của bạn.</p>
          <label className="field"><span className="field-label">Tên tệp</span><input value={fileName} onChange={(event) => setFileName(event.target.value)} maxLength={255} required disabled={saving} /></label>
          <label className="field"><span className="field-label">Loại tệp</span><input value={fileType} onChange={(event) => setFileType(event.target.value)} maxLength={50} placeholder="application/pdf" required disabled={saving} /></label>
          <label className="field"><span className="field-label">Đường dẫn lưu trữ</span><input value={storagePath} onChange={(event) => setStoragePath(event.target.value)} maxLength={500} required disabled={saving} /></label>
        </> : <>
          <label className="field"><span className="field-label">Mô tả</span><textarea value={description} onChange={(event) => setDescription(event.target.value)} maxLength={1000} rows={3} disabled={saving} /></label>
          <label className="field"><span className="field-label">Mức độ hiểu: {confidence}%</span><input type="range" min="0" max="100" value={confidence} onChange={(event) => setConfidence(event.target.value)} disabled={saving} /></label>
          <label className="field"><span className="field-label">Trạng thái</span><select value={status} onChange={(event) => setStatus(event.target.value)} disabled={saving}>{CONCEPT_STATUSES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        </>}
        <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose} disabled={saving}>Hủy</button><button className="primary-button" type="submit" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu'}</button></div>
      </form>
    </section>
  </div>
}

function PrerequisiteDialog({ concept, token, onClose, onSaved }) {
  const [items, setItems] = useState([])
  const [concepts, setConcepts] = useState([])
  const [selected, setSelected] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    Promise.all([listPrerequisites(token, concept.id), listConcepts(token, concept.courseId)])
      .then(([prerequisites, courseConcepts]) => { if (active) { setItems(prerequisites); setConcepts(courseConcepts) } })
      .catch((caught) => { if (active) setError(message(caught)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, concept.id, concept.courseId])

  const available = concepts.filter((candidate) => candidate.id !== concept.id && !items.some((item) => item.id === candidate.id))
  async function add(event) {
    event.preventDefault()
    if (!selected) return
    setBusy(true)
    setError('')
    try {
      await addPrerequisite(token, concept.id, Number(selected))
      setItems(await listPrerequisites(token, concept.id))
      setSelected('')
      onSaved()
    } catch (caught) { setError(message(caught)) }
    finally { setBusy(false) }
  }
  async function remove(item) {
    setBusy(true)
    setError('')
    try {
      await removePrerequisite(token, concept.id, item.id)
      setItems((current) => current.filter((entry) => entry.id !== item.id))
      onSaved()
    } catch (caught) { setError(message(caught)) }
    finally { setBusy(false) }
  }

  return <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
    <section className="editor-dialog" role="dialog" aria-modal="true" aria-labelledby="prerequisite-title">
      <div className="dialog-heading"><div><p className="section-kicker">Kiến thức cần trước</p><h2 id="prerequisite-title">{concept.name}</h2></div><button className="icon-button" type="button" onClick={onClose} aria-label="Đóng">×</button></div>
      {error && <p className="form-message error" role="alert">{error}</p>}
      {loading ? <p className="muted">Đang tải…</p> : <>
        {items.length === 0 ? <p className="muted">Chưa có kiến thức tiên quyết.</p> : <ul className="prerequisite-list">{items.map((item) => <li key={item.id}><span>{item.name}</span><button type="button" onClick={() => remove(item)} disabled={busy}>Gỡ</button></li>)}</ul>}
        {available.length > 0 && <form className="prerequisite-add" onSubmit={add}><label className="sr-only" htmlFor="prerequisite-select">Chọn khái niệm</label><select id="prerequisite-select" value={selected} onChange={(event) => setSelected(event.target.value)} required disabled={busy}><option value="">Chọn khái niệm trong khóa học</option>{available.map((item) => <option value={item.id} key={item.id}>{item.name}</option>)}</select><button className="primary-button" type="submit" disabled={busy || !selected}>Thêm</button></form>}
      </>}
    </section>
  </div>
}

export function CourseDetailPage() {
  const { courseId } = useParams()
  const { token } = useAuth()
  const [course, setCourse] = useState(null)
  const [courseError, setCourseError] = useState('')
  const [tab, setTab] = useState('documents')
  const [documents, setDocuments] = useState([])
  const [documentPage, setDocumentPage] = useState(0)
  const [documentPages, setDocumentPages] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [concepts, setConcepts] = useState([])
  const [threshold, setThreshold] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [dialog, setDialog] = useState(null)
  const [deleting, setDeleting] = useState(null)

  useEffect(() => {
    let active = true
    getCourse(token, courseId).then((data) => { if (active) setCourse(data) })
      .catch((caught) => { if (active) setCourseError(message(caught)) })
    return () => { active = false }
  }, [token, courseId])

  useEffect(() => {
    let active = true
    const request = tab === 'documents'
      ? listDocuments(token, { courseId, q: search, page: documentPage })
      : threshold === '' ? listConcepts(token, courseId) : listLowConfidenceConcepts(token, courseId, Number(threshold))
    request.then((data) => {
      if (!active) return
      if (tab === 'documents') {
        setDocuments(data.content ?? [])
        setDocumentPages(data.totalPages ?? 0)
      } else setConcepts(data)
      setError('')
    }).catch((caught) => { if (active) setError(message(caught)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [token, courseId, tab, search, documentPage, threshold, revision])

  function refresh() { setRevision((value) => value + 1); setDialog(null); setLoading(true) }
  function changeTab(value) { setTab(value); setError(''); setLoading(true) }
  async function remove(type, item) {
    if (!window.confirm(`Xóa “${type === 'document' ? item.title : item.name}”?`)) return
    setDeleting(`${type}-${item.id}`)
    setError('')
    try {
      if (type === 'document') {
        await deleteDocument(token, item.id)
        if (documents.length === 1 && documentPage > 0) setDocumentPage((page) => page - 1)
      } else await deleteConcept(token, item.id)
      refresh()
    } catch (caught) { setError(message(caught)) }
    finally { setDeleting(null) }
  }

  return <main className="dashboard-page">
    <header className="dashboard-header"><Link className="brand" to="/welcome"><span className="brand-mark" aria-hidden="true">S</span><span>StudyHub</span></Link><Link className="text-link" to="/welcome">← Bảng học tập</Link></header>
    <div className="detail-shell">
      <div className="page-heading"><div><p className="section-kicker">Chi tiết khóa học</p><h1>{course?.name ?? 'Khóa học'}</h1><p>{course?.description}</p></div></div>
      {courseError && <p className="form-message error" role="alert">{courseError}</p>}
      {!courseError && <div className="course-panel">
        <div className="detail-toolbar"><div className="detail-tabs" role="tablist" aria-label="Nội dung khóa học"><button type="button" role="tab" aria-selected={tab === 'documents'} className={tab === 'documents' ? 'active' : ''} onClick={() => changeTab('documents')}>Tài liệu</button><button type="button" role="tab" aria-selected={tab === 'concepts'} className={tab === 'concepts' ? 'active' : ''} onClick={() => changeTab('concepts')}>Khái niệm</button></div><button className="primary-button detail-add" type="button" onClick={() => setDialog({ type: tab === 'documents' ? 'document' : 'concept' })}>+ Thêm {tab === 'documents' ? 'tài liệu' : 'khái niệm'}</button></div>
        {tab === 'documents' ? <>
          <p className="inline-help">Tài liệu ở giai đoạn này chỉ là thông tin tệp và đường dẫn lưu trữ.</p>
          <form className="search-form detail-search" role="search" onSubmit={(event) => { event.preventDefault(); setSearch(searchInput.trim()); setDocumentPage(0); setLoading(true) }}><label className="sr-only" htmlFor="document-search">Tìm tài liệu</label><input id="document-search" type="search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Tìm theo tiêu đề…" /><button className="secondary-button" type="submit">Tìm</button></form>
        </> : <label className="status-filter detail-filter"><span className="field-label">Lọc mức độ hiểu</span><select value={threshold} onChange={(event) => { setThreshold(event.target.value); setLoading(true) }}><option value="">Tất cả khái niệm</option><option value="25">Dưới 25%</option><option value="50">Dưới 50%</option><option value="75">Dưới 75%</option></select></label>}
        {error && <p className="form-message error" role="alert">{error}</p>}
        {loading && <p className="list-state">Đang tải…</p>}
        {!loading && !error && tab === 'documents' && (documents.length ? <div className="resource-list">{documents.map((item) => <article className="resource-card" key={item.id}><div className="resource-copy"><h3>{item.title}</h3><p>{item.fileName} · {item.fileType}</p><p className="resource-path">{item.storagePath}</p></div><span className="status-badge">{DOCUMENT_LABELS[item.status] ?? item.status}</span><div className="course-actions"><button type="button" onClick={() => setDialog({ type: 'document', item })}>Sửa</button><button type="button" onClick={() => remove('document', item)} disabled={deleting === `document-${item.id}`}>Xóa</button></div></article>)}</div> : <div className="empty-state"><h3>Chưa có tài liệu</h3><p>Thêm thông tin tệp để quản lý tài liệu của khóa học.</p></div>)}
        {!loading && !error && tab === 'documents' && documentPages > 1 && <div className="pagination"><span>Trang {documentPage + 1} / {documentPages}</span><div><button className="secondary-button" type="button" disabled={documentPage === 0} onClick={() => { setDocumentPage((page) => page - 1); setLoading(true) }}>Trước</button><button className="secondary-button" type="button" disabled={documentPage + 1 >= documentPages} onClick={() => { setDocumentPage((page) => page + 1); setLoading(true) }}>Sau</button></div></div>}
        {!loading && !error && tab === 'concepts' && (concepts.length ? <div className="resource-list">{concepts.map((item) => <article className="resource-card" key={item.id}><div className="resource-copy"><h3>{item.name}</h3><p>{item.description || 'Chưa có mô tả'}</p><div className="confidence-track" aria-label={`Mức độ hiểu ${item.confidence}%`}><span style={{ width: `${item.confidence}%` }} /></div></div><div className="concept-meta"><strong>{item.confidence}%</strong><span className="status-badge">{CONCEPT_LABELS[item.status] ?? item.status}</span></div><div className="course-actions"><button type="button" onClick={() => setDialog({ type: 'prerequisite', item })}>Tiên quyết</button><button type="button" onClick={() => setDialog({ type: 'concept', item })}>Sửa</button><button type="button" onClick={() => remove('concept', item)} disabled={deleting === `concept-${item.id}`}>Xóa</button></div></article>)}</div> : <div className="empty-state"><h3>Chưa có khái niệm phù hợp</h3><p>Thêm khái niệm hoặc thay đổi mức lọc.</p></div>)}
      </div>}
    </div>
    {dialog?.type === 'prerequisite' && <PrerequisiteDialog concept={dialog.item} token={token} onClose={() => setDialog(null)} onSaved={() => setRevision((value) => value + 1)} />}
    {dialog && dialog.type !== 'prerequisite' && <ResourceDialog key={`${dialog.type}-${dialog.item?.id ?? 'new'}`} {...dialog} courseId={courseId} token={token} onClose={() => setDialog(null)} onSaved={refresh} />}
  </main>
}
