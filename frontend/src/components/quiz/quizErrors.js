import { ApiError } from '../../api/authApi.js'

export function quizError(error) {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    if (error.status === 404) return 'Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập.'
    if (error.details?.fieldErrors) return Object.values(error.details.fieldErrors).flat().join('. ')
    if (error.status >= 500) return 'Máy chủ chưa xử lý được yêu cầu. Vui lòng thử lại.'
    return error.message
  }
  return error.message || 'Có lỗi xảy ra. Vui lòng thử lại.'
}
