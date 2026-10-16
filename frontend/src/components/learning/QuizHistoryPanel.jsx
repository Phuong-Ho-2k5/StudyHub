import { useState } from 'react'
import { getQuizHistory } from '../../api/learningApi'
import { formatDate, formatPercentage } from '../../api/learningDisplay'
import { useAuth } from '../../auth/AuthContext'
import { useLearning } from '../../context/LearningContext'
import { useRemoteData } from '../../hooks/useRemoteData'
import { Pagination } from '../Pagination'
import { RemoteState } from '../RemoteState'

export function QuizHistoryPanel() {
  const { token } = useAuth()
  const { learningRevision } = useLearning()
  const [page, setPage] = useState(0)
  const state = useRemoteData(() => getQuizHistory(token, page), [token, page, learningRevision])
  return <section className="course-panel learning-section">
    <div className="section-heading"><div><p className="section-kicker">Nhìn lại kết quả</p><h2>Lịch sử làm Quiz</h2></div><button className="secondary-button" type="button" onClick={state.reload} disabled={state.loading}>Làm mới</button></div>
    <RemoteState {...state} onRetry={state.reload} empty={state.data?.content.length === 0 && 'Bạn chưa có lần nộp Quiz nào.'}>
      <div className="table-scroll"><table className="data-table"><thead><tr><th>Lần nộp</th><th>Quiz</th><th>Thời điểm</th><th>Tổng câu</th><th>Đúng</th><th>Sai</th><th>Chờ chấm</th><th>Kết quả</th></tr></thead><tbody>
        {state.data?.content.map((item) => <tr key={item.attemptId}><td>#{item.attemptId}</td><td>#{item.quizId}</td><td>{formatDate(item.createdAt)}</td><td>{item.totalQuestions}</td><td>{item.correctCount}</td><td>{item.incorrectCount}</td><td>{item.pendingCount}</td><td><strong>{formatPercentage(item.questionPercentage)}</strong></td></tr>)}
      </tbody></table></div>
    </RemoteState>
    <Pagination page={page} totalPages={state.data?.totalPages ?? 0} busy={state.loading} onChange={setPage} />
  </section>
}
