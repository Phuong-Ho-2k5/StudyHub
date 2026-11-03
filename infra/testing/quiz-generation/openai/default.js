// Synthetic Responses fixture: reuse the existing Concept adapter's wire contract.
const http = require('http');
const counts = {};
http.createServer(async (req, res) => {
  const reply = (status, body) => { res.writeHead(status, {'Content-Type': 'application/json'}); res.end(JSON.stringify(body)); };
  if (req.method === 'GET' && req.url === '/_stats') return reply(200, {counts});
  if (req.method !== 'POST' || !['/v1/responses', '/v1/responses/input_tokens'].includes(req.url)) return reply(501, {});
  if (req.headers.authorization !== 'Bearer simulation-key') return reply(401, {error: {message: 'private credential error'}});
  try {
    let body = req.body;
    if (body === undefined) { const chunks = []; for await (const chunk of req) chunks.push(chunk); body = Buffer.concat(chunks).toString('utf8'); }
    if (typeof body === 'string') body = JSON.parse(body);
    if (body.text?.format?.name !== 'quiz_generation' || body.text.format.strict !== true ||
        !body.text.format.schema?.properties?.questions || body.input?.[0]?.role !== 'system' || body.input?.[1]?.role !== 'user') return reply(400, {});
    const data = JSON.parse(body.input[1].content);
    if (!data.contexts?.length || !Number.isInteger(data.questionCount) || data.contexts.some(c => !c.chunkId || !c.documentId || !c.content)) return reply(400, {});
    const content = data.contexts.map(c => c.content).join('\n');
    if (content.includes('[unavailable]')) return reply(503, {error: {message: 'private upstream error'}});
    if (content.includes('[timeout]')) await new Promise(resolve => setTimeout(resolve, 2200));
    if (req.url.endsWith('/input_tokens')) return reply(200, {input_tokens: content.includes('[budget]') ? 100000 : 100});
    counts[content] = (counts[content] || 0) + 1;
    if (body.store !== false || body.truncation !== 'disabled' || !Number.isInteger(body.max_output_tokens) || body.tools) return reply(400, {});
    if (content.includes('[rate-limit]')) return reply(429, {error: {message: 'private rate limit'}});
    if (content.includes('[truncated]')) return reply(200, {status: 'incomplete', model: body.model, output: []});
    if (content.includes('[malformed]')) return reply(200, {status: 'completed', model: body.model, output: []});
    const first = data.contexts[0];
    const evidence = [{chunkId: content.includes('[fake-id]') ? 999999999 : first.chunkId,
      quote: content.includes('[fake-quote]') ? 'fabricated evidence' : Array.from(first.content).slice(0, 1000).join('')}];
    const questions = Array.from({length: data.questionCount}, (_, i) => ({text: `Atomicity question ${i}`, options:
      ['All or nothing', 'Sorting', 'Indexing', 'Caching'].map((text, n) => ({text, correct: n === 0 || (n === 1 && content.includes('[bad-options]'))})), evidence}));
    const output = content.includes('[insufficient]') ? {status: 'INSUFFICIENT_CONTENT', questions: []} : {status: 'GENERATED', questions};
    return reply(200, {status: 'completed', model: body.model, output: [{type: 'message', role: 'assistant', status: 'completed',
      content: [{type: 'output_text', text: JSON.stringify(output)}]}]});
  } catch { return reply(400, {error: {message: 'Invalid simulation input'}}); }
}).listen(process.env.PORT || 4940);
