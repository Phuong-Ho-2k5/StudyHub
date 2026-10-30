import { useEffect, useEffectEvent, useState } from 'react'
import { getDocument } from '../api/courseAiApi.js'

export function useDocumentIngestionPolling({ token, courseId, documents, onDocumentUpdated }) {
  const [error, setError] = useState(false)
  const [revision, setRevision] = useState(0)
  const pendingKey = documents.filter(item => ['PENDING', 'PROCESSING'].includes(item.ingestionStatus)).map(item => item.id).join(',')
  const update = useEffectEvent(onDocumentUpdated)
  useEffect(() => {
    if (!pendingKey) return undefined
    let active = true
    let timer
    let running = false
    let failed = false
    const controller = new AbortController()
    async function poll() {
      if (!active || running || document.visibilityState === 'hidden') return
      running = true
      try {
        const rows = []
        for (const id of pendingKey.split(',')) {
          const item = await getDocument(token, id, { signal: controller.signal })
          if (!active) return
          rows.push(item)
        }
        // Updating a row changes pendingKey. Publish the whole batch together so
        // a replacement effect cannot race the remainder of this request loop.
        for (const row of rows) update(row)
        if (active) { failed = false; setError(false) }
      } catch {
        if (active) { failed = true; setError(true) }
      } finally {
        running = false
        if (active && !failed) timer = setTimeout(poll, 3000)
      }
    }
    function visible() {
      clearTimeout(timer)
      if (document.visibilityState === 'visible' && !failed) void poll()
    }
    timer = setTimeout(poll, revision ? 0 : 3000)
    document.addEventListener('visibilitychange', visible)
    return () => { active = false; controller.abort(); clearTimeout(timer); document.removeEventListener('visibilitychange', visible) }
  }, [token, courseId, pendingKey, revision])
  return { error: Boolean(pendingKey) && error, retry: () => setRevision(value => value + 1) }
}
