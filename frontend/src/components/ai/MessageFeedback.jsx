import { useEffect, useRef, useState } from 'react'
import { saveMessageFeedback } from '../../api/courseAiApi.js'
import { validateFeedback } from './feedbackDisplay.js'

export function MessageFeedback({ messageId, token, initialFeedback }) {
  const [saved, setSaved] = useState(initialFeedback ?? null)
  const [rating, setRating] = useState(initialFeedback?.rating ?? '')
  const [note, setNote] = useState(initialFeedback?.note ?? '')
  const [expanded, setExpanded] = useState(!!initialFeedback?.note)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const lock = useRef(false)
  const life = useRef(0)
  useEffect(() => {
    const generation = life
    return () => { generation.current++ }
  }, [])

  async function submit(event) {
    event.preventDefault()
    if (lock.current || !rating) return
    if (note.trim().length > 2000) { setError('Ghi chú tối đa 2.000 ký tự.'); return }
    lock.current = true
    const generation = life.current
    setPending(true)
    setError('')
    try {
      const result = await saveMessageFeedback(token, messageId, { rating, note: note.trim() || null })
      if (!validateFeedback(result, messageId)) throw new Error('Invalid feedback')
      if (generation !== life.current) return
      setSaved(result)
      setRating(result.rating)
      setNote(result.note ?? '')
    } catch {
      if (generation === life.current) setError('Chưa xác nhận lưu phản hồi. Bạn có thể thử lại hoặc tải lại lịch sử để kiểm tra.')
    } finally {
      if (generation === life.current) { lock.current = false; setPending(false) }
    }
  }
  const unchanged = saved && rating === saved.rating && (note.trim() || null) === saved.note
  return <form className="message-feedback" aria-label="Đánh giá câu trả lời" onSubmit={submit}>
    <fieldset disabled={pending}>
      <legend>Câu trả lời này có hữu ích không?</legend>
      <div className="feedback-actions">
        <button type="button" className="secondary-button" aria-pressed={rating === 'HELPFUL'} onClick={() => setRating('HELPFUL')}>Hữu ích</button>
        <button type="button" className="secondary-button" aria-pressed={rating === 'NOT_HELPFUL'} onClick={() => setRating('NOT_HELPFUL')}>Chưa hữu ích</button>
        {!expanded && <button type="button" className="text-button" onClick={() => setExpanded(true)}>Thêm ghi chú</button>}
      </div>
      {expanded && <label className="field"><span className="field-label">Ghi chú</span><textarea rows={2} value={note} maxLength={2000} onChange={event => setNote(event.target.value)} /></label>}
      <button type="submit" className="secondary-button" disabled={!rating || pending}>{pending ? 'Đang lưu…' : saved ? 'Cập nhật phản hồi' : 'Gửi phản hồi'}</button>
    </fieldset>
    {saved && unchanged && !pending && !error && <p role="status" className="inline-help">Đã lưu phản hồi</p>}
    {saved && !unchanged && <p className="inline-help">Có thay đổi chưa lưu.</p>}
    {error && <p role="alert" className="form-message error">{error}</p>}
  </form>
}
