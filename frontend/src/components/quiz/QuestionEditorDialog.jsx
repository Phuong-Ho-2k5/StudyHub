import { useState } from 'react'
import { createQuestion, updateQuestion } from '../../api/quizApi.js'
import { EditorDialog } from './EditorDialog'
import { buildQuestionPayload, QUESTION_TYPES } from './quizForms.js'
import { quizError } from './quizErrors.js'

function defaultOptions(type) {
  return type === 'TRUE_FALSE' ? [{ text: 'Đúng', correct: true }, { text: 'Sai', correct: false }]
    : Array.from({ length: 4 }, () => ({ text: '', correct: false }))
}

export function QuestionEditorDialog({ item, quizId, concepts, token, onClose, onSaved }) {
  const [draft, setDraft] = useState(() => ({
    text: item?.text ?? '', type: item?.type ?? 'MULTIPLE_CHOICE',
    conceptId: item?.conceptId ?? '', referenceAnswer: item?.referenceAnswer ?? '',
    options: item?.options.map(({ text, correct }) => ({ text, correct })) ?? defaultOptions('MULTIPLE_CHOICE'),
  }))
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  function field(name, value) { setDraft((current) => ({ ...current, [name]: value })) }
  function changeType(type) {
    setDraft((current) => ({ ...current, type, options: type === 'SHORT_ANSWER' ? [] : defaultOptions(type) }))
    setError('')
  }
  function editOption(index, data) {
    setDraft((current) => ({ ...current, options: current.options.map((item, position) => {
      if (position === index) return { ...item, ...data }
      if (current.type === 'TRUE_FALSE' && data.correct) return { ...item, correct: false }
      return item
    }) }))
  }

  async function save(event) {
    event.preventDefault()
    if (saving) return
    let payload
    try { payload = buildQuestionPayload(draft) }
    catch (caught) { setError(quizError(caught)); return }
    setSaving(true)
    setError('')
    try {
      const saved = item ? await updateQuestion(token, item.id, payload) : await createQuestion(token, quizId, payload)
      onSaved(saved)
    } catch (caught) { setError(quizError(caught)) }
    finally { setSaving(false) }
  }

  return <EditorDialog title={item ? 'Sửa câu hỏi' : 'Thêm câu hỏi'} busy={saving} onClose={onClose} wide>
    <form className="editor-form" onSubmit={save}>
      {error && <p className="form-message error" role="alert">{error}</p>}
      <div className="field"><label className="field-label" htmlFor="question-text">Nội dung câu hỏi</label>
        <textarea id="question-text" value={draft.text} onChange={(event) => field('text', event.target.value)} maxLength={500} required disabled={saving} rows={3} />
      </div>
      <div className="question-fields">
        <div className="field"><label className="field-label" htmlFor="question-type">Loại câu hỏi</label>
          <select id="question-type" value={draft.type} onChange={(event) => changeType(event.target.value)} disabled={saving}>
            {QUESTION_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </div>
        <div className="field"><label className="field-label" htmlFor="question-concept">Khái niệm liên quan</label>
          <select id="question-concept" value={draft.conceptId} onChange={(event) => field('conceptId', event.target.value)} disabled={saving}>
            <option value="">Không gắn khái niệm</option>
            {concepts.map((concept) => <option key={concept.id} value={concept.id}>{concept.name}</option>)}
          </select>
        </div>
      </div>
      {draft.type === 'SHORT_ANSWER' ? <>
        <div className="field"><label className="field-label" htmlFor="question-reference">Đáp án tham chiếu</label>
          <textarea id="question-reference" value={draft.referenceAnswer} onChange={(event) => field('referenceAnswer', event.target.value)} maxLength={4000} required disabled={saving} rows={4} />
        </div>
        <p className="inline-help">Câu tự luận ngắn hiện được ghi nhận là chờ chấm khi nộp bài.</p>
      </> : <fieldset className="option-editor" disabled={saving}>
        <legend>Các lựa chọn và đáp án đúng</legend>
        <p className="inline-help">{draft.type === 'TRUE_FALSE' ? 'Chọn một đáp án đúng.' : 'Nhập ít nhất 4 lựa chọn. Có thể chọn nhiều đáp án đúng.'}</p>
        {draft.options.map((option, index) => <div className="option-editor-row" key={index}>
          <label className="option-correct"><input type={draft.type === 'TRUE_FALSE' ? 'radio' : 'checkbox'}
            name="correct-option" checked={option.correct} onChange={(event) => editOption(index, { correct: event.target.checked })}
            aria-label={`Lựa chọn ${index + 1} là đáp án đúng`} /><span>{index + 1}.</span></label>
          <input className="option-text" value={option.text} onChange={(event) => editOption(index, { text: event.target.value })}
            aria-label={`Nội dung lựa chọn ${index + 1}`} maxLength={500} required />
          {draft.type === 'MULTIPLE_CHOICE' && <button className="option-remove" type="button" disabled={draft.options.length <= 4}
            onClick={() => field('options', draft.options.filter((_, position) => position !== index))} aria-label={`Xóa lựa chọn ${index + 1}`}>×</button>}
        </div>)}
        {draft.type === 'MULTIPLE_CHOICE' && <button className="secondary-button" type="button"
          onClick={() => field('options', [...draft.options, { text: '', correct: false }])}>+ Thêm lựa chọn</button>}
      </fieldset>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose} disabled={saving}>Hủy</button>
        <button className="primary-button" type="submit" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu câu hỏi'}</button>
      </div>
    </form>
  </EditorDialog>
}
