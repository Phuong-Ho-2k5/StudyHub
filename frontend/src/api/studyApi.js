import { request } from './authApi.js'

function authorized(token, options = {}) {
  return { ...options, headers: { Authorization: `Bearer ${token}`, ...options.headers } }
}

export function buildCourseQuery({ workspaceId = '', status = '', q = '', page = 0 }) {
  const params = new URLSearchParams()
  if (workspaceId) params.set('workspaceId', workspaceId)
  if (status) params.set('status', status)
  if (q.trim()) params.set('q', q.trim())
  params.set('page', String(page))
  params.set('size', '10')
  params.set('sort', 'name,asc')
  return `?${params}`
}

export const listWorkspaces = (token) => request('/api/workspaces', authorized(token))
export const createWorkspace = (token, data) => request('/api/workspaces', authorized(token, {
  method: 'POST', body: JSON.stringify(data),
}))
export const updateWorkspace = (token, id, data) => request(`/api/workspaces/${id}`, authorized(token, {
  method: 'PUT', body: JSON.stringify(data),
}))
export const deleteWorkspace = (token, id) => request(`/api/workspaces/${id}`, authorized(token, {
  method: 'DELETE',
}))

export const listCourses = (token, filters) => request(`/api/courses${buildCourseQuery(filters)}`, authorized(token))
export const createCourse = (token, workspaceId, data) => request(`/api/workspaces/${workspaceId}/courses`, authorized(token, {
  method: 'POST', body: JSON.stringify(data),
}))
export const updateCourse = (token, id, data) => request(`/api/courses/${id}`, authorized(token, {
  method: 'PUT', body: JSON.stringify(data),
}))
export const deleteCourse = (token, id) => request(`/api/courses/${id}`, authorized(token, {
  method: 'DELETE',
}))

export const getCourse = (token, id) => request(`/api/courses/${id}`, authorized(token))

export function buildDocumentQuery({ courseId, q = '', page = 0 }) {
  const params = new URLSearchParams({ courseId: String(courseId) })
  if (q.trim()) params.set('q', q.trim())
  params.set('page', String(page))
  params.set('size', '10')
  params.set('sort', 'title,asc')
  return `?${params}`
}

export const listDocuments = (token, filters) => request(`/api/documents${buildDocumentQuery(filters)}`, authorized(token))
export const createDocument = (token, courseId, data) => request(`/api/courses/${courseId}/documents`, authorized(token, {
  method: 'POST', body: JSON.stringify(data),
}))
export const updateDocument = (token, id, data) => request(`/api/documents/${id}`, authorized(token, {
  method: 'PUT', body: JSON.stringify(data),
}))
export const deleteDocument = (token, id) => request(`/api/documents/${id}`, authorized(token, { method: 'DELETE' }))

export function buildLowConfidenceQuery(threshold) {
  return `?${new URLSearchParams({ confidenceBelow: String(threshold) })}`
}

export const listConcepts = (token, courseId) => request(`/api/concepts?${new URLSearchParams({ courseId: String(courseId) })}`, authorized(token))
export const listLowConfidenceConcepts = (token, courseId, threshold) => request(`/api/courses/${courseId}/concepts${buildLowConfidenceQuery(threshold)}`, authorized(token))
export const createConcept = (token, courseId, data) => request(`/api/courses/${courseId}/concepts`, authorized(token, {
  method: 'POST', body: JSON.stringify(data),
}))
export const updateConcept = (token, id, data) => request(`/api/concepts/${id}`, authorized(token, {
  method: 'PUT', body: JSON.stringify(data),
}))
export const deleteConcept = (token, id) => request(`/api/concepts/${id}`, authorized(token, { method: 'DELETE' }))
export const listPrerequisites = (token, id) => request(`/api/concepts/${id}/prerequisites`, authorized(token))
export const addPrerequisite = (token, id, prerequisiteId) => request(`/api/concepts/${id}/prerequisites`, authorized(token, {
  method: 'POST', body: JSON.stringify({ prerequisiteId }),
}))
export const removePrerequisite = (token, id, prerequisiteId) => request(`/api/concepts/${id}/prerequisites/${prerequisiteId}`, authorized(token, { method: 'DELETE' }))
