const API_URL = import.meta.env.VITE_API_URL?.replace(/\/$/, '') ?? ''
const CONNECTION_ERROR = 'Không thể kết nối tới máy chủ. Vui lòng thử lại.'

export class ApiError extends Error {
  constructor(message, status = 0, details = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
  }
}

async function request(path, options) {
  let response

  try {
    response = await fetch(`${API_URL}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    })
  } catch {
    throw new ApiError(CONNECTION_ERROR)
  }

  let responseText

  try {
    responseText = await response.text()
  } catch {
    throw new ApiError(CONNECTION_ERROR)
  }

  let body = {}

  if (responseText) {
    try {
      const parsedBody = JSON.parse(responseText)
      body = parsedBody && typeof parsedBody === 'object' ? parsedBody : {}
    } catch {
      body = {}
    }
  }

  if (!response.ok) {
    if ([502, 503, 504].includes(response.status)) {
      throw new ApiError(CONNECTION_ERROR, response.status, body)
    }

    const message = typeof body.message === 'string'
      ? body.message
      : 'Yêu cầu chưa được xử lý. Vui lòng thử lại.'

    throw new ApiError(message, response.status, body)
  }

  return body
}

export function registerUser({ name, email, password }) {
  return request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name, email, password }),
  })
}

export function loginUser({ email, password }) {
  return request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}
