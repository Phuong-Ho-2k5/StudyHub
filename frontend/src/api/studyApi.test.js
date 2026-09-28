import test from 'node:test'
import assert from 'node:assert/strict'
import { buildCourseQuery } from './studyApi.js'

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
