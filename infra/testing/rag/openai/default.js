// Wire fixtures based on OpenAI Responses/Structured Outputs/token-counting docs,
// verified 2026-10-27. Local simulation only: no provider credentials or real data.
const http = require('http');
const generationCounts = {};
const server = http.createServer(async (req, res) => {
  const reply = (code, body) => { res.writeHead(code, {'Content-Type':'application/json'}); res.end(JSON.stringify(body)); };
  if (req.method === 'GET' && req.url === '/_stats') return reply(200,{generationCounts});
  if (req.method !== 'POST' || !['/v1/responses','/v1/responses/input_tokens'].includes(req.url)) return reply(501, {});
  if (req.headers.authorization !== 'Bearer simulation-key') return reply(401, {error:{message:'private provider credential error'}});
  let body = req.body;
  try {
    if (body === undefined) { const chunks=[]; for await (const chunk of req) chunks.push(chunk); body=Buffer.concat(chunks).toString('utf8'); }
    if (typeof body === 'string') body=JSON.parse(body);
    if (!body.model || !Array.isArray(body.input) || body.input[0]?.role !== 'system' || body.input[1]?.role !== 'user' ||
        body.text?.format?.type !== 'json_schema' || body.text.format.strict !== true) return reply(400,{error:{message:'Invalid Responses contract'}});
    const data=JSON.parse(body.input[1].content);
    if (!data.contexts?.length || data.contexts.some(c=>!c.content || !c.documentId || !c.chunkId)) return reply(400,{});
    const question=data.question;
    if (question.includes('[rate-limit]')) return reply(429,{error:{message:'private provider rate limit'}});
    if (question.includes('[unavailable]')) return reply(503,{error:{message:'private provider unavailable'}});
    if (question.includes('[html-error]')) { res.writeHead(500,{'Content-Type':'text/html'}); return res.end('<html>private failure</html>'); }
    if (question.includes('[timeout]')) { await new Promise(resolve=>setTimeout(resolve,2000)); }
    if (req.url.endsWith('/input_tokens')) {
      return reply(200,{object:'response.input_tokens',input_tokens:question.includes('[budget]') ? 100000 : Math.ceil(JSON.stringify(body).length/4)});
    }
    generationCounts[question]=(generationCounts[question] || 0)+1;
    if (question.includes('[generation-rate-limit]')) return reply(429,{error:{message:'private generation rate limit'}});
    if (body.store !== false || body.truncation !== 'disabled' || !Number.isInteger(body.max_output_tokens) || body.tools) return reply(400,{});
    if (question.includes('[malformed]')) return reply(200,{status:'completed',model:body.model,output:[]});
    if (question.includes('[truncated]')) return reply(200,{status:'incomplete',model:body.model,output:[]});
    const insufficient=question.includes('[insufficient]');
    const answer=insufficient ? 'No evidence.' : data.contexts[0].content;
    let evidence=insufficient ? [] : [{chunkId:data.contexts[0].chunkId,quote:Array.from(data.contexts[0].content).slice(0,1000).join('')}];
    if (question.includes('[fake-id]')) evidence[0].chunkId=999999999;
    if (question.includes('[fake-quote]')) evidence[0].quote='fabricated evidence';
    if (question.includes('[duplicate-evidence]')) evidence.push({...evidence[0]});
    const result={status:insufficient?'INSUFFICIENT_CONTEXT':'ANSWERED',answer,evidence};
    if (question.includes('[missing-evidence]')) delete result.evidence;
    return reply(200,{id:'resp_simulation',object:'response',status:'completed',model:body.model,
      output:[{type:'message',role:'assistant',status:'completed',content:[{type:'output_text',text:JSON.stringify(result),annotations:[]}]}],
      usage:{input_tokens:100,output_tokens:20,total_tokens:120}});
  } catch { return reply(400,{error:{message:'Invalid simulated request'}}); }
});
server.listen(process.env.PORT || 4927);
