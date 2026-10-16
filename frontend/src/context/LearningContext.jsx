/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { getAdminStatistics } from '../api/adminApi'
import { getUnreadCount } from '../api/notificationApi'
import { useAuth } from '../auth/AuthContext'

const LearningContext = createContext(null)

export function LearningProvider({ children }) {
  const { token } = useAuth()
  const [unreadCount, setUnreadCount] = useState(null)
  const [notificationError, setNotificationError] = useState(null)
  const [adminState, setAdminState] = useState('loading')
  const [learningRevision, setRevision] = useState(0)
  const [notificationRevision, setNotificationRevision] = useState(0)
  const [adminRevision, setAdminRevision] = useState(0)
  const permissionEpoch = useRef(0)
  const refreshNotifications = useCallback(() => setNotificationRevision((n) => n + 1), [])
  const checkAdmin = useCallback(() => setAdminRevision((n) => n + 1), [])
  const invalidateLearning = useCallback(() => { setRevision((n) => n + 1); refreshNotifications() }, [refreshNotifications])
  const denyAdmin = useCallback(() => { permissionEpoch.current++; setAdminState('denied') }, [])

  useEffect(() => {
    let active = true
    getUnreadCount(token).then((data) => { if (active) { setUnreadCount(data.unreadCount); setNotificationError(null) } })
      .catch((error) => { if (active) setNotificationError(error) })
    return () => { active = false }
  }, [token, notificationRevision])
  useEffect(() => {
    let active = true
    const epoch = ++permissionEpoch.current
    getAdminStatistics(token).then(() => { if (active && epoch === permissionEpoch.current) setAdminState('allowed') })
      .catch((error) => { if (active && epoch === permissionEpoch.current) setAdminState(error.status === 403 ? 'denied' : 'error') })
    return () => { active = false }
  }, [token, adminRevision])
  useEffect(() => {
    const refresh = () => { if (document.visibilityState === 'visible') { invalidateLearning(); checkAdmin() } }
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', refresh)
    return () => { window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh) }
  }, [invalidateLearning, checkAdmin])

  return <LearningContext.Provider value={{ unreadCount, notificationError, adminState, refreshNotifications, checkAdmin, denyAdmin, invalidateLearning, learningRevision }}>{children}</LearningContext.Provider>
}

export const useLearning = () => useContext(LearningContext)
