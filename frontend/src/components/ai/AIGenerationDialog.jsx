import { useEffect, useRef, useState } from 'react'
import { EditorDialog } from '../quiz/EditorDialog.jsx'
import { listDocuments } from '../../api/studyApi.js'
import { submitConceptJob, submitQuizJob, validJobId } from '../../api/aiJobsApi.js'
export function AIGenerationDialog({ token, courseId, document: source, mode, onClose, onAccepted }) {
  const [title, setTitle] = useState(source ? `Ôn tập: ${source.title}`.slice(0,200) : 'Ôn tập khóa học')
  const [count, setCount] = useState(5)
  const [start, setStart] = useState(0)
  const [limit, setLimit] = useState(10)
  const [documents, setDocuments] = useState([])
  const [selected, setSelected] = useState([])
  const [page, setPage] = useState(0)
  const [pages, setPages] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const key = useRef(crypto.randomUUID())
  const locked = useRef(false)
  const lifecycle = useRef(null)
  useEffect(() => { const scope = { active: true }; lifecycle.current = scope; return () => { scope.active = false } }, [token, courseId])
  useEffect(() => {
    if (source) return undefined
    let active = true
    listDocuments(token, { courseId, page }).then(data => { if (active) { setDocuments(data.content ?? []); setPages(data.totalPages ?? 0) } }).catch(error => { if (active) setError(error.message) })
    return () => { active = false }
  }, [token, courseId, source, page])
  async function submit(event) {
    event.preventDefault(); if (locked.current) return
    const scope = lifecycle.current
    locked.current = true; setBusy(true); setError('')
    try {
      const data = mode === 'concept' ? {startChunkIndex:Number(start),chunkLimit:Number(limit),maxSuggestions:Number(count)}
        : {title:title.trim(),questionCount:Number(count),chunkLimit:Number(limit),...(source ? {startChunkIndex:Number(start)} : {documentIds:selected})}
      const accepted = mode === 'concept' ? await submitConceptJob(token,courseId,source.id,data) : await submitQuizJob(token,{courseId,documentId:source?.id,key:key.current,request:data})
      if (!validJobId(accepted.jobId)) throw new Error('Máy chủ không trả mã tác vụ hợp lệ.')
      if (scope?.active) onAccepted(accepted.jobId)
    } catch (caught) { if (scope?.active) setError(caught.message) }
    finally { locked.current = false; if (scope?.active) setBusy(false) }
  }
  return <EditorDialog title={mode === 'concept' ? 'Gợi ý khái niệm' : 'Sinh Quiz bằng AI'} busy={busy} onClose={onClose}>
    <form className="editor-form" onSubmit={submit}>
      {error && <p className="form-message error" role="alert">{error}</p>}
      {mode !== 'concept' && <label className="field">Tiêu đề Quiz<input value={title} onChange={e=>setTitle(e.target.value)} required maxLength={200} disabled={busy}/></label>}
      {!source && <fieldset disabled={busy}><legend>Chọn 1–5 tài liệu đã xử lý ({selected.length}/5)</legend>
        {documents.map(item => <label className="field" key={item.id}><span><input type="checkbox" checked={selected.includes(item.id)} disabled={!item.publishedIngestionId || (!selected.includes(item.id) && selected.length>=5)} onChange={e=>setSelected(v=>e.target.checked?[...v,item.id]:v.filter(id=>id!==item.id))}/>{item.title}{!item.publishedIngestionId && ' · Chưa có bản dùng cho AI'}</span></label>)}
        <button type="button" disabled={page===0} onClick={()=>setPage(v=>v-1)}>Trang trước</button><button type="button" disabled={page+1>=pages} onClick={()=>setPage(v=>v+1)}>Trang sau</button>
      </fieldset>}
      <label className="field">{mode === 'concept' ? 'Số gợi ý tối đa' : 'Số câu hỏi'}<input type="number" min="1" max="10" required value={count} onChange={e=>setCount(e.target.value)} disabled={busy}/></label>
      {source && <label className="field">Chỉ số đoạn bắt đầu<input type="number" min="0" max="2147483647" required value={start} onChange={e=>setStart(e.target.value)} disabled={busy}/></label>}
      <label className="field">Số đoạn tối đa<input type="number" min={Math.max(1,selected.length)} max="20" required value={limit} onChange={e=>setLimit(e.target.value)} disabled={busy}/></label>
      <p className="inline-help">AI chỉ đọc phần tài liệu được chọn. Kết quả là bản nháp cần bạn kiểm tra.</p>
      <div className="dialog-actions"><button className="secondary-button" type="button" disabled={busy} onClick={onClose}>Hủy</button><button className="primary-button" disabled={busy || (!source && selected.length===0)}>{busy?'Đang gửi…':'Bắt đầu'}</button></div>
    </form>
  </EditorDialog>
}
