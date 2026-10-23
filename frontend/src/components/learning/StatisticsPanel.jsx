import { getStatistics } from '../../api/learningApi'
import { formatPercentage, formatStudyTime } from '../../api/learningDisplay'
import { useAuth } from '../../auth/AuthContext'
import { useLearning } from '../../context/LearningContext'
import { useRemoteData } from '../../hooks/useRemoteData'
import { RemoteState } from '../RemoteState'
import { Icon } from '../Icon'

export function StatisticsPanel() {
  const { token } = useAuth()
  const { learningRevision } = useLearning()
  const state = useRemoteData(() => getStatistics(token), [token, learningRevision])
  const data = state.data
  return <section className="learning-section" aria-label="Thống kê học tập">
    <div className="section-heading statistics-heading"><h2>Nhịp học của bạn</h2><button className="quiet-button" type="button" onClick={state.reload} disabled={state.loading}>Làm mới thống kê</button></div>
    <RemoteState {...state} onRetry={state.reload}>{data && <div className="stat-grid">
      {[['Thời gian đã học', formatStudyTime(data.totalStudySeconds), 'clock', 'Tích lũy qua các phiên đã kết thúc'], ['Phiên đã hoàn tất', data.completedStudySessions, 'book', 'Mỗi phiên học, một bước tiến'], ['Lượt kiểm tra đã nộp', data.quizAttemptCount, 'check', 'Nhìn lại những điều đã học'], ['Điểm trung bình', formatPercentage(data.averageQuizScore), 'chart', 'Từ các lượt kiểm tra đã lưu']].map(([label, value, icon, caption]) => <article className="stat-card" key={label}><div className="stat-label"><span>{label}</span><Icon name={icon} size={18} /></div><strong>{value}</strong><span className="stat-caption">{caption}</span></article>)}
    </div>}</RemoteState>
  </section>
}
