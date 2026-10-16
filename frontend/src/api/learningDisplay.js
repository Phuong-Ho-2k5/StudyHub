export function formatStudyTime(seconds) {
  const minutes = Math.floor(Math.max(0, Number(seconds) || 0) / 60)
  const hours = Math.floor(minutes / 60)
  return hours ? `${hours} giờ ${minutes % 60} phút` : `${minutes} phút`
}

export const formatPercentage = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(Number(value) || 0)}%`
export const formatDate = (value) => value ? new Date(value).toLocaleString('vi-VN') : '—'

export function errorMessage(error) {
  if (error?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  if (error?.status === 403) return 'Bạn không có quyền thực hiện thao tác này.'
  if (error?.status === 404) return 'Không tìm thấy dữ liệu hoặc bạn không có quyền truy cập.'
  if (error?.details?.fieldErrors) return Object.values(error.details.fieldErrors).join('. ')
  return error?.message || 'Có lỗi xảy ra. Vui lòng thử lại.'
}
