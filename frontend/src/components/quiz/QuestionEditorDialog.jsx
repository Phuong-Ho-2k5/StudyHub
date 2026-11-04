import { useRef, useState } from 'react'
import { createQuestion, updateQuestion } from '../../api/quizApi.js'
import { EditorDialog } from './EditorDialog'
import { buildQuestionPayload, QUESTION_TYPES } from './quizForms.js'
import { quizError } from './quizErrors.js'

function defaultOptions(type) {
  return type === 'TRUE_FALSE' ? [{ text: 'Đúng', correct: true }, { text: 'Sai', correct: false }]
    : Array.from({ length: 4 }, () => ({ text: '', correct: false }))
}

export function QuestionEditorDialog({ item, quizId, concepts, token, onClose, onSaved, aiGenerated = false, expectedRevision, onConflict, reloadRequired = false, onReload }) {
  const pending = useRef(false)
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
      if ((aiGenerated || current.type === 'TRUE_FALSE') && data.correct) return { ...item, correct: false }
      return item
    }) }))
  }

  async function save(event) {
    event.preventDefault()
    if (pending.current || reloadRequired) return
    let payload
    try { payload = buildQuestionPayload(draft, { aiGenerated }) }
    catch (caught) { setError(quizError(caught)); return }
    if (aiGenerated) payload.expectedRevision = expectedRevision
    pending.current = true
    setSaving(true)
    setError('')
    try {
      const saved = item ? await updateQuestion(token, item.id, payload) : await createQuestion(token, quizId, payload)
      onSaved(saved)
    } catch (caught) { setError(quizError(caught)); onConflict?.(caught) }
    finally { pending.current = false; setSaving(false) }
  }

  return <EditorDialog title={item ? 'Sửa câu hỏi' : 'Thêm câu hỏi'} busy={saving} onClose={onClose} wide>
    <form className="editor-form" onSubmit={save}>
      {error && <p className="form-message error" role="alert">{error}</p>}
      {reloadRequired && <div className="form-message error" role="alert"><p>Nội dung có thể đã thay đổi. Bạn có thể sao chép phần đang nhập trước khi tải lại; chưa thể lưu bản này.</p>
        <button className="secondary-button" type="button" onClick={onReload}>Tải lại nội dung</button></div>}
      <div className="field"><label className="field-label" htmlFor="question-text">Nội dung câu hỏi</label>
        <textarea id="question-text" value={draft.text} onChange={(event) => field('text', event.target.value)} maxLength={500} required disabled={saving} rows={3}
          placeholder={draft.type === 'SHORT_ANSWER'
            ? 'Ví dụ: HTTP mặc định dùng cổng nào? Chỉ nhập số nguyên, không ghi đơn vị, không thêm khoảng trắng.'
            : undefined}
          aria-describedby={draft.type === 'SHORT_ANSWER' ? 'question-text-help' : undefined} />
        {draft.type === 'SHORT_ANSWER' && <p className="inline-help" id="question-text-help">
          Nên viết hướng dẫn trả lời ngay trong ô Nội dung câu hỏi để người làm bài biết chính xác định dạng cần nhập:
          chữ hoa/thường, dấu, đơn vị, định dạng số và khoảng trắng.
        </p>}
      </div>
      <div className="question-fields">
        <div className="field"><label className="field-label" htmlFor="question-type">Loại câu hỏi</label>
          <select id="question-type" value={draft.type} onChange={(event) => changeType(event.target.value)} disabled={saving || aiGenerated}>
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
        <div className="field"><label className="field-label" htmlFor="question-reference">Đáp án mẫu</label>
          <textarea id="question-reference" value={draft.referenceAnswer} onChange={(event) => field('referenceAnswer', event.target.value)} maxLength={4000} required disabled={saving} rows={4} aria-describedby="question-reference-help" />
        </div>
        <p className="inline-help" id="question-reference-help">
          Câu trả lời được chấm ngay bằng cách so khớp từng ký tự với đáp án mẫu,
          bao gồm chữ hoa/thường, dấu, khoảng trắng và xuống dòng. Đáp án mẫu được giữ nguyên khi lưu.
        </p>
      </> : <fieldset className="option-editor" disabled={saving}>
        <legend>Các lựa chọn và đáp án đúng</legend>
        <p className="inline-help">{aiGenerated ? 'Quiz AI cần đúng 4 lựa chọn khác nhau và đúng 1 đáp án đúng.' : draft.type === 'TRUE_FALSE' ? 'Chọn một đáp án đúng.' : 'Nhập ít nhất 4 lựa chọn. Có thể chọn nhiều đáp án đúng.'}</p>
        {draft.options.map((option, index) => <div className="option-editor-row" key={index}>
          <label className="option-correct"><input type={aiGenerated || draft.type === 'TRUE_FALSE' ? 'radio' : 'checkbox'}
            name="correct-option" checked={option.correct} onChange={(event) => editOption(index, { correct: event.target.checked })}
            aria-label={`Lựa chọn ${index + 1} là đáp án đúng`} /><span>{index + 1}.</span></label>
          <input className="option-text" value={option.text} onChange={(event) => editOption(index, { text: event.target.value })}
            aria-label={`Nội dung lựa chọn ${index + 1}`} maxLength={500} required />
          {!aiGenerated && draft.type === 'MULTIPLE_CHOICE' && <button className="option-remove" type="button" disabled={draft.options.length <= 4}
            onClick={() => field('options', draft.options.filter((_, position) => position !== index))} aria-label={`Xóa lựa chọn ${index + 1}`}>×</button>}
        </div>)}
        {!aiGenerated && draft.type === 'MULTIPLE_CHOICE' && <button className="secondary-button" type="button"
          onClick={() => field('options', [...draft.options, { text: '', correct: false }])}>+ Thêm lựa chọn</button>}
      </fieldset>}
      <div className="dialog-actions"><button className="secondary-button" type="button" onClick={onClose} disabled={saving}>Hủy</button>
        <button className="primary-button" type="submit" disabled={saving || reloadRequired}>{saving ? 'Đang lưu…' : 'Lưu câu hỏi'}</button>
      </div>
    </form>
  </EditorDialog>
}
