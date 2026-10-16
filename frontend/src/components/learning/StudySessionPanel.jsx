import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useStudySession } from '../../context/StudySessionContext'

export function StudySessionPanel({ courseId }) {
  const { session, busy, error, start, finish, unavailable, dismissUnavailable } = useStudySession()
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    if (!session) return undefined
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [session])
  const seconds = session ? Math.max(0, Math.floor((now - Date.parse(session.startTime)) / 1000)) : 0
  const clock = [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60].map((n) => String(n).padStart(2, '0')).join(':')
  const otherCourse = session && String(session.courseId) !== String(courseId)
  return <section className="course-panel session-panel" aria-label="Phiên học">
    <div><p className="section-kicker">Thời gian tập trung</p><h2>{session ? 'Phiên học đang diễn ra' : 'Bắt đầu một phiên học'}</h2>
      {session ? <><p className="session-clock" aria-label={`Thời gian học ${clock}`}>{clock}</p>{otherCourse && <p>Đang học ở <Link to={`/courses/${session.courseId}`}>khóa học #{session.courseId}</Link>.</p>}</>
        : <p className="muted">Thời gian được ghi nhận vào thống kê khi kết thúc phiên.</p>}
    </div>
    <div className="session-actions">{session ? <button className="primary-button" type="button" onClick={finish} disabled={busy}>{busy ? 'Đang xử lý…' : 'Kết thúc phiên'}</button>
      : <button className="primary-button" type="button" onClick={() => start(courseId)} disabled={busy}>{busy ? 'Đang bắt đầu…' : 'Bắt đầu học'}</button>}</div>
    {error && <p className="form-message error session-error" role="alert">{error}</p>}
    {unavailable && <button className="secondary-button" type="button" onClick={dismissUnavailable} disabled={busy}>Bỏ theo dõi phiên không truy cập được</button>}
  </section>
}
