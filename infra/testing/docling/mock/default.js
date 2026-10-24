// Based on ai-service/openapi.json. Validates the multipart contract used by Spring.
const http = require('http');
const server = http.createServer(async (req, res) => {
  if (req.method !== 'POST' || req.url !== '/internal/documents/extract') {
    res.writeHead(501); return res.end();
  }
  if (req.headers['x-ai-service-token'] !== 'simulation-token') {
    res.writeHead(401); return res.end();
  }
  let body = req.body;
  if (body === undefined) {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    body = Buffer.concat(chunks).toString('utf8');
  }
  const raw = typeof body === 'string' ? body : JSON.stringify(body);
  const match = raw.match(/name="documentId"\r\n\r\n(\d+)/);
  const documentId = match ? Number(match[1]) : Number(body.documentId);
  if (!documentId || !raw.includes('%PDF-') || !req.headers['content-type']?.startsWith('multipart/form-data; boundary=')) {
    res.writeHead(422); return res.end(JSON.stringify({ detail: 'Invalid multipart PDF' }));
  }
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    documentId, source: 'document.pdf', pageCount: 1,
    pages: [{ pageNumber: 1, text: 'StudyHub extracted text', blocks: [{
      id: '#/texts/0', type: 'text', text: 'StudyHub extracted text',
      bbox: { l: 10, t: 20, r: 200, b: 80, coordOrigin: 'TOPLEFT' },
      description: null, descriptionMethod: null,
    }] }], warnings: [],
  }));
});
server.listen(process.env.PORT || 4900);
