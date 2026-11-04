import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getQuizReview, reviewQuizQuestion, unreviewQuizQuestion, confirmAiQuiz, publishAiQuiz, archiveAiQuiz } from '../../api/quizReviewApi.js'
import { deleteQuestion, deleteQuiz } from '../../api/quizApi.js'
import { listConcepts } from '../../api/studyApi.js'
import { ApiError } from '../../api/authApi.js'
import { useLearning } from '../../context/LearningContext'
import { SourceViewer } from '../ai/SourceViewer'
import { AiQuizQuestionCard } from './AiQuizQuestionCard'
import { QuizEditorDialog } from './QuizEditorDialog'
import { QuestionEditorDialog } from './QuestionEditorDialog'
import { QuizAttempt } from './QuizAttempt'
import { QUIZ_LABELS } from './quizForms.js'
import { quizError } from './quizErrors.js'

export function AiQuizReviewPanel({ quizId, courseId, token, concepts, onQuizChanged }) {
  const navigate = useNavigate()
  const { invalidateLearning } = useLearning()
  const [review, setReview] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [reloadRequired, setReloadRequired] = useState(false)
  const [dialog, setDialog] = useState(null)
  const [selection, setSelection] = useState(null)
  const [mode, setMode] = useState('review')
  const [submitting, setSubmitting] = useState(false)
  const [confidence, setConfidence] = useState(null)
  const alive = useRef(false)
  const epoch = useRef(0)
  const pending = useRef(false)

  const accept = useCallback(data => {
    if (String(data.quiz?.id) !== String(quizId) || String(data.quiz?.courseId) !== String(courseId) || data.quiz?.sourceType !== 'AI_GENERATED'
      || !Number.isSafeInteger(data.contentRevision) || data.contentRevision < 0 || !Array.isArray(data.questions)) {
      throw new ApiError('Dữ liệu duyệt không khớp bài kiểm tra đang mở.', 404)
    }
    setReview(data); onQuizChanged(data.quiz); setReloadRequired(false); setError('')
    if (data.quiz.status !== 'PUBLISHED') setMode('review')
  }, [quizId, courseId, onQuizChanged])

  const handleError = useCallback(caught => {
    if (!alive.current) return
    setError(quizError(caught))
    if ([401, 404].includes(caught.status)) { setReview(null); setDialog(null); setSelection(null) }
    if (caught.status !== 400) setReloadRequired(true)
  }, [])

  const load = useCallback(async () => {
    const requestEpoch = ++epoch.current
    setLoading(true)
    try {
      const data = await getQuizReview(token, quizId)
      if (alive.current && requestEpoch === epoch.current) accept(data)
    } catch (caught) {
      if (alive.current && requestEpoch === epoch.current) { handleError(caught); setReloadRequired(true) }
    } finally { if (alive.current && requestEpoch === epoch.current) setLoading(false) }
  }, [token, quizId, accept, handleError])

  useEffect(() => {
    alive.current = true
    const requestEpoch = ++epoch.current
    getQuizReview(token, quizId)
      .then(data => { if (alive.current && requestEpoch === epoch.current) accept(data) })
      .catch(caught => { if (alive.current && requestEpoch === epoch.current) { handleError(caught); setReloadRequired(true) } })
      .finally(() => { if (alive.current && requestEpoch === epoch.current) setLoading(false) })
    return () => { alive.current = false }
  }, [token, quizId, accept, handleError])

  const disabled = busy || submitting || loading || reloadRequired || Boolean(dialog)
  const editable = review && ['DRAFT', 'CONFIRMED'].includes(review.quiz.status)
  const published = review?.quiz.status === 'PUBLISHED'

  async function run(action, refresh = false) {
    if (pending.current || disabled) return
    pending.current = true; setBusy(true); setError('')
    try {
      const result = await action()
      if (!alive.current) return
      if (refresh) await load(); else accept(result)
    } catch (caught) { handleError(caught) }
    finally { pending.current = false; if (alive.current) setBusy(false) }
  }
  function allowEdit() {
    return !disabled && (review.quiz.status !== 'CONFIRMED' || window.confirm('Chỉnh sửa hoặc hủy duyệt sẽ đưa Quiz về bản nháp và cần xác nhận lại. Tiếp tục?'))
  }
  function edit(type, item) {
    if (allowEdit()) setDialog({ type, item, expectedRevision: review.contentRevision })
  }
  async function saved() {
    if (!alive.current) return
    setDialog(null)
    // The write already succeeded. A failed read must never invite resending it.
    setReloadRequired(true)
    await load()
  }
  function reload() {
    if (dialog && !window.confirm('Tải lại sẽ đóng biểu mẫu và bỏ phần chưa lưu. Bạn đã sao chép nội dung cần giữ?')) return
    setDialog(null); setSelection(null); load()
  }
  async function removeQuiz() {
    if (pending.current || disabled || !window.confirm('Xóa Quiz cùng câu hỏi và lịch sử nộp bài?')) return
    pending.current = true; setBusy(true)
    try {
      await deleteQuiz(token, quizId)
      if (alive.current) navigate(`/courses/${courseId}?tab=quizzes`)
    } catch (caught) { handleError(caught) }
    finally { pending.current = false; if (alive.current) setBusy(false) }
  }
  async function refreshConfidence() {
    setConfidence({ loading: true })
    try {
      const values = await listConcepts(token, courseId)
      if (alive.current) setConfidence({ values })
    } catch (caught) { if (alive.current) setConfidence({ error: quizError(caught) }) }
  }

  return <section className="ai-quiz-review" aria-label="Duyệt Quiz AI" aria-busy={loading || busy}>
    {loading && <p className="list-state" role="status">Đang tải nội dung duyệt…</p>}
    {error && <div className="form-message error" role="alert"><p>{error}</p>
      {reloadRequired && <p>Nội dung hoặc trạng thái có thể đã thay đổi. Tải lại và kiểm tra trước khi thao tác tiếp.</p>}</div>}
    {reloadRequired && !dialog && <button className="secondary-button" type="button" disabled={loading || busy} onClick={reload}>Tải lại nội dung</button>}
    {review && <>
      <div className="page-heading"><div><p className="section-kicker">Quiz AI · Duyệt trước khi xuất bản</p><h1>{review.quiz.title}</h1>
        <p><span className={`status-badge quiz-status-${review.quiz.status.toLowerCase()}`}>{QUIZ_LABELS[review.quiz.status]}</span></p>
        <p className="ai-review-count" aria-live="polite">{review.reviewedQuestions} / {review.totalQuestions} câu đã duyệt</p>
      </div><div className="quiz-heading-actions">
        {editable && <button className="secondary-button" type="button" disabled={disabled} onClick={() => edit('quiz', review.quiz)}>Sửa Quiz</button>}
        {review.quiz.status !== 'ARCHIVED' && <button className="secondary-button" type="button" disabled={disabled} onClick={() => {
          if (window.confirm('Lưu trữ Quiz? Quiz sẽ khóa nội dung và không thể mở lại.')) run(() => archiveAiQuiz(token, quizId, review.contentRevision))
        }}>Lưu trữ Quiz</button>}
        <button className="danger-link" type="button" disabled={disabled} onClick={removeQuiz}>Xóa Quiz</button>
      </div></div>
      {review.quiz.status === 'CONFIRMED' && <p className="ai-review-notice" role="status">Đã xác nhận, chưa xuất bản.</p>}
      {review.confirmedAt && <p className="inline-help">Xác nhận: <time dateTime={review.confirmedAt}>{new Date(review.confirmedAt).toLocaleString('vi-VN')}</time></p>}
      {review.publishedAt && <p className="inline-help">Xuất bản: <time dateTime={review.publishedAt}>{new Date(review.publishedAt).toLocaleString('vi-VN')}</time></p>}
      {review.sourceCurrent === false && <p className="ai-review-notice">Tài liệu nguồn đã thay đổi hoặc bị xóa. Hãy đối chiếu nguồn gốc trước khi duyệt; trích dẫn không chứng thực nội dung đã sửa.</p>}
      {review.sourceCurrent === null && <p className="ai-review-notice">Không có thông tin nguồn AI gốc để đối chiếu.</p>}
      <div className="course-panel">
        <div className="detail-toolbar"><div className="detail-tabs" role="tablist" aria-label="Chế độ Quiz AI">
          <button id="ai-review-tab" role="tab" type="button" aria-selected={mode === 'review'} aria-controls="ai-review-content" className={mode === 'review' ? 'active' : ''} disabled={busy || submitting} onClick={() => setMode('review')}>Duyệt câu hỏi</button>
          {published && <button id="ai-attempt-tab" role="tab" type="button" aria-selected={mode === 'attempt'} aria-controls="ai-attempt-content" className={mode === 'attempt' ? 'active' : ''} disabled={disabled} onClick={() => setMode('attempt')}>Làm bài</button>}
        </div>{editable && <button className="primary-button detail-add" type="button" disabled={disabled} onClick={() => edit('question', null)}>+ Thêm câu hỏi</button>}</div>
        <section id="ai-review-content" role="tabpanel" aria-labelledby="ai-review-tab" hidden={mode !== 'review'}>
          {review.questions.length ? <div className="resource-list">{review.questions.map((entry, index) => <AiQuizQuestionCard key={entry.question.id}
            entry={entry} index={index} concepts={concepts} courseId={courseId} status={review.quiz.status} disabled={disabled}
            onOpenSource={setSelection} onEdit={() => edit('question', entry.question)}
            onDelete={() => { if (allowEdit() && window.confirm(`Xóa câu hỏi “${entry.question.text}”?`)) run(() => deleteQuestion(token, entry.question.id, review.contentRevision), true) }}
            onReview={() => run(() => reviewQuizQuestion(token, quizId, entry.question.id, review.contentRevision))}
            onUnreview={() => { if (allowEdit()) run(() => unreviewQuizQuestion(token, quizId, entry.question.id, review.contentRevision)) }} />)}</div>
            : <div className="empty-state"><h3>Chưa có câu hỏi</h3><p>Thêm câu hỏi và duyệt từng câu trước khi xác nhận.</p></div>}
          {review.quiz.status === 'DRAFT' && <div className="ai-review-workflow"><p className="inline-help">Duyệt tất cả câu hỏi trước khi xác nhận. Xác nhận chưa mở bài kiểm tra.</p>
            <button className="primary-button" type="button" disabled={disabled || !review.canConfirm} onClick={() => run(() => confirmAiQuiz(token, quizId, review.contentRevision))}>Xác nhận Quiz</button></div>}
          {review.quiz.status === 'CONFIRMED' && <div className="ai-review-workflow"><p className="inline-help">Xuất bản để mở bài kiểm tra. Nội dung sẽ được khóa sau khi xuất bản.</p>
            <button className="primary-button" type="button" disabled={disabled || !review.canPublish} onClick={() => {
              if (window.confirm('Xuất bản Quiz và khóa nội dung đã duyệt?')) run(() => publishAiQuiz(token, quizId, review.contentRevision))
            }}>Xuất bản Quiz</button></div>}
        </section>
        {published && <section id="ai-attempt-content" role="tabpanel" aria-labelledby="ai-attempt-tab" hidden={mode !== 'attempt'}>
          {!reloadRequired && <QuizAttempt key={review.contentRevision} quiz={review.quiz} questions={review.questions.map(entry => entry.question)} token={token}
            onBusyChange={setSubmitting} onSubmitted={() => { invalidateLearning(); refreshConfidence() }} />}
          {confidence && <section className="quiz-confidence" aria-label="Mức độ hiểu sau khi nộp bài"><h3>Mức độ hiểu sau lần nộp</h3>
            {confidence.loading ? <p role="status">Đang tải mức độ hiểu mới…</p> : confidence.error ? <><p role="alert">Kết quả đã lưu, nhưng chưa tải được mức độ hiểu. {confidence.error}</p>
              <button className="secondary-button" type="button" onClick={refreshConfidence}>Tải lại mức độ hiểu</button></>
              : <ul>{confidence.values.filter(c => review.questions.some(e => e.question.conceptId === c.id)).map(c => <li key={c.id}>{c.name}: {c.confidence}%</li>)}</ul>}
          </section>}
        </section>}
      </div>
    </>}
    {dialog?.type === 'quiz' && <QuizEditorDialog item={dialog.item} courseId={courseId} token={token} aiGenerated expectedRevision={dialog.expectedRevision}
      onClose={() => setDialog(null)} onSaved={saved} onConflict={handleError} reloadRequired={reloadRequired} onReload={reload} />}
    {dialog?.type === 'question' && <QuestionEditorDialog item={dialog.item} quizId={quizId} concepts={concepts} token={token} aiGenerated expectedRevision={dialog.expectedRevision}
      onClose={() => setDialog(null)} onSaved={saved} onConflict={handleError} reloadRequired={reloadRequired} onReload={reload} />}
    {selection && <SourceViewer key={`${selection.documentId}-${selection.chunkId}`} selection={selection} token={token} onClose={() => setSelection(null)} />}
  </section>
}
