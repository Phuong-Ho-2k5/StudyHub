import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useLearning } from '../context/LearningContext'

export function AppHeader() {
  const { user, logout } = useAuth()
  const { unreadCount, notificationError, adminState, checkAdmin, refreshNotifications } = useLearning()
  return <header className="dashboard-header app-header">
    <NavLink className="brand" to="/welcome"><span className="brand-mark" aria-hidden="true">S</span><span>StudyHub</span></NavLink>
    <nav className="app-nav" aria-label="Điều hướng chính">
      <NavLink to="/welcome">Bảng học tập</NavLink>
      <NavLink to="/notifications">Thông báo {unreadCount !== null && <span className="count-pill" aria-label={`${unreadCount} chưa đọc`}>{unreadCount}</span>}</NavLink>
      {adminState === 'allowed' && <NavLink to="/admin">Quản trị</NavLink>}
      {adminState === 'error' && <button type="button" onClick={checkAdmin}>Thử lại kiểm tra quyền</button>}
      {notificationError && <button type="button" onClick={refreshNotifications}>Tải lại số thông báo</button>}
    </nav>
    <div className="header-account"><span title={user.email}>{user.email}</span><button className="secondary-button" type="button" onClick={logout}>Đăng xuất</button></div>
  </header>
}
