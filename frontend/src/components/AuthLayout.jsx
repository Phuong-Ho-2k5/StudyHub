import { Link } from 'react-router-dom'

export function AuthLayout({ eyebrow, title, description, children }) {
  return (
    <main className="auth-page">
      <section className="auth-story" aria-label="Giới thiệu StudyHub">
        <Link className="brand" to="/login" aria-label="StudyHub - trang đăng nhập">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>StudyHub</span>
        </Link>

        <div className="story-main">
          <p className="story-kicker">Không gian học tập cá nhân</p>
          <h1>Một chỗ gọn gàng cho việc học mỗi ngày.</h1>
          <p>
            Bắt đầu bằng một tài khoản. Những phần còn lại sẽ được xây từng bước,
            vừa đủ cho cách bạn học.
          </p>

          <div className="study-note" aria-label="Định hướng của StudyHub">
            <p>StudyHub hướng tới</p>
            <ul>
              <li>Ghi chú tập trung</li>
              <li>Tiến độ rõ ràng</li>
              <li>Học theo nhịp của bạn</li>
            </ul>
          </div>
        </div>

        <span className="story-footer">Một project nhỏ, làm từng phần cho tử tế.</span>
      </section>

      <section className="auth-panel">
        <div className="form-card">
          <p className="form-eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
          <p className="form-intro">{description}</p>
          {children}
        </div>
      </section>
    </main>
  )
}
