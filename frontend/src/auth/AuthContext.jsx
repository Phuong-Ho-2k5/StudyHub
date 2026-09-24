/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useState } from 'react'
import { readToken, TOKEN_STORAGE_KEY } from './token'

const AuthContext = createContext(null)

function loadStoredSession() {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  const tokenData = readToken(token)

  if (!tokenData) {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    return { token: null, tokenData: null }
  }

  return { token, tokenData }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(loadStoredSession)

  function login(accessToken) {
    const tokenData = readToken(accessToken)

    if (!tokenData) {
      throw new Error('Máy chủ trả về phiên đăng nhập không hợp lệ.')
    }

    localStorage.setItem(TOKEN_STORAGE_KEY, accessToken)
    setSession({ token: accessToken, tokenData })
  }

  function logout() {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    setSession({ token: null, tokenData: null })
  }

  useEffect(() => {
    if (!session.tokenData) {
      return undefined
    }

    const timeRemaining = session.tokenData.expiresAt - Date.now()
    const timeout = window.setTimeout(logout, Math.max(0, timeRemaining))

    return () => window.clearTimeout(timeout)
  }, [session.tokenData])

  const value = {
    token: session.token,
    user: session.tokenData ? { email: session.tokenData.email } : null,
    isAuthenticated: Boolean(session.tokenData),
    login,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth phải được sử dụng bên trong AuthProvider.')
  }

  return context
}
