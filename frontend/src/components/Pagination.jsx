export function Pagination({ page, totalPages, busy = false, onChange }) {
  if (totalPages <= 1) return null
  return <nav className="pagination" aria-label="Phân trang"><span>Trang {page + 1} / {totalPages}</span><div>
    <button className="secondary-button" type="button" disabled={busy || page <= 0} onClick={() => onChange(page - 1)}>Trước</button>
    <button className="secondary-button" type="button" disabled={busy || page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Sau</button>
  </div></nav>
}
