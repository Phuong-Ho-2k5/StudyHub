import { useState } from 'react'
import { Link } from 'react-router-dom'
import { searchCourse } from '../../api/courseAiApi.js'
import { ApiError } from '../../api/authApi.js'
import { useAiRequest } from '../../hooks/useAiRequest.js'
import { formatSourcePages, validateAiInput } from './aiDisplay.js'

export function SemanticSearchPanel({ courseId, token, onOpenSource }) {
  const [query, setQuery] = useState('')
  const [validation, setValidation] = useState('')
  const state = useAiRequest('search')
  async function submit(event) {
    event.preventDefault()
    const error = validateAiInput(query)
    setValidation(error)
    if (error) return
    const submitted = query.trim()
    await state.run(async () => {
      const response = await searchCourse(token, courseId, { query: submitted })
      if (String(response.courseId) !== String(courseId) || !Array.isArray(response.items)) throw new ApiError('Invalid search response', 502)
      return { ...response, query: submitted }
    })
  }
  return <section aria-label="Tìm kiếm ngữ nghĩa" className="ai-section">
    <h3>Tìm đoạn tài liệu theo ý nghĩa</h3>
    <p className="inline-help">Tìm trong các bản tài liệu đã xử lý của khóa học này.</p>
    <form className="ai-form" onSubmit={submit}>
      <label className="field"><span className="field-label">Tìm trong tài liệu</span><textarea rows={3} value={query} onChange={event => setQuery(event.target.value)} maxLength={16384} /></label>
      <button className="primary-button" disabled={state.pending} type="submit">{state.pending ? 'Đang tìm…' : 'Tìm kiếm'}</button>
    </form>
    {(validation || state.error) && <p className="form-message error" role="alert">{validation || state.error}</p>}
    {state.pending && <p role="status">Đang tìm đoạn phù hợp…</p>}
    {state.data && <div className="ai-results" aria-live="polite">
      <p className="inline-help">Kết quả cho: {state.data.query}</p>
      {state.data.items.length === 0 ? <div className="empty-state"><h3>Không tìm thấy đoạn phù hợp.</h3><p>Tài liệu có thể chưa được xử lý hoặc chưa có nội dung phù hợp.</p><Link to={`/courses/${courseId}?tab=documents`}>Kiểm tra tài liệu</Link></div> : state.data.items.map(item => <article className="ai-result" key={item.chunkId}>
        <div className="section-heading"><h4>{item.documentTitle}</h4><span>{formatSourcePages(item.pageStart, item.pageEnd)}</span></div>
        <p className="ai-text">{item.content}</p>
        <button className="secondary-button" type="button" onClick={() => onOpenSource({ courseId, documentId: item.documentId, chunkId: item.chunkId, quote: null })}>Xem nguồn</button>
      </article>)}
    </div>}
  </section>
}
