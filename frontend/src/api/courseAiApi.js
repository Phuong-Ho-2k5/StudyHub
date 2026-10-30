import { authorizedRequest as api } from './authorizedRequest.js'
import { request } from './authApi.js'

export const searchCourse = (token, courseId, { query, topK = 5 }) => api(token, `/api/courses/${courseId}/search`, 'POST', { query, topK })
export const chatCourse = (token, courseId, { question, topK = 5 }) => api(token, `/api/courses/${courseId}/ai/chat`, 'POST', { question, topK })
export const ingestDocument = (token, documentId) => api(token, `/api/documents/${documentId}/ingest`, 'POST')
export const getDocument = (token, documentId, { signal } = {}) => request(`/api/documents/${documentId}`, { method: 'GET', headers: { Authorization: `Bearer ${token}` }, signal })
export const getSource = (token, courseId, documentId, chunkId) => api(token, `/api/courses/${courseId}/documents/${documentId}/chunks/${chunkId}/source`)
