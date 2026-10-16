import { authorizedRequest as api, pageQuery } from './authorizedRequest.js'

export const getAdminStatistics = (token) => api(token, '/api/admin/statistics')
export const listUsers = (token, page = 0) => api(token, `/api/admin/users${pageQuery(page)}`)
export const getUser = (token, id) => api(token, `/api/admin/users/${id}`)
export const updateUserRole = (token, id, role) => api(token, `/api/admin/users/${id}/role`, 'PATCH', { role })
