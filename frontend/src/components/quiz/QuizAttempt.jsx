import { useRef, useState } from 'react'
import { submitQuiz } from '../../api/quizApi.js'
import { buildSubmission, QUESTION_LABELS } from './quizForms.js'
import { quizError } from './quizErrors.js'

export function QuizAttempt({ quiz, questions, token, onSubmitted, onBusyChange }) {
  const [answers, setAnswers] = useState({})
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const pending = useRef(false)
  const resultRef = useRef(null)
  const answered = questions.filter((question) => question.type === 'SHORT_ANSWER'
    ? Boolean(answers[question.id]?.trim()) : Boolean(answers[question.id]?.length)).length

  function changeChoice(question, id) {
    setAnswers((current) => {
      const selected = current[question.id] ?? []
      return { ...current, [question.id]: question.type === 'TRUE_FALSE' ? [id]
        : selected.includes(id) ? selected.filter((value) => value !== id) : [...selected, id] }
    })
    setError('')
  }
  async function submit(event) {
    event.preventDefault()
    if (pending.current || result || quiz.status !== 'PUBLISHED') return
    let payload
    try { payload = buildSubmission(questions, answers) }
    catch (caught) { setError(quizError(caught)); return }
    pending.current = true
    setSubmitting(true)
    onBusyChange(true)
    setError('')
    try {
      const data = await submitQuiz(token, quiz.id, payload)
      setResult(data)
      onSubmitted(data)
      requestAnimationFrame(() => resultRef.current?.focus())
    } catch (caught) { setError(quizError(caught)) }
    finally { pending.current = false; setSubmitting(false); onBusyChange(false) }
  }
  function retry() {
    setResult(null)
    setAnswers({})
    setError('')
  }

  if (quiz.status !== 'PUBLISHED') return <div className="empty-state"><h3>Bài kiểm tra chưa mở</h3>
    <p>{quiz.status === 'ARCHIVED' ? 'Chuyển trạng thái sang Đã xuất bản để làm bài.' : 'Thêm câu hỏi và xuất bản bài kiểm tra trước khi làm bài.'}</p></div>
  if (!questions.length) return <div className="empty-state"><h3>Bài kiểm tra chưa có câu hỏi</h3><p>Thêm câu hỏi trong mục Soạn câu hỏi.</p></div>

  return <div>
    {result ? <section className="quiz-result" ref={resultRef} tabIndex={-1} aria-label="Kết quả bài kiểm tra">
      <p className="section-kicker">Đã lưu kết quả lần nộp</p>
      <div className="quiz-result-heading"><div><h2>{Number(result.questionPercentage).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%</h2>
        <p>Tỷ lệ câu đúng trên tổng số câu hỏi</p></div>
        <button className="secondary-button" type="button" onClick={retry}>Làm lại</button>
      </div>
      <dl className="quiz-result-stats"><div><dt>Đúng</dt><dd>{result.correctCount}</dd></div>
        <div><dt>Sai</dt><dd>{result.incorrectCount}</dd></div><div><dt>Chờ chấm</dt><dd>{result.pendingCount}</dd></div>
        <div><dt>Tổng số câu</dt><dd>{result.totalQuestions}</dd></div></dl>
      {result.pendingCount > 0 && <p className="inline-help">Câu tự luận ngắn đang chờ chấm; chưa được tính là câu trả lời đúng.</p>}
    </section> : <div className="quiz-progress"><span>Đã trả lời {answered} / {questions.length} câu</span>
      <progress max={questions.length} value={answered} aria-label="Tiến độ trả lời" /></div>}
    <form className="quiz-attempt-form" onSubmit={submit}>
      {questions.map((question, index) => {
        const outcome = result?.questionResults.find((item) => item.questionId === question.id)
        const state = outcome ? outcome.isCorrect === null ? 'pending' : outcome.isCorrect ? 'correct' : 'incorrect' : ''
        return <fieldset className={`attempt-question${state ? ` answer-${state}` : ''}`} key={question.id} disabled={submitting || Boolean(result)}>
          <legend><span className="question-number">{index + 1}</span>{question.text}</legend>
          <div className="question-meta"><span>{QUESTION_LABELS[question.type]}</span>
            {state && <span className={`answer-badge answer-${state}`}>{state === 'pending' ? 'Chờ chấm' : state === 'correct' ? 'Đúng' : 'Sai'}</span>}
          </div>
          {question.type === 'SHORT_ANSWER' ? <div className="field"><label className="field-label" htmlFor={`answer-text-${question.id}`}>Câu trả lời của bạn</label>
            <textarea id={`answer-text-${question.id}`} rows={4} maxLength={4000} value={answers[question.id] ?? ''}
              onChange={(event) => { setAnswers((current) => ({ ...current, [question.id]: event.target.value })); setError('') }} />
          </div> : <>
            {question.type === 'MULTIPLE_CHOICE' && <p className="inline-help">Chọn tất cả đáp án bạn cho là đúng.</p>}
            <div className="answer-options">{question.options.map((option) => <label className={`answer-option${result && option.correct ? ' revealed-correct' : ''}`} key={option.id}>
              <input type={question.type === 'TRUE_FALSE' ? 'radio' : 'checkbox'} name={`answer-${question.id}`}
                checked={(answers[question.id] ?? []).includes(option.id)} onChange={() => changeChoice(question, option.id)} />
              <span>{option.text}</span>{result && option.correct && <span className="correct-label">Đáp án đúng</span>}
            </label>)}</div>
          </>}
          {result && question.type === 'SHORT_ANSWER' && <p className="reference-answer"><strong>Đáp án tham chiếu:</strong> {question.referenceAnswer}</p>}
        </fieldset>
      })}
      {error && <p className="form-message error" role="alert">{error}</p>}
      {!result && <div className="quiz-submit-bar"><p className="inline-help">Trả lời toàn bộ câu hỏi trước khi nộp. Mỗi lần nộp thành công được lưu riêng.</p>
        <button className="primary-button detail-add" type="submit" disabled={submitting}>{submitting ? 'Đang nộp bài…' : 'Nộp bài'}</button>
      </div>}
    </form>
  </div>
}
