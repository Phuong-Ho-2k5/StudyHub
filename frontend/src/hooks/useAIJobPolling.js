import { useEffect, useEffectEvent, useState } from 'react'
import { getAIJob } from '../api/aiJobsApi.js'
import { ApiError } from '../api/authApi.js'
export function useAIJobPolling({ token, courseId, jobId, onCompleted }) {
  const [snapshot, setSnapshot] = useState(null)
  const [revision, setRevision] = useState(0)
  const complete = useEffectEvent(onCompleted ?? (() => {}))
  useEffect(() => {
    if (!jobId) return undefined
    let active = true, timer, running = false, stopped = false, delivered = false
    const controller = new AbortController()
    async function poll() {
      if (!active || running || stopped || document.visibilityState === 'hidden') return
      running = true
      try {
        const job = await getAIJob(token, jobId, { signal: controller.signal })
        if (!active) return
        if (job.id !== jobId || String(job.courseId) !== String(courseId)) throw new ApiError('Không thể truy cập tác vụ này.', 404)
        if (!['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'].includes(job.status)) throw new ApiError('Trạng thái không hợp lệ.', 502)
        const resultType = { INGEST_DOCUMENT: 'DOCUMENT_INDEX', EXTRACT_CONCEPTS: 'CONCEPT_SUGGESTION_BATCH', GENERATE_QUIZ: 'QUIZ' }[job.type]
        if (!resultType || (job.type !== 'GENERATE_QUIZ' && !Number.isSafeInteger(job.documentId))) throw new ApiError('Tác vụ không hợp lệ.', 502)
        if (job.status === 'COMPLETED' && (job.result?.type !== resultType || typeof job.result.id !== 'string' || !job.result.id.trim() || job.result.id.length > 100 || (resultType === 'QUIZ' && !/^[1-9][0-9]*$/.test(job.result.id)))) throw new ApiError('Kết quả tác vụ không hợp lệ.', 502)
        if (['COMPLETED','FAILED'].includes(job.status)) stopped = true
        setSnapshot({ token, courseId, jobId, job, pollError: null })
        if (job.status === 'COMPLETED' && !delivered) { delivered = true; complete(job) }
      } catch (error) {
        if (!active) return
        stopped = true
        setSnapshot({ token, courseId, jobId, job: null, pollError: error })
      } finally {
        running = false
        if (active && !stopped) timer = setTimeout(poll, 3000)
      }
    }
    function visible() { clearTimeout(timer); if (document.visibilityState === 'visible') void poll() }
    void poll()
    document.addEventListener('visibilitychange', visible)
    return () => { active = false; clearTimeout(timer); controller.abort(); document.removeEventListener('visibilitychange', visible) }
  }, [token, courseId, jobId, revision])
  const current = snapshot?.token === token && snapshot?.courseId === courseId && snapshot?.jobId === jobId ? snapshot : null
  return { job: current?.job ?? null, pollError: current?.pollError ?? null, refresh: () => setRevision(value => value + 1) }
}
