import { validateChatResponse } from './aiDisplay.js'

export const validHistoryId = value => Number.isSafeInteger(value) && value > 0
const timestamp = value => typeof value === 'string' && Number.isFinite(Date.parse(value))
const validPage = data => data && Array.isArray(data.content) &&
  Number.isInteger(data.number) && data.number >= 0 && Number.isInteger(data.size) && data.size >= 1 && data.size <= 100 &&
  Number.isInteger(data.totalPages) && data.totalPages >= 0 && Number.isInteger(data.totalElements) && data.totalElements >= 0 &&
  typeof data.last === 'boolean'

export function validateSession(data, courseId) {
  return !!data && validHistoryId(data.id) && validHistoryId(data.courseId) && String(data.courseId) === String(courseId) &&
    typeof data.title === 'string' && data.title.trim().length > 0 && data.title.length <= 150 && timestamp(data.createdAt) && timestamp(data.updatedAt)
}

export function validateSessionPage(data, courseId) {
  return !!validPage(data) && data.content.every(item => validateSession(item, courseId))
}

export function validateMessagePage(data, sessionId) {
  return !!validPage(data) && data.content.every(item => validHistoryId(item.id) && item.sessionId === sessionId &&
    typeof item.content === 'string' && timestamp(item.createdAt) && Array.isArray(item.citations) &&
    (item.role === 'USER'
      ? item.status === null && item.model === null && item.promptVersion === null && item.citations.length === 0
      : item.role === 'ASSISTANT' && (item.model === null || typeof item.model === 'string') && typeof item.promptVersion === 'string' &&
        validateChatResponse({ ...item, answer: item.content })))
}

export function mergeMessages(existing, incoming) {
  return [...new Map([...existing, ...incoming].map(item => [item.id, item])).values()].sort((a, b) => a.id - b.id)
}
