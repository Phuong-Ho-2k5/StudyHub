import { useState } from 'react'

export function ChatSessionList({ sessions, activeSessionId, page, totalPages, pending, error, createError, busy, uncertainCreate, onPageChange, onRetry, onSelect, onCreate }) {
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')
  const [validation, setValidation] = useState('')

  async function submit(event) {
    event.preventDefault()
    if (busy || uncertainCreate) return
    const trimmed = title.trim()
    if (!trimmed || trimmed.length > 150) { setValidation('Tiêu đề cần có 1–150 ký tự sau khi bỏ khoảng trắng đầu/cuối.'); return }
    setValidation('')
    if (await onCreate(trimmed)) { setCreating(false); setTitle('') }
  }

  return <aside className="chat-sessions" aria-label="Danh sách hội thoại">
    <h4>Hội thoại của khóa học</h4>
    <button type="button" className="primary-button" disabled={busy} onClick={() => { setCreating(true); setValidation('') }}>Hội thoại mới</button>
    {creating && <form className="ai-form" onSubmit={submit}>
      <label className="field"><span className="field-label">Tiêu đề hội thoại</span><input value={title} disabled={busy} onChange={event => setTitle(event.target.value)} /></label>
      <button type="submit" className="primary-button" disabled={busy || uncertainCreate}>Tạo hội thoại</button>
      <button type="button" className="secondary-button" disabled={busy} onClick={() => setCreating(false)}>Hủy</button>
    </form>}
    {(validation || createError) && <p role="alert" className="form-message error">{validation || createError}</p>}
    {pending && <p role="status">Đang tải danh sách hội thoại…</p>}
    {error && <p role="alert" className="form-message error">{error}</p>}
    {!pending && !error && sessions.length === 0 && <p>Chưa có hội thoại nào.</p>}
    <ul className="chat-session-items">{sessions.map(item => <li key={item.id}>
      <button type="button" className="chat-session-button" disabled={busy} aria-pressed={activeSessionId === item.id} onClick={() => onSelect(item)}>{item.title}</button>
      <time className="inline-help" dateTime={item.updatedAt}>{new Date(item.updatedAt).toLocaleString('vi-VN')}</time>
    </li>)}</ul>
    <div className="chat-pagination">
      <button type="button" className="secondary-button" disabled={busy || pending || page === 0} onClick={() => onPageChange(page - 1)}>Trang trước</button>
      <span>Trang {totalPages === 0 ? 0 : page + 1}/{totalPages}</span>
      <button type="button" className="secondary-button" disabled={busy || pending || page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>Trang sau</button>
    </div>
    <button type="button" className="secondary-button" disabled={busy || pending} onClick={onRetry}>Tải lại danh sách</button>
  </aside>
}
