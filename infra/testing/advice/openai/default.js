// Derived from the repository's Responses wire fixture. Synthetic facts and token counts.
const http = require('http');
const counts = {tokens: 0, generations: 0};
http.createServer(async (req, res) => {
  const reply = (status, data) => {
    res.writeHead(status, {'Content-Type': 'application/json'});
    res.end(JSON.stringify(data));
  };
  if (req.method === 'GET' && req.url === '/_stats') return reply(200, counts);
  if (req.method !== 'POST' || !['/v1/responses', '/v1/responses/input_tokens'].includes(req.url)) return reply(501, {});
  if (req.headers.authorization !== 'Bearer simulation-key') return reply(401, {error: {message: 'Synthetic auth failure'}});
  try {
    let body = req.body;
    if (body === undefined) {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      body = Buffer.concat(chunks).toString('utf8');
    }
    if (typeof body === 'string') body = JSON.parse(body);
    if (body.text?.format?.name !== 'learning_advice' || body.text.format.strict !== true ||
        !body.text.format.schema?.properties?.usedLearningEvidenceIds ||
        body.input?.[0]?.role !== 'system' || body.input?.[1]?.role !== 'user') return reply(400, {});
    const data = JSON.parse(body.input[1].content);
    if (!data.learningEvidence?.length || !Array.isArray(data.contexts) || typeof data.question !== 'string') return reply(400, {});
    const marker = data.question;
    if (marker.includes('[unavailable]')) return reply(503, {error: {message: 'Synthetic unavailable'}});
    if (marker.includes('[timeout]')) await new Promise(resolve => setTimeout(resolve, 2200));
    if (req.url.endsWith('/input_tokens')) {
      counts.tokens++;
      return reply(200, {input_tokens: marker.includes('[budget]') ? 100000 : 100});
    }
    counts.generations++;
    if (body.store !== false || body.truncation !== 'disabled' || !Number.isInteger(body.max_output_tokens) || body.tools) return reply(400, {});
    if (marker.includes('[rate-limit]')) return reply(429, {error: {message: 'Synthetic rate limit'}});
    if (marker.includes('[malformed]')) return reply(200, {status: 'completed', model: body.model, output: []});
    const insufficient = marker.includes('[insufficient]');
    const learningOnly = marker.includes('[learning-only]') || data.contexts.length === 0;
    const concept = data.learningEvidence.find(f => f.type === 'CONCEPT');
    const fact = concept || data.learningEvidence[0];
    const evidence = insufficient || learningOnly ? [] : [{
      chunkId: marker.includes('[fake-id]') ? 999999999 : data.contexts[0].chunkId,
      quote: marker.includes('[fake-quote]') ? 'fabricated evidence' : Array.from(data.contexts[0].content).slice(0, 1000).join('')
    }];
    const answer = concept ? `Confidence của ${concept.data.name} hiện là ${concept.data.confidence}; hãy dùng dữ liệu này để chọn phần ôn tập.` : 'Dựa trên lịch sử học đã cung cấp, hãy kiểm tra lại tiến độ.';
    const output = {
      status: insufficient ? 'INSUFFICIENT_CONTEXT' : learningOnly ? 'LEARNING_DATA_ONLY' : 'ANSWERED',
      answer,
      usedLearningEvidenceIds: insufficient ? [] : [marker.includes('[unknown-fact]') ? 'concept:999999999' : fact.id],
      evidence
    };
    return reply(200, {status: 'completed', model: body.model, output: [{type: 'message', role: 'assistant', status: 'completed',
      content: [{type: 'output_text', text: JSON.stringify(output)}]}]});
  } catch {
    return reply(400, {error: {message: 'Invalid synthetic request'}});
  }
}).listen(process.env.PORT || 4935);
