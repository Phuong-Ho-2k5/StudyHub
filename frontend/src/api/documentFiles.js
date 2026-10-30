import { ApiError } from './authApi.js'

const API_URL = import.meta.env?.VITE_API_URL?.replace(/\/$/, '') ?? ''

function unauthorized(token, status) {
  if (status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('studyhub:unauthorized', { detail: `Bearer ${token}` }))
  }
}

export async function fetchDocumentPdf(token, documentId, { signal } = {}) {
  let response
  try {
    response = await fetch(`${API_URL}/api/documents/${documentId}/file`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/pdf' }, signal,
    })
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new ApiError('Không thể tải PDF. Vui lòng thử lại.')
  }
  if (!response.ok) {
    unauthorized(token, response.status)
    throw new ApiError('Không thể tải PDF.', response.status)
  }
  if (response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase() !== 'application/pdf') {
    throw new ApiError('Máy chủ không trả về PDF hợp lệ.', 502)
  }
  try { return await response.blob() }
  catch (error) {
    if (error.name === 'AbortError') throw error
    throw new ApiError('Không thể đọc PDF. Vui lòng thử lại.')
  }
}

export function validatePdf(file) {
  if (!file || !file.size) return 'Tệp không được rỗng.'
  if (!file.name?.toLowerCase().endsWith('.pdf')) return 'Vui lòng chọn tệp PDF.'
  return ''
}

export function uploadDocument(token, courseId, title, file, onProgress = () => {}) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${API_URL}/api/courses/${courseId}/documents/upload`)
    xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.timeout = 120000
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round(event.loaded * 100 / event.total))
    }
    xhr.onload = () => {
      let data = {}
      try { data = JSON.parse(xhr.responseText) } catch { /* Preserve status for non JSON proxy errors. */ }
      if (xhr.status >= 200 && xhr.status < 300) resolve(data)
      else {
        unauthorized(token, xhr.status)
        reject(new ApiError(data.message || (xhr.status === 413 ? 'Tệp vượt quá dung lượng cho phép.' : 'Không thể tải tệp lên.'), xhr.status, data))
      }
    }
    xhr.onerror = () => reject(new ApiError('Không thể kết nối tới máy chủ. Vui lòng thử lại.'))
    xhr.ontimeout = () => reject(new ApiError('Tải lên quá thời gian chờ. Kiểm tra danh sách tài liệu trước khi thử lại.'))
    const body = new FormData()
    body.append('title', title)
    // Browsers occasionally omit the MIME type of a locally selected PDF.
    body.append('file', file.type ? file : new Blob([file], { type: 'application/pdf' }), file.name || 'document.pdf')
    xhr.send(body)
  })
}

