import { getStatistics } from '../../api/learningApi'
import { formatPercentage, formatStudyTime } from '../../api/learningDisplay'
import { useAuth } from '../../auth/AuthContext'
import { useLearning } from '../../context/LearningContext'
import { useRemoteData } from '../../hooks/useRemoteData'
import { RemoteState } from '../RemoteState'

export function StatisticsPanel() {
  const { token } = useAuth()
  const { learningRevision } = useLearning()
  const state = useRemoteData(() => getStatistics(token), [token, learningRevision])
  const data = state.data
  return <section className="learning-section" aria-label="Thống kê học tập">
    <div className="section-heading"><div><p className="section-kicker">Hành trình của bạn</p><h2>Tổng quan học tập</h2></div><button className="secondary-button" type="button" onClick={state.reload} disabled={state.loading}>Làm mới</button></div>
    <RemoteState {...state} onRetry={state.reload}>{data && <div className="stat-grid">
      {[['Thời gian học', formatStudyTime(data.totalStudySeconds)], ['Phiên đã hoàn tất', data.completedStudySessions], ['Lượt làm Quiz', data.quizAttemptCount], ['Điểm trung bình', formatPercentage(data.averageQuizScore)]].map(([label, value]) => <article className="stat-card" key={label}><span>{label}</span><strong>{value}</strong></article>)}
    </div>}</RemoteState>
  </section>
}
