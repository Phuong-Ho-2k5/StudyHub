import { useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deletePost, getPost, updatePost } from '../api/discussionApi'
import { ApiError } from '../api/authApi'
import { errorMessage, formatDate } from '../api/learningDisplay'
import { useAuth } from '../auth/AuthContext'
import { AppHeader } from '../components/AppHeader'
import { RemoteState } from '../components/RemoteState'
import { CommentList } from '../components/discussion/CommentList'
import { DiscussionEditor } from '../components/discussion/DiscussionEditor'
import { useRemoteData } from '../hooks/useRemoteData'

function DiscussionDetail({ courseId, postId }) {
  const { token } = useAuth()
  const navigate = useNavigate()
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const lock = useRef(false)
  const back = `/courses/${courseId}?tab=discussions`
  const state = useRemoteData(async () => {
    const post = await getPost(token, postId)
    if (String(post.courseId) !== courseId) throw new ApiError('Bài thảo luận không thuộc khóa học này.', 404)
    return post
  }, [token, courseId, postId])
  async function save(data) {
    if (lock.current) return
    lock.current = true; setBusy(true); setError('')
    try { await updatePost(token, postId, data); setEditing(false); state.reload() }
    catch (caught) { setError(errorMessage(caught)) }
    finally { lock.current = false; setBusy(false) }
  }
  async function remove() {
    if (lock.current || !window.confirm('Xóa bài thảo luận và tất cả bình luận?')) return
    lock.current = true; setBusy(true); setError('')
    try { await deletePost(token, postId); navigate(back) }
    catch (caught) { setError(errorMessage(caught)) }
    finally { lock.current = false; setBusy(false) }
  }
  return <main className="dashboard-page"><AppHeader /><div className="detail-shell" id="main-content" tabIndex={-1}>
    <Link className="text-link" to={back}>← Thảo luận khóa học</Link>
    <RemoteState {...state} onRetry={state.reload}>{state.data && <>
      <article className="course-panel learning-section post-detail"><p className="section-kicker">Thảo luận</p><h1>{state.data.title}</h1><p className="muted">Người dùng #{state.data.authorId} · {formatDate(state.data.createdAt)}</p>
        <p className="prose-content">{state.data.content}</p>
        <div className="course-actions"><button type="button" disabled={busy} onClick={() => { setError(''); setEditing(true) }}>Sửa bài viết</button><button type="button" disabled={busy} onClick={remove}>Xóa bài viết</button></div>
        {error && !editing && <p className="form-message error" role="alert">{error}</p>}
      </article><CommentList key={postId} postId={postId} />
    </>}</RemoteState>
    {editing && <DiscussionEditor item={state.data} busy={busy} error={error} onSave={save} onClose={() => setEditing(false)} />}
  </div></main>
}
export function DiscussionDetailPage() {
  const { courseId, postId } = useParams()
  return <DiscussionDetail key={`${courseId}-${postId}`} courseId={courseId} postId={postId} />
}
