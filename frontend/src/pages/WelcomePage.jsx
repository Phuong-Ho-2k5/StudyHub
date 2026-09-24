import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function WelcomePage() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <main className="welcome-page">
      <header className="welcome-header">
        <div className="brand" aria-label="StudyHub">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>StudyHub</span>
        </div>
        <button className="secondary-button" type="button" onClick={handleLogout}>
          Đăng xuất
        </button>
      </header>

      <section className="welcome-content">
        <div className="welcome-copy">
          <p className="welcome-kicker">Tài khoản đã sẵn sàng</p>
          <h1>
            Chào bạn,
            <span className="welcome-email">{user.email}</span>
          </h1>
          <p className="welcome-lead">
            Tài khoản của bạn đã sẵn sàng. Không gian học tập và khóa học sẽ
            được bổ sung ở chặng tiếp theo.
          </p>
        </div>

        <aside className="milestone-card" aria-label="Trạng thái dự án">
          <span>Mốc hiện tại</span>
          <h2>Đăng ký và đăng nhập đã hoạt động</h2>
          <p>
            Nền tảng xác thực đã hoàn thành để chuẩn bị cho Workspace và Course.
          </p>
        </aside>
      </section>
    </main>
  )
}
