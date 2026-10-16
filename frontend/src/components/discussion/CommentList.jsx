import { useRef, useState } from 'react'
import { createComment, deleteComment, listComments, updateComment } from '../../api/discussionApi'
import { errorMessage, formatDate } from '../../api/learningDisplay'
import { useAuth } from '../../auth/AuthContext'
import { useRemoteData } from '../../hooks/useRemoteData'
import { Pagination } from '../Pagination'
import { RemoteState } from '../RemoteState'
import { EditorDialog } from '../quiz/EditorDialog'

export function CommentList({ postId }) {
  const { token } = useAuth()
  const [page, setPage] = useState(0)
  const [content, setContent] = useState('')
  const [editor, setEditor] = useState(null)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const lock = useRef(false)
  const state = useRemoteData(() => listComments(token, postId, page), [token, postId, page])
  async function save(event, editing = false) {
    event.preventDefault()
    if (lock.current) return
    const text = editing ? draft : content
    if (!text.trim()) { setError('Vui lòng nhập nội dung bình luận.'); return }
    lock.current = true; setBusy(true); setError('')
    try {
      if (editing) { await updateComment(token, editor.id, { content: text.trim() }); setEditor(null) }
      else { await createComment(token, postId, { content: text.trim() }); setContent(''); setPage(Math.floor((state.data?.totalElements ?? 0) / 20)) }
      state.reload()
    } catch (caught) { setError(errorMessage(caught)) }
    finally { lock.current = false; setBusy(false) }
  }
  async function remove(item) {
    if (lock.current || !window.confirm('Xóa bình luận này?')) return
    lock.current = true; setBusy(true); setError('')
    try {
      await deleteComment(token, item.id)
      if (state.data.content.length === 1 && page > 0) setPage(page - 1)
      else state.reload()
    } catch (caught) { setError(errorMessage(caught)) }
    finally { lock.current = false; setBusy(false) }
  }
  return <section className="course-panel learning-section" aria-label="Bình luận">
    <h2>Bình luận <span className="count-pill">{state.data?.totalElements ?? 0}</span></h2>
    {error && !editor && <p className="form-message error" role="alert">{error}</p>}
    <RemoteState {...state} onRetry={state.reload} empty={state.data?.content.length === 0 && 'Chưa có bình luận.'}>
      <div className="comment-list">{state.data?.content.map((item) => <article className="comment-card" key={item.id}>
        <div className="comment-meta"><span>Người dùng #{item.authorId} · {formatDate(item.createdAt)}</span><div className="course-actions"><button type="button" disabled={busy} onClick={() => { setError(''); setDraft(item.content); setEditor(item) }}>Sửa bình luận</button><button type="button" disabled={busy} onClick={() => remove(item)}>Xóa bình luận</button></div></div>
        <p className="prose-content">{item.content}</p>
      </article>)}</div>
    </RemoteState>
    <Pagination page={page} totalPages={state.data?.totalPages ?? 0} busy={state.loading || busy} onChange={setPage} />
    <form className="editor-form comment-form" onSubmit={(event) => save(event)}>
      <label className="field"><span className="field-label">Bình luận mới</span><textarea rows={4} maxLength={10000} value={content} onChange={(event) => setContent(event.target.value)} required disabled={busy} /></label>
      <button className="primary-button detail-add" type="submit" disabled={busy}>{busy ? 'Đang gửi…' : 'Gửi bình luận'}</button>
    </form>
    {editor && <EditorDialog title="Sửa bình luận" busy={busy} onClose={() => setEditor(null)}><form className="editor-form" onSubmit={(event) => save(event, true)}>
      {error && <p className="form-message error" role="alert">{error}</p>}
      <label className="field"><span className="field-label">Nội dung bình luận</span><textarea rows={5} maxLength={10000} required value={draft} disabled={busy} onChange={(event) => setDraft(event.target.value)} /></label>
      <button className="primary-button" type="submit" disabled={busy}>Lưu bình luận</button>
    </form></EditorDialog>}
  </section>
}
