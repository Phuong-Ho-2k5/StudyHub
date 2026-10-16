export const sessionKey = (email) => `studyhub_session:${encodeURIComponent(email)}`

export function readSession(storage, email) {
  try {
    const data = JSON.parse(storage.getItem(sessionKey(email)) || 'null')
    if (!data || !Number.isSafeInteger(data.id) || data.id <= 0
      || !Number.isSafeInteger(data.courseId) || data.courseId <= 0
      || typeof data.startTime !== 'string' || !Number.isFinite(Date.parse(data.startTime))) return null
    return data
  } catch { return null }
}
export const saveSession = (storage, email, session) => storage.setItem(sessionKey(email), JSON.stringify(session))
export function clearSession(storage, email, expectedId) {
  if (expectedId !== undefined && readSession(storage, email)?.id !== expectedId) return false
  storage.removeItem(sessionKey(email))
  return true
}

const queues = new Map()
// Web Locks coordinate windows/tabs; the queue also supports environments without Web Locks.
export function withSessionLock(email, operation, locks = globalThis.navigator?.locks) {
  const key = sessionKey(email)
  if (locks?.request) return locks.request(key, operation)
  const previous = queues.get(key) || Promise.resolve()
  const current = previous.catch(() => {}).then(operation)
  queues.set(key, current)
  return current.finally(() => { if (queues.get(key) === current) queues.delete(key) })
}
