import { authorizedRequest as api, pageQuery } from './authorizedRequest.js'

export const listNotifications = (token, page = 0) => api(token, `/api/notifications${pageQuery(page)}`)
export const getUnreadCount = (token) => api(token, '/api/notifications/unread-count')
export const markRead = (token, id) => api(token, `/api/notifications/${id}/read`, 'PATCH')
