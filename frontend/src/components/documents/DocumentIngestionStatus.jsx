import { useAIJobPolling } from '../../hooks/useAIJobPolling.js'
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { getDocument, ingestDocument } from '../../api/courseAiApi.js'
import { aiErrorMessage, ingestionDisplay } from '../ai/aiDisplay.js'

export function DocumentIngestionStatus({ document, token, onChanged }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [reloadError, setReloadError] = useState(false)
  const lock = useRef(false)
  const active = useRef(false)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const changed = useEffectEvent(onChanged)
  const state = ingestionDisplay(document)
  async function reloadDocument() {
    try {
      const item = await getDocument(token, document.id)
      if (active.current) { onChanged(item); setReloadError(false); setError('') }
    } catch { if (active.current) setReloadError(true) }
  }
  const polling = useAIJobPolling({ token, courseId: document.courseId, jobId: state.pending ? document.ingestionJobId : null, onCompleted: reloadDocument })
  useEffect(() => {
    if (polling.job?.status !== "FAILED") return undefined
    let live = true
    getDocument(token,document.id).then(item => { if (live) { changed(item); setReloadError(false) } }).catch(() => { if (live) setReloadError(true) })
    return () => { live = false }
  }, [polling.job, token, document.id])
  async function run() {
    if (lock.current || state.pending) return
    lock.current = true
    setBusy(true); setError('')
    try { const accepted = await ingestDocument(token, document.id); if (active.current) onChanged({...document,ingestionStatus:"PENDING",ingestionJobId:accepted.jobId}) }
    catch (caught) { if (active.current) setError(aiErrorMessage(caught, 'ingest')) }
    finally {
      if (active.current) {
        try { const item = await getDocument(token, document.id); if (active.current) onChanged(item) }
        catch { if (active.current) setError(current => current || 'Không thể cập nhật trạng thái. Hãy làm mới danh sách tài liệu.') }
        if (active.current) setBusy(false)
      }
      lock.current = false
    }
  }
  return <div className="document-ingestion">
    <span className={`status-badge ingestion-${document.ingestionStatus?.toLowerCase()}`}>{({PENDING:"Đang chờ xử lý",PROCESSING:"AI đang xử lý",COMPLETED:"Đã hoàn tất",FAILED:"Xử lý thất bại"})[polling.job?.status] ?? state.label}</span>
    <span className="inline-help">{state.published ? 'Đã có bản dùng cho AI' : 'Chưa có bản dùng cho AI'}</span>
    {state.pending && state.published && <p className="inline-help">Đang tạo bản mới; AI vẫn dùng bản đã công bố.</p>}
    {document.ingestionStatus === 'FAILED' && <p className="inline-help">Lần xử lý mới nhất chưa thành công.{state.published ? ' AI vẫn dùng bản đã công bố.' : ' Hãy thử lại hoặc kiểm tra tệp PDF.'}</p>}
    {(state.action || busy) && <button className="secondary-button" type="button" onClick={run} disabled={busy}>{busy ? 'Đang xử lý…' : state.action}</button>}
    {reloadError && <div role="alert">Không thể tải lại tài liệu. <button className="secondary-button" onClick={reloadDocument}>Tải lại tài liệu</button></div>}
    {polling.pollError && <div role="alert">Không thể đọc trạng thái tác vụ. <button className="secondary-button" onClick={polling.refresh}>Cập nhật trạng thái</button></div>}
    {error && <p className="form-message error" role="alert">{error}</p>}
  </div>
}
