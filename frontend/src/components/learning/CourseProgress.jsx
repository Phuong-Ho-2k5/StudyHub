import { getProgress } from '../../api/learningApi'
import { formatPercentage } from '../../api/learningDisplay'
import { useAuth } from '../../auth/AuthContext'
import { useLearning } from '../../context/LearningContext'
import { useRemoteData } from '../../hooks/useRemoteData'
import { RemoteState } from '../RemoteState'

export function CourseProgress({ courseId, revision }) {
  const { token } = useAuth()
  const { learningRevision } = useLearning()
  const state = useRemoteData(() => getProgress(token, courseId), [token, courseId, revision, learningRevision])
  const data = state.data
  return <section className="course-panel learning-section" aria-label="Tiến độ khóa học">
    <div className="section-heading"><h2>Tiến độ khóa học</h2><button className="secondary-button" type="button" onClick={state.reload} disabled={state.loading}>Làm mới</button></div>
    <RemoteState {...state} onRetry={state.reload}>{data && <>
      <div className="progress-summary"><strong>{formatPercentage(data.progressPercentage)}</strong><span>Mức độ hiểu trung bình · {data.totalConcepts} khái niệm</span></div>
      <progress className="course-progress" value={data.progressPercentage} max="100" aria-label="Mức độ hiểu trung bình" />
      <div className="progress-breakdown"><span>Thành thạo: <strong>{data.masteredConcepts}</strong></span><span>Đang phát triển: <strong>{data.developingConcepts}</strong></span><span>Cần ôn tập: <strong>{data.needsReviewConcepts}</strong></span><span>Tỷ lệ nắm vững: <strong>{formatPercentage(data.masteryPercentage)}</strong></span></div>
    </>}</RemoteState>
  </section>
}
