// A legacy extraction response: valid for extract-only, invalid when chunks were requested.
const http = require('http');
http.createServer(async (req, res) => {
  if (req.method !== 'POST' || req.url !== '/internal/documents/extract') {
    res.writeHead(501); return res.end();
  }
  let body = req.body;
  if (body === undefined) {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    body = Buffer.concat(chunks).toString('utf8');
  }
  const match = String(body).match(/name="documentId"\r\n\r\n(\d+)/);
  res.writeHead(200, {'Content-Type': 'application/json'});
  res.end(JSON.stringify({documentId: match ? Number(match[1]) : 42,
    source: 'legacy.pdf', pageCount: 1, pages: [{pageNumber: 1, text: 'legacy text', blocks: []}], warnings: []}));
}).listen(process.env.PORT || 4900);
