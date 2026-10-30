import { useEffect, useRef, useState } from 'react'
import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

GlobalWorkerOptions.workerSrc = workerUrl

function PdfPage({ pdf, page }) {
  const canvas = useRef(null)
  const [state, setState] = useState('loading')
  useEffect(() => {
    let active = true
    let rendering
    pdf.getPage(page).then(item => {
      if (!active) return
      const viewport = item.getViewport({ scale: 1.25 })
      canvas.current.width = viewport.width
      canvas.current.height = viewport.height
      rendering = item.render({ canvasContext: canvas.current.getContext('2d'), viewport })
      return rendering.promise
    }).then(() => { if (active) setState('ready') })
      .catch(() => { if (active) setState('error') })
    return () => { active = false; rendering?.cancel() }
  }, [pdf, page])
  return <div className="pdf-page">
    {state === 'loading' && <p role="status">Đang vẽ trang PDF…</p>}
    {state === 'error' && <p role="alert">Không thể hiển thị trang PDF. Bạn có thể mở PDF trong tab mới.</p>}
    <canvas ref={canvas} role="img" aria-label={`PDF trang ${page}`} data-rendered-page={state === 'ready' ? page : undefined} />
  </div>
}

export default function PdfPreview({ url, initialPage }) {
  const [pdf, setPdf] = useState(null)
  const [error, setError] = useState('')
  const [page, setPage] = useState(Number.isInteger(initialPage) && initialPage > 0 ? initialPage : 1)
  useEffect(() => {
    let active = true
    const loading = getDocument({ url, isEvalSupported: false, disableRange: true })
    loading.promise.then(value => { if (active) setPdf(value) })
      .catch(() => { if (active) setError('Không thể đọc PDF. Bạn có thể mở PDF trong tab mới.') })
    return () => { active = false; void loading.destroy().catch(() => {}) }
  }, [url])
  return <section className="pdf-preview" aria-label="Xem PDF">
    {error && <p role="alert">{error}</p>}
    {!pdf && !error && <p role="status">Đang đọc PDF…</p>}
    {pdf && (page > pdf.numPages ? <p role="alert">Trang nguồn vượt số trang PDF. Hãy mở tệp để đối chiếu.</p> : <>
      <div className="pdf-toolbar"><button type="button" className="secondary-button" disabled={page <= 1} onClick={() => setPage(value => value - 1)}>Trang trước</button><span aria-live="polite">Trang {page} / {pdf.numPages}</span><button type="button" className="secondary-button" disabled={page >= pdf.numPages} onClick={() => setPage(value => value + 1)}>Trang sau</button></div>
      <PdfPage key={page} pdf={pdf} page={page} />
    </>)}
  </section>
}
