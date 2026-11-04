import { useRef, useState } from 'react'
import { createQuiz, updateQuiz } from '../../api/quizApi.js'
import { EditorDialog } from './EditorDialog'
import { QUIZ_STATUSES } from './quizForms.js'
import { quizError } from './quizErrors.js'

export function QuizEditorDialog({ item, courseId, token, onClose, onSaved, aiGenerated = false, expectedRevision, onConflict, reloadRequired = false, onReload }) {
  const pending = useRef(false)
  const [title, setTitle] = useState(item?.title ?? '')
  const [status, setStatus] = useState(item?.status ?? 'DRAFT')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function save(event) {
    event.preventDefault()
    if (pending.current || reloadRequired) return
    if (!title.trim()) { setError('Vui lòng nhập tên bài kiểm tra.'); return }
    pending.current = true
    setSaving(true)
    setError('')
    try {
      const saved = item ? await updateQuiz(token, item.id, { title: title.trim(), status, ...(aiGenerated ? { expectedRevision } : {}) })
        : await createQuiz(token, courseId, { title: title.trim() })
      onSaved(saved)
    } catch (caught) { setError(quizError(caught)); onConflict?.(caught) }
    finally { pending.current = false; setSaving(false) }
  }

  return <EditorDialog title={item ? 'Sửa bài kiểm tra' : 'Tạo bài kiểm tra'} busy={saving} onClose={onClose}>
    <form className="editor-form" onSubmit={save}>
      {error && <p className="form-message error" role="alert">{error}</p>}
      {reloadRequired && <div className="form-message error" role="alert"><p>Nội dung có thể đã thay đổi. Sao chép tên đang nhập nếu cần trước khi tải lại.</p>
        <button className="secondary-button" type="button" onClick={onReload}>Tải lại nội dung</button></div>}
      <label className="field"><span className="field-label">Tên bài kiểm tra</span>
        <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} required disabled={saving} />
      </label>
      {item && !aiGenerated ? <div className="field"><label className="field-label" htmlFor="quiz-status">Trạng thái</label>
        <select id="quiz-status" value={status} onChange={(event) => setStatus(event.target.value)} disabled={saving}>
          {QUIZ_STATUSES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </div> : <p className="inline-help">{aiGenerated ? 'Sửa tên sẽ đưa Quiz về bản nháp và cần xác nhận lại trước khi xuất bản.' : 'Bài mới được lưu dưới dạng bản nháp. Thêm câu hỏi rồi xuất bản để bắt đầu làm bài.'}</p>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose} disabled={saving}>Hủy</button>
        <button className="primary-button" type="submit" disabled={saving || reloadRequired}>{saving ? 'Đang lưu…' : 'Lưu'}</button>
      </div>
    </form>
  </EditorDialog>
}
