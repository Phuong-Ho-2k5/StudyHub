// Based on ai-service/openapi.json. Validates the multipart contract used by Spring.
const http = require('http');
const server = http.createServer(async (req, res) => {
  if (req.method === 'POST' && req.url === '/internal/embeddings') {
    if (req.headers['x-ai-service-token'] !== 'simulation-token') {
      res.writeHead(401); return res.end();
    }
    let data = req.body;
    if (data === undefined) {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      data = Buffer.concat(chunks).toString('utf8');
    }
    try { if (typeof data === 'string') data = JSON.parse(data); } catch {
      res.writeHead(422); return res.end();
    }
    if (!req.headers['content-type']?.startsWith('application/json') ||
        !Array.isArray(data.texts) || !data.texts.length || data.texts.length > 32 ||
        data.texts.some(text => typeof text !== 'string' || !text.trim() || text.length > 16384)) {
      res.writeHead(422); return res.end();
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      model: 'intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3',
      dimensions: 384,
      vectors: data.texts.map(() => [1, ...Array(383).fill(0)]),
    }));
  }
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
  const includeChunks = /name="includeChunks"\r\n\r\ntrue/.test(raw) || body.includeChunks === 'true';
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
    ...(includeChunks ? { chunks: [{
      documentId, source: 'document.pdf', chunkIndex: 0,
      content: 'StudyHub extracted text', embeddingText: 'StudyHub\nStudyHub extracted text',
      headings: ['StudyHub'], pageStart: 1, pageEnd: 1, blockIds: ['#/texts/0'],
      sourceRefs: [{blockId: '#/texts/0', pageNumber: 1,
        bbox: {l: 10, t: 20, r: 200, b: 80, coordOrigin: 'TOPLEFT'}}],
      overlapContent: '', overlapSourceRefs: [], tokenCount: 8, tokenizerModel: 'intfloat/multilingual-e5-small',
    }] } : {}),
  }));
});
server.listen(process.env.PORT || 4900);
