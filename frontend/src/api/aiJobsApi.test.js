import { test } from 'node:test'
import assert from 'node:assert/strict'
import { getAIJob, submitQuizJob } from './aiJobsApi.js'
const id = '5d0b41ee-9f4d-42ec-94ed-216d29cbdbca'
test('job status uses JWT and cancellation; generation submits once with its key', async () => {
 const previous = globalThis.fetch; const calls = []; const controller = new AbortController()
 globalThis.fetch = async (url, options) => { calls.push({url,options}); return Response.json({jobId:id}) }
 try {
  await getAIJob('token', id, {signal:controller.signal})
  await submitQuizJob('token', {courseId:7, documentId:9, key:'one-key', request:{title:'Quiz',questionCount:2}})
  assert.equal(calls.length,2);assert.equal(calls[0].url,`/api/ai/jobs/${id}`)
  assert.equal(calls[0].options.signal,controller.signal);assert.equal(calls[0].options.headers.Authorization,'Bearer token')
  assert.equal(calls[1].options.headers['Idempotency-Key'],'one-key');assert.equal(calls[1].options.method,'POST')
  assert.deepEqual(JSON.parse(calls[1].options.body),{title:'Quiz',questionCount:2})
  await assert.rejects(()=>getAIJob('token','not-uuid'), /job/i);assert.equal(calls.length,2)
 } finally { globalThis.fetch=previous }
})
test('status failures preserve server status and never retry generation', async () => {
 const previous=globalThis.fetch;let calls=0
 globalThis.fetch=async()=>{calls++;return Response.json({message:'Unavailable'},{status:503})}
 try {await assert.rejects(()=>submitQuizJob('token',{documentId:9,key:'key',request:{title:'Quiz'}}),error=>error.status===503);assert.equal(calls,1)} finally {globalThis.fetch=previous}
})
