import { useState } from 'react'
import { SemanticSearchPanel } from './SemanticSearchPanel.jsx'
import { CourseChatPanel } from './CourseChatPanel.jsx'
import { SourceViewer } from './SourceViewer.jsx'

export function CourseAiPanel({ courseId, token }) {
  const [mode, setMode] = useState('search')
  const [selection, setSelection] = useState(null)
  return <div className="course-ai-panel" tabIndex={-1}>
    <div className="ai-mode-switch" role="group" aria-label="Chế độ trợ lý AI">
      <button type="button" className={mode === 'search' ? 'active' : ''} aria-pressed={mode === 'search'} onClick={() => setMode('search')}>Tìm trong tài liệu</button>
      <button type="button" className={mode === 'chat' ? 'active' : ''} aria-pressed={mode === 'chat'} onClick={() => setMode('chat')}>Hỏi tài liệu</button>
    </div>
    {mode === 'search' ? <SemanticSearchPanel courseId={courseId} token={token} onOpenSource={setSelection} /> : <CourseChatPanel courseId={courseId} token={token} onOpenSource={setSelection} />}
    {selection && <SourceViewer key={`${selection.courseId}-${selection.documentId}-${selection.chunkId}`} selection={selection} token={token} onClose={() => setSelection(null)} />}
  </div>
}
