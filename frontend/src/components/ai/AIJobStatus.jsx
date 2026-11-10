import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAIJobPolling } from '../../hooks/useAIJobPolling.js'
import { getConceptBatch } from '../../api/aiJobsApi.js'
import { request } from '../../api/authApi.js'
import { ConceptSuggestionBatch } from './ConceptSuggestionBatch.jsx'
const labels = { PENDING: 'Đang chờ xử lý', PROCESSING: 'AI đang xử lý', COMPLETED: 'Đã hoàn tất', FAILED: 'Xử lý thất bại' }
const errors = { AI_UNAVAILABLE: 'Dịch vụ AI hiện không sẵn sàng.', AI_TIMEOUT: 'Dịch vụ AI phản hồi quá lâu.', AI_INVALID_RESPONSE: 'Kết quả AI không hợp lệ.', NO_CONTENT: 'Không đủ nội dung để xử lý.', SOURCE_CHANGED: 'Nguồn tài liệu đã thay đổi.', JOB_FAILED: 'Không thể hoàn tất tác vụ AI.' }
function JobResult({ job, token }) {
  const [output, setOutput] = useState(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    let active = true; const controller = new AbortController()
    const result = job.result
    const load = result?.type === 'CONCEPT_SUGGESTION_BATCH'
      ? getConceptBatch(token, job.courseId, job.documentId, result.id, { signal: controller.signal })
      : request(result?.type === 'QUIZ' ? `/api/quizzes/${encodeURIComponent(result.id)}` : `/api/documents/${job.documentId}`, { headers: { Authorization: `Bearer ${token}` }, signal: controller.signal })
    load.then(data => {
      if (!active) return
      const course = data.courseId
      if (String(course) !== String(job.courseId)) throw new Error('Kết quả không thuộc khóa học này.')
      if (String(data.id) !== String(result?.type === 'DOCUMENT_INDEX' ? job.documentId : result?.id)) throw new Error('Kết quả không khớp tác vụ.')
      if (result?.type === 'CONCEPT_SUGGESTION_BATCH') {
        if (data.documentId !== job.documentId || typeof data.sourceCurrent !== 'boolean' || !Array.isArray(data.suggestions) || !data.suggestions.every(item =>
          Number.isSafeInteger(item.id) && item.id > 0 && typeof item.name === 'string' && typeof item.description === 'string' && ['PENDING','CONFIRMED'].includes(item.state) &&
          Array.isArray(item.citations) && item.citations.every(citation => Number.isSafeInteger(citation.chunkId) && citation.documentId === job.documentId && typeof citation.documentTitle === 'string' && typeof citation.quote === 'string')
        )) throw new Error('Batch không hợp lệ.')
      }
      setOutput(data); setError('')
    }).catch(() => { if (active) setError('Không thể tải kết quả. Kết quả có thể đã bị xóa hoặc bạn không còn quyền truy cập.') })
    return () => { active = false; controller.abort() }
  }, [job, token, revision])
  if (error) return <div role="alert"><p>{error}</p><button className="secondary-button" onClick={() => setRevision(v => v + 1)}>Tải lại kết quả</button></div>
  if (!output) return <p role="status">Đang tải kết quả…</p>
  if (job.result?.type === 'QUIZ') return <Link className="text-link" to={`/courses/${job.courseId}/quizzes/${job.result.id}`}>Mở Quiz để duyệt</Link>
  if (job.result?.type === 'CONCEPT_SUGGESTION_BATCH') return <ConceptSuggestionBatch batch={output} token={token} />
  return <p>Đã xử lý tài liệu. {output.publishedIngestionId === job.result?.id ? 'Bản này đang được dùng cho AI.' : 'Tài liệu hiện đang dùng một bản khác.'}</p>
}
export function AIJobStatus({ token, courseId, jobId, onCompleted }) {
  const { job, pollError, refresh } = useAIJobPolling({ token, courseId, jobId, onCompleted })
  return <section className="ai-job-status" aria-label="Tác vụ AI">
    <h3>Tác vụ AI</h3>
    {pollError ? <div role="alert"><p>{[400,404].includes(pollError.status) ? 'Không thể truy cập tác vụ này.' : 'Không thể đọc trạng thái tác vụ. Tác vụ có thể vẫn đang chạy.'}</p>
      {![400,401,404].includes(pollError.status) && <button className="secondary-button" onClick={refresh}>Cập nhật trạng thái</button>}</div>
      : <p role="status" aria-live="polite">{job ? labels[job.status] : 'Đang đọc trạng thái…'}</p>}
    {job?.status === 'FAILED' && <p role="alert">{errors[job.errorCode] ?? errors.JOB_FAILED}</p>}
    {job?.status === 'COMPLETED' && <JobResult key={job.id} job={job} token={token} />}
  </section>
}
