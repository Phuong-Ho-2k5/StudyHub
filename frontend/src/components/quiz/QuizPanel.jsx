import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteQuiz, listQuizzes } from '../../api/quizApi.js'
import { QuizEditorDialog } from './QuizEditorDialog'
import { QUIZ_LABELS } from './quizForms.js'
import { quizError } from './quizErrors.js'

export function QuizPanel({ courseId, token }) {
  const [quizzes, setQuizzes] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [editor, setEditor] = useState(null)
  const [deleting, setDeleting] = useState(null)

  useEffect(() => {
    let active = true
    listQuizzes(token, courseId).then((data) => {
      if (active) { setQuizzes(data); setError('') }
    }).catch((caught) => { if (active) setError(quizError(caught)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [courseId, token, revision])

  function saved(quiz) {
    setQuizzes((current) => editor.item ? current.map((item) => item.id === quiz.id ? quiz : item) : [...current, quiz])
    setEditor(null)
    setError('')
  }
  async function remove(quiz) {
    if (deleting !== null || !window.confirm(`Xóa bài kiểm tra “${quiz.title}” cùng câu hỏi và lịch sử nộp bài?`)) return
    setDeleting(quiz.id)
    setError('')
    try {
      await deleteQuiz(token, quiz.id)
      setQuizzes((current) => current.filter((item) => item.id !== quiz.id))
    } catch (caught) { setError(quizError(caught)) }
    finally { setDeleting(null) }
  }

  return <section aria-label="Bài kiểm tra của khóa học">
    <div className="quiz-section-heading"><div><h2>Bài kiểm tra</h2>
      <p className="inline-help">Soạn câu hỏi, làm bài và cập nhật mức độ hiểu của bạn.</p></div>
      <button className="primary-button detail-add" type="button" onClick={() => setEditor({ item: null })} disabled={loading || Boolean(error)}>+ Tạo Quiz</button>
    </div>
    {loading && <p className="list-state" role="status">Đang tải bài kiểm tra…</p>}
    {error && <div className="form-message error" role="alert"><p>{error}</p>
      <button className="secondary-button" type="button" onClick={() => { setLoading(true); setRevision((value) => value + 1) }}>Thử lại</button>
    </div>}
    {!loading && !error && (quizzes.length ? <div className="resource-list">{quizzes.map((quiz) => <article className="resource-card quiz-card" key={quiz.id}>
      <div className="resource-copy"><h3><Link className="quiz-title-link" to={`/courses/${courseId}/quizzes/${quiz.id}`}>{quiz.title}</Link></h3>
        <p>{quiz.status === 'PUBLISHED' ? 'Sẵn sàng để làm bài' : quiz.status === 'DRAFT' ? 'Thêm câu hỏi và xuất bản khi đã sẵn sàng' : 'Đã lưu trữ'}</p>
      </div>
      <span className={`status-badge quiz-status-${quiz.status.toLowerCase()}`}>{QUIZ_LABELS[quiz.status] ?? quiz.status}</span>
      <div className="course-actions"><Link className="text-link" to={`/courses/${courseId}/quizzes/${quiz.id}`}>Mở Quiz</Link>
        <button type="button" disabled={deleting !== null} onClick={() => setEditor({ item: quiz })}>Sửa</button>
        <button type="button" disabled={deleting !== null} onClick={() => remove(quiz)}>{deleting === quiz.id ? 'Đang xóa…' : 'Xóa'}</button>
      </div>
    </article>)}</div> : <div className="empty-state"><h3>Chưa có bài kiểm tra</h3>
      <p>Tạo một Quiz để bắt đầu ôn tập kiến thức của khóa học.</p></div>)}
    {editor && <QuizEditorDialog key={editor.item?.id ?? 'new'} item={editor.item} courseId={courseId} token={token}
      onClose={() => setEditor(null)} onSaved={saved} />}
  </section>
}
