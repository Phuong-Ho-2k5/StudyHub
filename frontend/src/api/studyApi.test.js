import test from 'node:test'
import assert from 'node:assert/strict'
import { buildCourseQuery, buildDocumentQuery, buildLowConfidenceQuery } from './studyApi.js'

test('course query includes only active filters and zero based pagination', () => {
  assert.equal(
    buildCourseQuery({ workspaceId: '3', status: 'IN_PROGRESS', q: '  Java  ', page: 2 }),
    '?workspaceId=3&status=IN_PROGRESS&q=Java&page=2&size=10&sort=name%2Casc',
  )
})

test('course query omits empty filters', () => {
  assert.equal(
    buildCourseQuery({ workspaceId: '', status: '', q: '   ', page: 0 }),
    '?page=0&size=10&sort=name%2Casc',
  )
})

test('document query scopes results to a course and trims search text', () => {
  assert.equal(buildDocumentQuery({ courseId: 7, q: '  Notes  ', page: 1 }),
    '?courseId=7&q=Notes&page=1&size=10&sort=title%2Casc')
})

test('low confidence query keeps the zero threshold', () => {
  assert.equal(buildLowConfidenceQuery(0), '?confidenceBelow=0')
})
