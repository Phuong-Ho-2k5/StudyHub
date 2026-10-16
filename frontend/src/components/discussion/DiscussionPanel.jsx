import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { createPost, deletePost, listPosts, updatePost } from '../../api/discussionApi'
import { errorMessage, formatDate } from '../../api/learningDisplay'
import { useAuth } from '../../auth/AuthContext'
import { useRemoteData } from '../../hooks/useRemoteData'
import { Pagination } from '../Pagination'
import { RemoteState } from '../RemoteState'
import { DiscussionEditor } from './DiscussionEditor'

export function DiscussionPanel({ courseId }) {
  const { token } = useAuth()
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const lock = useRef(false)
  const state = useRemoteData(() => listPosts(token, courseId, page), [token, courseId, page])
  async function save(data) {
    if (lock.current) return
    lock.current = true; setBusy(true); setError('')
    try {
      if (editor.item) await updatePost(token, editor.item.id, data)
      else await createPost(token, courseId, data)
      setEditor(null); setPage(0); state.reload()
    } catch (caught) { setError(errorMessage(caught)) }
    finally { lock.current = false; setBusy(false) }
  }
  async function remove(item) {
    if (lock.current || !window.confirm(`Xóa bài “${item.title}” và các bình luận?`)) return
    lock.current = true; setBusy(true); setError('')
    try {
      await deletePost(token, item.id)
      if (state.data.content.length === 1 && page > 0) setPage(page - 1)
      else state.reload()
    } catch (caught) { setError(errorMessage(caught)) }
    finally { lock.current = false; setBusy(false) }
  }
  return <section className="discussion-panel">
    <div className="section-heading"><div><h2>Thảo luận khóa học</h2><p className="muted">Ghi lại câu hỏi và trao đổi kiến thức trong khóa học.</p></div><button className="primary-button detail-add" type="button" disabled={busy} onClick={() => { setError(''); setEditor({ item: null }) }}>Tạo bài thảo luận</button></div>
    {error && !editor && <p className="form-message error" role="alert">{error}</p>}
    <RemoteState {...state} onRetry={state.reload} empty={state.data?.content.length === 0 && 'Chưa có bài thảo luận. Hãy bắt đầu bằng một câu hỏi.'}>
      <div className="resource-list">{state.data?.content.map((item) => <article className="resource-card discussion-card" key={item.id}>
        <div className="resource-copy"><h3><Link to={`/courses/${courseId}/discussions/${item.id}`}>{item.title}</Link></h3><p className="discussion-preview">{item.content}</p><p className="muted">Người dùng #{item.authorId} · {formatDate(item.createdAt)}</p></div>
        <div className="course-actions"><button type="button" disabled={busy} onClick={() => { setError(''); setEditor({ item }) }}>Sửa</button><button type="button" disabled={busy} onClick={() => remove(item)}>Xóa</button></div>
      </article>)}</div>
    </RemoteState>
    <Pagination page={page} totalPages={state.data?.totalPages ?? 0} busy={state.loading || busy} onChange={setPage} />
    {editor && <DiscussionEditor item={editor.item} busy={busy} error={error} onSave={save} onClose={() => setEditor(null)} />}
  </section>
}
