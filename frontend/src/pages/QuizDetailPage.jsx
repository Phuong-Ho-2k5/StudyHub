import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getCourse, listConcepts } from '../api/studyApi.js'
import { deleteQuestion, deleteQuiz, getQuiz, listQuestions, updateQuiz } from '../api/quizApi.js'
import { useAuth } from '../auth/AuthContext'
import { QuizAttempt } from '../components/quiz/QuizAttempt'
import { QuizEditorDialog } from '../components/quiz/QuizEditorDialog'
import { QuestionEditorDialog } from '../components/quiz/QuestionEditorDialog'
import { QUESTION_LABELS, QUIZ_LABELS } from '../components/quiz/quizForms.js'
import { quizError } from '../components/quiz/quizErrors.js'

function QuizDetail({ courseId, quizId, token }) {
  const navigate = useNavigate()
  const [quiz, setQuiz] = useState(null)
  const [course, setCourse] = useState(null)
  const [questions, setQuestions] = useState([])
  const [concepts, setConcepts] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [questionRevision, setQuestionRevision] = useState(0)
  const [mode, setMode] = useState('manage')
  const [dialog, setDialog] = useState(null)
  const [busy, setBusy] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [confidenceState, setConfidenceState] = useState(null)
  const back = `/courses/${courseId}?tab=quizzes`

  useEffect(() => {
    let active = true
    Promise.all([getQuiz(token, quizId), getCourse(token, courseId), listQuestions(token, quizId), listConcepts(token, courseId)])
      .then(([quizData, courseData, questionData, conceptData]) => {
        if (!active) return
        if (String(quizData.courseId) !== String(courseId)) {
          setLoadError('Bài kiểm tra không thuộc khóa học này.')
          return
        }
        setQuiz(quizData); setCourse(courseData); setQuestions(questionData); setConcepts(conceptData); setLoadError('')
      }).catch((caught) => { if (active) setLoadError(quizError(caught)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [courseId, quizId, token, revision])

  function questionSaved(saved) {
    setQuestions((current) => dialog.item ? current.map((item) => item.id === saved.id ? saved : item) : [...current, saved])
    setQuestionRevision((value) => value + 1)
    setConfidenceState(null)
    setDialog(null)
    setError('')
  }
  async function removeQuestion(question) {
    if (busy || submitting || !window.confirm(`Xóa câu hỏi “${question.text}”?`)) return
    setBusy(true); setError('')
    try {
      await deleteQuestion(token, question.id)
      setQuestions((current) => current.filter((item) => item.id !== question.id))
      setQuestionRevision((value) => value + 1)
      setConfidenceState(null)
    } catch (caught) { setError(quizError(caught)) }
    finally { setBusy(false) }
  }
  async function changeStatus(status) {
    if (busy || submitting) return
    setBusy(true); setError('')
    try { setQuiz(await updateQuiz(token, quiz.id, { title: quiz.title, status })) }
    catch (caught) { setError(quizError(caught)) }
    finally { setBusy(false) }
  }
  async function removeQuiz() {
    if (busy || submitting || !window.confirm(`Xóa bài kiểm tra “${quiz.title}” cùng câu hỏi và lịch sử nộp bài?`)) return
    setBusy(true); setError('')
    try { await deleteQuiz(token, quiz.id); navigate(back) }
    catch (caught) { setError(quizError(caught)) }
    finally { setBusy(false) }
  }
  async function refreshConfidence() {
    setConfidenceState({ loading: true })
    try {
      const data = await listConcepts(token, courseId)
      setConcepts(data)
      setConfidenceState({ loading: false })
    } catch (caught) { setConfidenceState({ loading: false, error: quizError(caught) }) }
  }
  const linkedConcepts = concepts.filter((concept) => questions.some((question) => question.conceptId === concept.id))
  const disabled = busy || submitting

  return <main className="dashboard-page">
    <header className="dashboard-header"><Link className="brand" to="/welcome"><span className="brand-mark" aria-hidden="true">S</span><span>StudyHub</span></Link>
      <Link className="text-link" to={back}>← Khóa học</Link>
    </header>
    <div className="detail-shell quiz-detail-shell">
      {loading ? <p className="list-state" role="status">Đang tải bài kiểm tra…</p> : loadError ? <div className="form-message error" role="alert"><p>{loadError}</p>
        <button className="secondary-button" type="button" onClick={() => { setLoading(true); setRevision((value) => value + 1) }}>Thử lại</button>
      </div> : quiz && <>
        <div className="page-heading"><div><p className="section-kicker">{course.name} · Bài kiểm tra</p><h1>{quiz.title}</h1>
          <p><span className={`status-badge quiz-status-${quiz.status.toLowerCase()}`}>{QUIZ_LABELS[quiz.status]}</span> · {questions.length} câu hỏi</p>
        </div><div className="quiz-heading-actions"><button className="secondary-button" type="button" disabled={disabled} onClick={() => setDialog({ type: 'quiz', item: quiz })}>Sửa Quiz</button>
          {quiz.status !== 'PUBLISHED' && <button className="primary-button detail-add" type="button" disabled={disabled || !questions.length} onClick={() => changeStatus('PUBLISHED')}>Xuất bản</button>}
          <button className="danger-link" type="button" disabled={disabled} onClick={removeQuiz}>Xóa Quiz</button>
        </div></div>
        {error && <p className="form-message error" role="alert">{error}</p>}
        <div className="course-panel">
          <div className="detail-toolbar"><div className="detail-tabs" role="tablist" aria-label="Chế độ bài kiểm tra">
            <button id="quiz-manage-tab" type="button" role="tab" aria-controls="quiz-manage-panel" aria-selected={mode === 'manage'} className={mode === 'manage' ? 'active' : ''} disabled={disabled} onClick={() => setMode('manage')}>Soạn câu hỏi</button>
            <button id="quiz-attempt-tab" type="button" role="tab" aria-controls="quiz-attempt-panel" aria-selected={mode === 'attempt'} className={mode === 'attempt' ? 'active' : ''} disabled={disabled} onClick={() => setMode('attempt')}>Làm bài</button>
          </div>{mode === 'manage' && <button className="primary-button detail-add" type="button" disabled={disabled} onClick={() => setDialog({ type: 'question', item: null })}>+ Thêm câu hỏi</button>}</div>
          <section id="quiz-manage-panel" role="tabpanel" aria-labelledby="quiz-manage-tab" hidden={mode !== 'manage'}>
            {questions.length ? <div className="resource-list">{questions.map((question, index) => <article className="question-card" key={question.id}>
              <div className="question-card-heading"><span className="question-number">{index + 1}</span><h3>{question.text}</h3>
                <div className="course-actions"><button type="button" disabled={disabled} onClick={() => setDialog({ type: 'question', item: question })}>Sửa</button>
                  <button type="button" disabled={disabled} onClick={() => removeQuestion(question)}>Xóa</button></div>
              </div><div className="question-meta"><span className="status-badge">{QUESTION_LABELS[question.type]}</span>
                <span>{concepts.find((concept) => concept.id === question.conceptId)?.name ?? 'Không gắn khái niệm'}</span></div>
              {question.type === 'SHORT_ANSWER' ? <p className="reference-answer"><strong>Đáp án tham chiếu:</strong> {question.referenceAnswer}</p>
                : <ul className="question-option-summary">{question.options.map((option) => <li key={option.id} className={option.correct ? 'is-correct' : ''}>
                  <span>{option.text}</span>{option.correct && <span className="correct-label">Đáp án đúng</span>}</li>)}</ul>}
            </article>)}</div> : <div className="empty-state"><h3>Chưa có câu hỏi</h3><p>Thêm câu hỏi trắc nghiệm, đúng/sai hoặc tự luận ngắn.</p></div>}
          </section>
          <section id="quiz-attempt-panel" role="tabpanel" aria-labelledby="quiz-attempt-tab" hidden={mode !== 'attempt'}>
            <QuizAttempt key={questionRevision} quiz={quiz} questions={questions} token={token} onSubmitted={refreshConfidence} onBusyChange={setSubmitting} />
            {confidenceState && <section className="quiz-confidence" aria-label="Mức độ hiểu sau khi nộp bài">
              <h3>Mức độ hiểu sau lần nộp</h3>
              {confidenceState.loading ? <p className="inline-help" role="status">Đang tải mức độ hiểu mới…</p> : confidenceState.error ? <>
                <p className="form-message error" role="alert">Kết quả bài nộp đã lưu, nhưng chưa tải được mức độ hiểu mới. {confidenceState.error}</p>
                <button className="secondary-button" type="button" onClick={refreshConfidence}>Tải lại mức độ hiểu</button>
              </> : linkedConcepts.length ? <ul>{linkedConcepts.map((concept) => <li key={concept.id}><span>{concept.name}</span><strong>{concept.confidence}%</strong></li>)}</ul>
                : <p className="inline-help">Các câu hỏi chưa gắn khái niệm nên mức độ hiểu không thay đổi.</p>}
            </section>}
          </section>
        </div>
      </>}
    </div>
    {dialog?.type === 'quiz' && <QuizEditorDialog item={dialog.item} courseId={courseId} token={token} onClose={() => setDialog(null)}
      onSaved={(saved) => { setQuiz(saved); setDialog(null); setError('') }} />}
    {dialog?.type === 'question' && <QuestionEditorDialog key={dialog.item?.id ?? 'new'} item={dialog.item} quizId={quizId}
      concepts={concepts} token={token} onClose={() => setDialog(null)} onSaved={questionSaved} />}
  </main>
}

export function QuizDetailPage() {
  const { courseId, quizId } = useParams()
  const { token } = useAuth()
  return <QuizDetail key={`${courseId}-${quizId}`} courseId={courseId} quizId={quizId} token={token} />
}
