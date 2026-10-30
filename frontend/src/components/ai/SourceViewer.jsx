import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { getSource } from '../../api/courseAiApi.js'
import { fetchDocumentPdf } from '../../api/documentFiles.js'
import { ApiError } from '../../api/authApi.js'
import { aiErrorMessage, formatSourcePages } from './aiDisplay.js'

const PdfPreview = lazy(() => import('./PdfPreview.jsx'))

function QuoteContent({ content, quote }) {
  const index = quote ? content.indexOf(quote) : -1
  if (index < 0) return <>{quote && <p className="inline-help">Không định vị được trích dẫn nguyên văn trong đoạn nguồn.</p>}<p className="ai-text">{content}</p></>
  return <p className="ai-text">{content.slice(0, index)}<mark>{content.slice(index, index + quote.length)}</mark>{content.slice(index + quote.length)}</p>
}

export function SourceViewer({ selection, token, onClose }) {
  const dialog = useRef(null)
  const lifecycle = useRef(null)
  const [source, setSource] = useState(null)
  const [error, setError] = useState('')
  const [pdf, setPdf] = useState({ loading: false, url: '', error: '' })
  function checkSource(data) {
    if (String(data.courseId) !== String(selection.courseId) || data.documentId !== selection.documentId || data.chunkId !== selection.chunkId || typeof data.content !== 'string') throw new ApiError('Invalid source response', 502)
    return data
  }
  useEffect(() => {
    const opener = document.activeElement
    const node = dialog.current
    node.showModal()
    const state = { active: true, url: '', busy: false, controller: new AbortController() }
    lifecycle.current = state
    getSource(token, selection.courseId, selection.documentId, selection.chunkId)
      .then(data => {
        if (String(data.courseId) !== String(selection.courseId) || data.documentId !== selection.documentId || data.chunkId !== selection.chunkId || typeof data.content !== 'string') throw new ApiError('Invalid source response', 502)
        if (state.active) setSource(data)
      })
      .catch(caught => { if (state.active) setError(aiErrorMessage(caught, 'source')) })
    return () => {
      state.active = false
      state.controller.abort()
      if (state.url) URL.revokeObjectURL(state.url)
      node.close()
      if (opener?.isConnected) opener.focus()
    }
  }, [token, selection.courseId, selection.documentId, selection.chunkId])

  async function openPdf() {
    const state = lifecycle.current
    if (!state?.active || state.busy || state.url) return
    state.busy = true
    setPdf({ loading: true, url: '', error: '' })
    try {
      // A user can leave the viewer open during re-ingestion. Recheck publication before fetching bytes.
      checkSource(await getSource(token, selection.courseId, selection.documentId, selection.chunkId))
      if (!state.active) return
      const blob = await fetchDocumentPdf(token, selection.documentId, { signal: state.controller.signal })
      if (!state.active) return
      state.url = URL.createObjectURL(blob)
      setPdf({ loading: false, url: state.url, error: '' })
    } catch (caught) {
      if (state.active && caught.name !== 'AbortError') setPdf({ loading: false, url: '', error: aiErrorMessage(caught, 'source') })
    } finally { state.busy = false }
  }

  function keepFocus(event) {
    if (event.key !== 'Tab') return
    const controls = [...dialog.current.querySelectorAll('button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]')].filter(node => node.getClientRects().length)
    const first = controls[0]
    const last = controls.at(-1)
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
  }

  return <dialog className="source-dialog" ref={dialog} aria-labelledby="source-viewer-title" onKeyDown={keepFocus} onCancel={event => { event.preventDefault(); onClose() }} onClick={event => { if (event.target === dialog.current) { const rect = dialog.current.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) onClose() } }}>
    <div className="dialog-heading"><div><p className="section-kicker">Đối chiếu tài liệu</p><h2 id="source-viewer-title">{source?.documentTitle ?? 'Nguồn tài liệu'}</h2></div><button className="icon-button" type="button" onClick={onClose} aria-label="Đóng nguồn">×</button></div>
    {!source && !error && <p role="status">Đang tải đoạn nguồn…</p>}
    {error && <p className="form-message error" role="alert">{error}</p>}
    {source && <>
      <p className="inline-help">{formatSourcePages(source.pageStart, source.pageEnd)}</p>
      <section className="source-content" aria-label="Đoạn nguồn"><QuoteContent content={source.content} quote={selection.quote} /></section>
      {source.fileUrl && !pdf.url && <button type="button" className="secondary-button" onClick={openPdf} disabled={pdf.loading}>{pdf.loading ? 'Đang tải PDF…' : 'Xem PDF'}</button>}
      {!source.fileUrl && <p className="inline-help">Nguồn này không có PDF được lưu để mở.</p>}
      {pdf.error && <p className="form-message error" role="alert">{pdf.error}</p>}
      {pdf.url && <><p><a href={pdf.url} target="_blank" rel="noopener noreferrer">Mở PDF trong tab mới</a></p><Suspense fallback={<p role="status">Đang mở PDF…</p>}><PdfPreview url={pdf.url} initialPage={source.pageStart} /></Suspense></>}
    </>}
  </dialog>
}
