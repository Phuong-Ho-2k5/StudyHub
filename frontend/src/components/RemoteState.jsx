import { errorMessage } from '../api/learningDisplay'

export function RemoteState({ loading, error, empty, onRetry, children }) {
  if (loading) return <p className="list-state" role="status">Đang tải…</p>
  if (error) return <div className="remote-error"><p className="form-message error" role="alert">{errorMessage(error)}</p>{onRetry && <button className="secondary-button" type="button" onClick={onRetry}>Thử lại</button>}</div>
  if (empty) return <div className="empty-state"><p>{typeof empty === 'string' ? empty : 'Chưa có dữ liệu.'}</p></div>
  return children
}
