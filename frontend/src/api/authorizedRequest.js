import { request } from './authApi.js'

export function authorizedRequest(token, path, method = 'GET', data) {
  return request(path, {
    method,
    headers: { Authorization: `Bearer ${token}` },
    ...(data === undefined ? {} : { body: JSON.stringify(data) }),
  })
}

export const pageQuery = (page = 0, size = 20) => `?${new URLSearchParams({ page: String(page), size: String(size) })}`
