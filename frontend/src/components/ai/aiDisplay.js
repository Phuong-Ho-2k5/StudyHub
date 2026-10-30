export function validateAiInput(value) {
  if (!value.trim()) return 'Vui lòng nhập nội dung.'
  if (value.length > 16384) return 'Nội dung tối đa 16384 ký tự.'
  return ''
}

export function formatSourcePages(start, end) {
  if (!Number.isInteger(start) || start < 1) return 'Chưa xác định trang'
  return Number.isInteger(end) && end > start ? `Trang ${start}–${end}` : `Trang ${start}`
}

export function ingestionDisplay(document) {
  const status = document.ingestionStatus ?? 'NOT_REQUESTED'
  const pending = ['PENDING', 'PROCESSING'].includes(status)
  return {
    label: { NOT_REQUESTED: 'Chưa xử lý', PENDING: 'Đang chờ xử lý', PROCESSING: 'Đang xử lý', INDEXED: 'Đã xử lý', FAILED: 'Xử lý thất bại' }[status] ?? 'Chưa rõ trạng thái',
    published: document.publishedIngestionId != null,
    pending,
    action: !document.managedFile || pending ? null : { NOT_REQUESTED: 'Xử lý', INDEXED: 'Xử lý lại', FAILED: 'Thử lại' }[status] ?? null,
  }
}

export function aiErrorMessage(error, operation = 'chat') {
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  if (error.status === 404) return 'Không tìm thấy tài nguyên hoặc bạn không có quyền truy cập.'
  if (error.status === 409) return operation === 'ingest'
    ? 'Tác vụ đang chạy hoặc tài liệu đã thay đổi. Hãy cập nhật trạng thái trước khi thử lại.'
    : 'Nguồn tài liệu đã thay đổi. Vui lòng tìm kiếm hoặc gửi lại câu hỏi.'
  if (error.status === 422) return operation === 'ingest'
    ? 'Không thể xử lý đầy đủ tài liệu này. Vui lòng kiểm tra PDF.'
    : 'Nội dung vượt giới hạn xử lý. Hãy rút ngắn câu hỏi hoặc truy vấn.'
  if (error.status === 413) return 'PDF vượt giới hạn dung lượng xử lý.'
  if (error.status === 415) return 'PDF không hợp lệ hoặc chưa được hỗ trợ.'
  if (error.status === 502) return 'Dịch vụ trả kết quả không hợp lệ. Vui lòng thử lại sau.'
  if (error.status === 503) return 'Dịch vụ AI chưa sẵn sàng. Vui lòng thử lại sau.'
  if (error.status === 504) return operation === 'ingest'
    ? 'Xử lý quá thời gian chờ. Hãy cập nhật trạng thái trước khi thử lại.'
    : 'Quá thời gian chờ phản hồi. Bạn có thể gửi lại yêu cầu.'
  return error.status === 400 ? 'Nội dung yêu cầu không hợp lệ. Vui lòng kiểm tra lại.' : 'Không thể kết nối tới máy chủ. Vui lòng thử lại.'
}

export function validateChatResponse(data) {
  if (!data || typeof data.answer !== 'string' || !data.answer.trim() || !Array.isArray(data.citations)) return false
  if (data.status === 'INSUFFICIENT_CONTEXT') return data.citations.length === 0
  return data.status === 'ANSWERED' && data.citations.length > 0 && data.citations.every(c =>
    Number.isSafeInteger(c.chunkId) && Number.isSafeInteger(c.documentId) &&
    typeof c.documentTitle === 'string' && typeof c.quote === 'string' && c.quote.length > 0)
}
