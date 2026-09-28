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
