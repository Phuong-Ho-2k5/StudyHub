const validId = value => Number.isSafeInteger(value) && value > 0
const timestamp = value => typeof value === 'string' && Number.isFinite(Date.parse(value))

export function validateFeedback(data, messageId) {
  return !!data && validId(data.id) && validId(data.messageId) && data.messageId === messageId &&
    ['HELPFUL', 'NOT_HELPFUL'].includes(data.rating) &&
    (data.note === null || (typeof data.note === 'string' && data.note.length <= 2000)) &&
    timestamp(data.createdAt) && timestamp(data.updatedAt)
}
