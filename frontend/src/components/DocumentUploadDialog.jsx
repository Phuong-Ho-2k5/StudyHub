import { useState } from 'react'
import { uploadDocument, validatePdf } from '../api/documentFiles'
import { errorMessage } from '../api/learningDisplay'
import { EditorDialog } from './quiz/EditorDialog'

export function DocumentUploadDialog({ token, courseId, onClose, onSaved }) {
  const [title, setTitle] = useState('')
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    const invalid = validatePdf(file)
    if (invalid) { setError(invalid); return }
    if (!title.trim()) { setError('Vui lòng nhập tiêu đề.'); return }
    setBusy(true); setProgress(0); setError('')
    try {
      await uploadDocument(token, courseId, title.trim(), file, setProgress)
      onSaved()
    } catch (caught) { setError(errorMessage(caught)); setBusy(false) }
  }

  return <EditorDialog title="Tải tài liệu PDF" busy={busy} onClose={onClose}>
    <form className="editor-form" onSubmit={submit}>
      {error && <p className="form-message error" role="alert">{error}</p>}
      <label className="field"><span className="field-label">Tiêu đề</span><input value={title} maxLength={200} required disabled={busy} onChange={(event) => setTitle(event.target.value)} /></label>
      <label className="field"><span className="field-label">Tệp PDF</span><input type="file" accept=".pdf,application/pdf" required disabled={busy} onChange={(event) => {
        const selected = event.target.files?.[0] ?? null
        setFile(selected); setError('')
        if (selected && !title) setTitle(selected.name.replace(/\.pdf$/i, '').slice(0, 200))
      }} /></label>
      <p className="inline-help">Chọn PDF không có mật khẩu. Tệp được lưu trong khóa học này.</p>
      {file && <p className="muted">{file.name} · {(file.size / 1024 / 1024).toFixed(2)} MB</p>}
      {busy && <div className="upload-progress" role="status" aria-live="polite"><progress max="100" value={progress} aria-label="Tiến độ tải lên" /><span>{progress < 100 ? `Đang tải lên ${progress}%` : 'Đang kiểm tra và lưu PDF…'}</span></div>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose} disabled={busy}>Hủy</button><button className="primary-button" type="submit" disabled={busy || !file}>{busy ? 'Đang tải…' : 'Tải lên'}</button></div>
    </form>
  </EditorDialog>
}
