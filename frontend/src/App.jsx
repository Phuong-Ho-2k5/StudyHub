import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { WelcomePage } from './pages/WelcomePage'

function PublicOnlyRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? <Navigate to="/welcome" replace /> : children
}

function App() {
  const { isAuthenticated } = useAuth()

  return (
    <Routes>
      <Route
        path="/login"
        element={<PublicOnlyRoute><LoginPage /></PublicOnlyRoute>}
      />
      <Route
        path="/register"
        element={<PublicOnlyRoute><RegisterPage /></PublicOnlyRoute>}
      />
      <Route
        path="/welcome"
        element={(
          <ProtectedRoute>
            <WelcomePage />
          </ProtectedRoute>
        )}
      />
      <Route path="/" element={<Navigate to={isAuthenticated ? '/welcome' : '/login'} replace />} />
      <Route path="*" element={<Navigate to={isAuthenticated ? '/welcome' : '/login'} replace />} />
    </Routes>
  )
}

export default App
