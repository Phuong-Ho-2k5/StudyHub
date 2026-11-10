import { useState } from 'react'
import { CitationList } from './CitationList.jsx'
import { SourceViewer } from './SourceViewer.jsx'
import { confirmConceptSuggestion } from '../../api/aiJobsApi.js'
export function ConceptSuggestionBatch({ batch, token }) {
  const [selection, setSelection] = useState(null)
  const [confirmed, setConfirmed] = useState([])
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState('')
  async function confirm(item) {
    setBusy(item.id); setError('')
    try { await confirmConceptSuggestion(token, batch.courseId, batch.documentId, batch.id, item); setConfirmed(value => [...value, item.id]) }
    catch (caught) { setError(caught.message) }
    finally { setBusy(null) }
  }
  return <section aria-label="Gợi ý khái niệm"><h3>Gợi ý khái niệm</h3>
    <p className="inline-help">Đối chiếu nội dung và nguồn trước khi thêm vào khóa học.</p>
    {!batch.sourceCurrent && <p role="alert">Nguồn đã thay đổi. Hãy tạo gợi ý mới trước khi xác nhận.</p>}
    {error && <p role="alert" className="form-message error">{error}</p>}
    {batch.suggestions.length === 0 && <p>Không có gợi ý trong phần tài liệu đã chọn.</p>}
    {batch.suggestions.map(item => <article className="resource-card" key={item.id}><div className="resource-copy"><h4>{item.name}</h4><p className="ai-text">{item.description}</p>
      <CitationList citations={item.citations} courseId={batch.courseId} onOpenSource={setSelection} />
      {item.state === 'CONFIRMED' || confirmed.includes(item.id) ? <p>Đã thêm khái niệm</p> : <button className="secondary-button" disabled={!batch.sourceCurrent || busy !== null} onClick={() => confirm(item)}>Xác nhận thêm khái niệm</button>}
    </div></article>)}
    {selection && <SourceViewer selection={selection} token={token} onClose={() => setSelection(null)} />}
  </section>
}
