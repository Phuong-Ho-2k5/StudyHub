import { CitationList } from './CitationList.jsx'
import { MessageFeedback } from './MessageFeedback.jsx'

export function ChatMessageList({ messages, courseId, token, onOpenSource }) {
  return <div className="chat-transcript" aria-label="Tin nhắn đã lưu" aria-live="polite">{messages.map(item =>
    <article key={item.id} className={`ai-answer chat-message-${item.role.toLowerCase()}`} data-testid="chat-message">
      <h4>{item.role === 'USER' ? 'Bạn' : item.status === 'INSUFFICIENT_CONTEXT' ? 'Chưa đủ thông tin' : 'Câu trả lời'}</h4>
      <p className="ai-text" data-testid={item.role === 'ASSISTANT' ? 'chat-answer' : undefined}>{item.content}</p>
      {item.createdAt && <time className="inline-help" dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString('vi-VN')}</time>}
      {item.role === 'ASSISTANT' && item.citations.length > 0 && <CitationList courseId={courseId} citations={item.citations} onOpenSource={onOpenSource} />}
      {item.role === 'ASSISTANT' && <MessageFeedback key={`${courseId}:${item.sessionId}:${item.id}:${token}`} messageId={item.id} token={token} initialFeedback={item.feedback} />}
    </article>)}</div>
}
