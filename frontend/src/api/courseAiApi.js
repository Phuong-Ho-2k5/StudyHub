import { authorizedRequest as api, pageQuery } from './authorizedRequest.js'
import { request } from './authApi.js'

export const saveMessageFeedback = (token, messageId, { rating, note }) => api(token, `/api/ai/messages/${messageId}/feedback`, 'POST', { rating, note })

export const searchCourse = (token, courseId, { query, topK = 5 }) => api(token, `/api/courses/${courseId}/search`, 'POST', { query, topK })
export const chatCourse = (token, courseId, { question, topK = 5, sessionId }) => api(token, `/api/courses/${courseId}/ai/chat`, 'POST', { question, topK, ...(sessionId === undefined ? {} : { sessionId }) })
export const createChatSession = (token, courseId, { title }) => api(token, `/api/courses/${courseId}/ai/chat-sessions`, 'POST', { title })
export const listChatSessions = (token, courseId, { page = 0, size = 10 } = {}) => api(token, `/api/courses/${courseId}/ai/chat-sessions${pageQuery(page, size)}`)
export const listChatMessages = (token, courseId, sessionId, { page = 0, size = 20 } = {}) => api(token, `/api/courses/${courseId}/ai/chat-sessions/${sessionId}/messages${pageQuery(page, size)}`)
export const ingestDocument = (token, documentId) => api(token, `/api/documents/${documentId}/ingest`, 'POST')
export const getDocument = (token, documentId, { signal } = {}) => request(`/api/documents/${documentId}`, { method: 'GET', headers: { Authorization: `Bearer ${token}` }, signal })
export const getSource = (token, courseId, documentId, chunkId) => api(token, `/api/courses/${courseId}/documents/${documentId}/chunks/${chunkId}/source`)
