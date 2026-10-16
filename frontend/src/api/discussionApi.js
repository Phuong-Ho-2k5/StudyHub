import { authorizedRequest as api, pageQuery } from './authorizedRequest.js'

export const listPosts = (token, courseId, page = 0) => api(token, `/api/courses/${courseId}/discussion-posts${pageQuery(page)}`)
export const getPost = (token, id) => api(token, `/api/discussion-posts/${id}`)
export const createPost = (token, courseId, data) => api(token, `/api/courses/${courseId}/discussion-posts`, 'POST', data)
export const updatePost = (token, id, data) => api(token, `/api/discussion-posts/${id}`, 'PUT', data)
export const deletePost = (token, id) => api(token, `/api/discussion-posts/${id}`, 'DELETE')
export const listComments = (token, postId, page = 0) => api(token, `/api/discussion-posts/${postId}/comments${pageQuery(page)}`)
export const getComment = (token, id) => api(token, `/api/comments/${id}`)
export const createComment = (token, postId, data) => api(token, `/api/discussion-posts/${postId}/comments`, 'POST', data)
export const updateComment = (token, id, data) => api(token, `/api/comments/${id}`, 'PUT', data)
export const deleteComment = (token, id) => api(token, `/api/comments/${id}`, 'DELETE')
