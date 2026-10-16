import { useState } from 'react'
import { EditorDialog } from '../quiz/EditorDialog'

export function DiscussionEditor({ item, busy, error, onSave, onClose }) {
  const [title, setTitle] = useState(item?.title ?? '')
  const [content, setContent] = useState(item?.content ?? '')
  const [validation, setValidation] = useState('')
  function submit(event) {
    event.preventDefault()
    if (!title.trim() || !content.trim()) { setValidation('Vui lòng nhập tiêu đề và nội dung.'); return }
    onSave({ title: title.trim(), content: content.trim() })
  }
  return <EditorDialog title={item ? 'Sửa bài thảo luận' : 'Tạo bài thảo luận'} busy={busy} onClose={onClose}>
    <form className="editor-form" onSubmit={submit}>
      {(error || validation) && <p className="form-message error" role="alert">{error || validation}</p>}
      <label className="field"><span className="field-label">Tiêu đề</span><input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} required disabled={busy} /></label>
      <label className="field"><span className="field-label">Nội dung</span><textarea value={content} onChange={(event) => setContent(event.target.value)} maxLength={10000} rows={8} required disabled={busy} /></label>
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose} disabled={busy}>Hủy</button><button className="primary-button" type="submit" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu bài viết'}</button></div>
    </form>
  </EditorDialog>
}
