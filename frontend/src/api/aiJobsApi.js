import { request, ApiError } from './authApi.js'
export const validJobId = id => typeof id === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)
export async function getAIJob(token, jobId, { signal } = {}) {
  if (!validJobId(jobId)) throw new ApiError('Invalid job ID', 400)
  return request(`/api/ai/jobs/${jobId}`, { headers: { Authorization: `Bearer ${token}` }, signal })
}
export function submitQuizJob(token, { courseId, documentId, key, request: data }) {
  const path = documentId ? `/api/documents/${documentId}/ai/generate-quiz` : `/api/courses/${courseId}/ai/generate-quiz`
  return request(path, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': key }, body: JSON.stringify(data) })
}
export const submitConceptJob = (token, courseId, documentId, data) => request(`/api/courses/${courseId}/documents/${documentId}/ai/concept-suggestions`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify(data) })
export const getConceptBatch = (token, courseId, documentId, batchId, { signal } = {}) => request(`/api/courses/${courseId}/documents/${documentId}/ai/concept-suggestions/${encodeURIComponent(batchId)}`, { headers: { Authorization: `Bearer ${token}` }, signal })
export const confirmConceptSuggestion = (token, courseId, documentId, batchId, item) => request(`/api/courses/${courseId}/documents/${documentId}/ai/concept-suggestions/${encodeURIComponent(batchId)}/items/${item.id}/confirm`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify({ name: item.name, description: item.description }) })
