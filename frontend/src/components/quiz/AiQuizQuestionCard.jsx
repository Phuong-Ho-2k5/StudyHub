import { CitationList } from '../ai/CitationList'

export function AiQuizQuestionCard({ entry, index, concepts, courseId, status, disabled, onEdit, onDelete, onReview, onUnreview, onOpenSource }) {
  const { question, reviewedAt } = entry
  const editable = status === 'DRAFT' || status === 'CONFIRMED'
  return <article className="question-card ai-review-card">
    <div className="question-card-heading"><span className="question-number">{index + 1}</span><h3>{question.text}</h3>
      {editable && <div className="course-actions"><button type="button" disabled={disabled} onClick={onEdit}>Sửa</button>
        <button type="button" disabled={disabled} onClick={onDelete}>Xóa</button></div>}
    </div>
    <div className="question-meta"><span className={`status-badge ${reviewedAt ? 'review-approved' : ''}`}>{reviewedAt ? 'Đã duyệt' : 'Chưa duyệt'}</span>
      <span>{concepts.find(c => c.id === question.conceptId)?.name ?? 'Không gắn khái niệm'}</span>
      {reviewedAt && <time dateTime={reviewedAt}>{new Date(reviewedAt).toLocaleString('vi-VN')}</time>}
    </div>
    <ul className="question-option-summary">{question.options.map(option => <li key={option.id} className={option.correct ? 'is-correct' : ''}>
      <span>{option.text}</span>{option.correct && <span className="correct-label">Đáp án đúng</span>}</li>)}</ul>
    {entry.editedSinceGeneration && <p className="inline-help">Đã chỉnh sửa so với bản AI gốc</p>}
    {entry.evidenceOrigin === 'NONE' ? <p className="inline-help">Không có nguồn AI gốc cho câu này.</p> : <>
      <p className="inline-help">Nguồn của bản AI sinh ban đầu; không chứng thực nội dung đã chỉnh sửa.</p>
      <CitationList citations={entry.citations} courseId={courseId} onOpenSource={onOpenSource} />
    </>}
    {editable && <div className="ai-review-card-actions">{reviewedAt
      ? <button className="secondary-button" type="button" disabled={disabled} onClick={onUnreview}>Hủy duyệt</button>
      : status === 'DRAFT' && <button className="secondary-button" type="button" disabled={disabled} onClick={onReview}>Duyệt câu</button>}</div>}
  </article>
}
