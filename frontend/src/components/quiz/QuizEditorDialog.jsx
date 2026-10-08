import { useState } from 'react'
import { createQuiz, updateQuiz } from '../../api/quizApi.js'
import { EditorDialog } from './EditorDialog'
import { QUIZ_STATUSES } from './quizForms.js'
import { quizError } from './quizErrors.js'

export function QuizEditorDialog({ item, courseId, token, onClose, onSaved }) {
  const [title, setTitle] = useState(item?.title ?? '')
  const [status, setStatus] = useState(item?.status ?? 'DRAFT')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function save(event) {
    event.preventDefault()
    if (saving) return
    if (!title.trim()) { setError('Vui lòng nhập tên bài kiểm tra.'); return }
    setSaving(true)
    setError('')
    try {
      const saved = item ? await updateQuiz(token, item.id, { title: title.trim(), status })
        : await createQuiz(token, courseId, { title: title.trim() })
      onSaved(saved)
    } catch (caught) { setError(quizError(caught)) }
    finally { setSaving(false) }
  }

  return <EditorDialog title={item ? 'Sửa bài kiểm tra' : 'Tạo bài kiểm tra'} busy={saving} onClose={onClose}>
    <form className="editor-form" onSubmit={save}>
      {error && <p className="form-message error" role="alert">{error}</p>}
      <label className="field"><span className="field-label">Tên bài kiểm tra</span>
        <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} required disabled={saving} />
      </label>
      {item ? <div className="field"><label className="field-label" htmlFor="quiz-status">Trạng thái</label>
        <select id="quiz-status" value={status} onChange={(event) => setStatus(event.target.value)} disabled={saving}>
          {QUIZ_STATUSES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </div> : <p className="inline-help">Bài mới được lưu dưới dạng bản nháp. Thêm câu hỏi rồi xuất bản để bắt đầu làm bài.</p>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose} disabled={saving}>Hủy</button>
        <button className="primary-button" type="submit" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu'}</button>
      </div>
    </form>
  </EditorDialog>
}
