import { request } from './authApi.js'

const headers = token => ({ Authorization: `Bearer ${token}` })
const mutate = (token, path, expectedRevision) => request(path, {
  method: 'POST', headers: headers(token), body: JSON.stringify({ expectedRevision }),
})

export const getQuizReview = (token, quizId) => request(`/api/quizzes/${quizId}/review`, { headers: headers(token) })
export const reviewQuizQuestion = (token, quizId, questionId, revision) => mutate(token, `/api/quizzes/${quizId}/questions/${questionId}/review`, revision)
export const unreviewQuizQuestion = (token, quizId, questionId, revision) => mutate(token, `/api/quizzes/${quizId}/questions/${questionId}/unreview`, revision)
export const confirmAiQuiz = (token, quizId, revision) => mutate(token, `/api/quizzes/${quizId}/confirm`, revision)
export const publishAiQuiz = (token, quizId, revision) => mutate(token, `/api/quizzes/${quizId}/publish`, revision)
export const archiveAiQuiz = (token, quizId, revision) => mutate(token, `/api/quizzes/${quizId}/archive`, revision)
