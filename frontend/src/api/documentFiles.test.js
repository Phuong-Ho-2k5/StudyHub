import test from 'node:test'
import assert from 'node:assert/strict'
import { uploadDocument, validatePdf } from './documentFiles.js'

test('PDF selection rejects empty files and non PDF names', () => {
  assert.match(validatePdf({ name: 'notes.txt', size: 10 }), /PDF/)
  assert.match(validatePdf({ name: 'notes.pdf', size: 0 }), /rỗng/)
  assert.equal(validatePdf({ name: 'Bài học.PDF', size: 200 }), '')
})

test('upload sends multipart with bearer auth, progress and server errors', async () => {
  const original = globalThis.XMLHttpRequest
  let xhr
  class Upload {
    constructor() { xhr = this; this.upload = {}; this.headers = {} }
    open(method, url) { this.method = method; this.url = url }
    setRequestHeader(key, value) { this.headers[key] = value }
    send(body) { this.body = body }
  }
  globalThis.XMLHttpRequest = Upload
  try {
    const progress = []
    const pending = uploadDocument('token', 7, 'Bài học', new Blob(['pdf'], { type: 'application/pdf' }), (n) => progress.push(n))
    assert.equal(xhr.method, 'POST')
    assert.equal(xhr.url, '/api/courses/7/documents/upload')
    assert.equal(xhr.headers.Authorization, 'Bearer token')
    assert.equal(xhr.headers['Content-Type'], undefined)
    assert.equal(xhr.body.get('title'), 'Bài học')
    assert.ok(xhr.body.get('file') instanceof Blob)
    xhr.upload.onprogress({ lengthComputable: true, loaded: 20, total: 100 })
    assert.deepEqual(progress, [20])
    xhr.status = 201; xhr.responseText = '{"id":1}'; xhr.onload()
    assert.deepEqual(await pending, { id: 1 })
    const rejected = uploadDocument('token', 7, 'PDF', new Blob(['pdf']))
    xhr.status = 413; xhr.responseText = '{"message":"Too large"}'; xhr.onload()
    await assert.rejects(rejected, (error) => error.status === 413 && error.message === 'Too large')
  } finally { globalThis.XMLHttpRequest = original }
})

