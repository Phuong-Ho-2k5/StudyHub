import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { DashboardPage } from './pages/DashboardPage'
import { CourseDetailPage } from './pages/CourseDetailPage'
import { QuizDetailPage } from './pages/QuizDetailPage'
import { DiscussionDetailPage } from './pages/DiscussionDetailPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { AdminPage } from './pages/AdminPage'
import { LearningProvider } from './context/LearningContext'
import { StudySessionProvider } from './context/StudySessionContext'

function SessionProviders({ children }) {
  const { token, isAuthenticated } = useAuth()
  return isAuthenticated ? <LearningProvider key={token}><StudySessionProvider>{children}</StudySessionProvider></LearningProvider> : children
}

function PublicOnlyRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? <Navigate to="/welcome" replace /> : children
}

function App() {
  const { isAuthenticated } = useAuth()

  return (
    <SessionProviders><Routes>
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
            <DashboardPage />
          </ProtectedRoute>
        )}
      />
      <Route path="/courses/:courseId" element={<ProtectedRoute><CourseDetailPage /></ProtectedRoute>} />
      <Route path="/courses/:courseId/quizzes/:quizId" element={<ProtectedRoute><QuizDetailPage /></ProtectedRoute>} />
      <Route path="/courses/:courseId/discussions/:postId" element={<ProtectedRoute><DiscussionDetailPage /></ProtectedRoute>} />
      <Route path="/notifications" element={<ProtectedRoute><NotificationsPage /></ProtectedRoute>} />
      <Route path="/admin" element={<ProtectedRoute><AdminPage /></ProtectedRoute>} />
      <Route path="/" element={<Navigate to={isAuthenticated ? '/welcome' : '/login'} replace />} />
      <Route path="*" element={<Navigate to={isAuthenticated ? '/welcome' : '/login'} replace />} />
    </Routes></SessionProviders>
  )
}

export default App
