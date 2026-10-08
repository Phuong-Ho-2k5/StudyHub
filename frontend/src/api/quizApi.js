import { request } from './authApi.js'

function authorized(token, method = 'GET', data) {
  return { method, headers: { Authorization: `Bearer ${token}` },
    ...(data === undefined ? {} : { body: JSON.stringify(data) }) }
}

export const listQuizzes = (token, courseId) => request(`/api/courses/${courseId}/quizzes`, authorized(token))
export const createQuiz = (token, courseId, data) => request(`/api/courses/${courseId}/quizzes`, authorized(token, 'POST', data))
export const getQuiz = (token, id) => request(`/api/quizzes/${id}`, authorized(token))
export const updateQuiz = (token, id, data) => request(`/api/quizzes/${id}`, authorized(token, 'PUT', data))
export const deleteQuiz = (token, id) => request(`/api/quizzes/${id}`, authorized(token, 'DELETE'))
export const listQuestions = (token, quizId) => request(`/api/quizzes/${quizId}/questions`, authorized(token))
export const createQuestion = (token, quizId, data) => request(`/api/quizzes/${quizId}/questions`, authorized(token, 'POST', data))
export const updateQuestion = (token, id, data) => request(`/api/questions/${id}`, authorized(token, 'PUT', data))
export const deleteQuestion = (token, id) => request(`/api/questions/${id}`, authorized(token, 'DELETE'))
export const submitQuiz = (token, quizId, data) => request(`/api/quizzes/${quizId}/submit`, authorized(token, 'POST', data))
