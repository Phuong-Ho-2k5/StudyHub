import { useEffect, useRef } from 'react'

export function EditorDialog({ title, busy, onClose, children, wide = false }) {
  const dialogRef = useRef(null)
  useEffect(() => {
    const previous = document.activeElement
    const dialog = dialogRef.current
    const field = dialog.querySelector('input:not([disabled]), textarea:not([disabled]), select:not([disabled])')
    ;(field ?? dialog.querySelector('button'))?.focus()
    return () => { if (previous?.isConnected) previous.focus() }
  }, [])

  function keyDown(event) {
    if (event.key === 'Escape' && !busy) { event.preventDefault(); onClose() }
    if (event.key !== 'Tab') return
    const nodes = [...dialogRef.current.querySelectorAll('button, input, textarea, select, a[href]')]
      .filter((node) => !node.disabled && node.tabIndex !== -1 && node.getClientRects().length)
    const first = nodes[0]
    const last = nodes.at(-1)
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
  }

  return <div className="dialog-backdrop" onMouseDown={(event) => {
    if (!busy && event.target === event.currentTarget) onClose()
  }} onKeyDown={keyDown}>
    <section ref={dialogRef} className={`editor-dialog${wide ? ' question-dialog' : ''}`}
      role="dialog" aria-modal="true" aria-labelledby="quiz-dialog-title">
      <div className="dialog-heading"><h2 id="quiz-dialog-title">{title}</h2>
        <button className="icon-button" type="button" onClick={onClose} disabled={busy} aria-label="Đóng">×</button>
      </div>
      {children}
    </section>
  </div>
}
