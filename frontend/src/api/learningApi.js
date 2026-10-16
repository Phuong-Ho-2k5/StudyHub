import { authorizedRequest as api, pageQuery } from './authorizedRequest.js'

export const getStatistics = (token) => api(token, '/api/users/me/statistics')
export const getQuizHistory = (token, page = 0) => api(token, `/api/users/me/quiz-history${pageQuery(page, 10)}`)
export const getProgress = (token, courseId) => api(token, `/api/courses/${courseId}/progress`)
export const startSession = (token, courseId) => api(token, '/api/study-sessions/start', 'POST', { courseId: Number(courseId) })
export const finishSession = (token, id) => api(token, `/api/study-sessions/${id}/finish`, 'POST')
