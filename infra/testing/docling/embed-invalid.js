const http = require('http');
http.createServer((req, res) => {
  if (req.method !== 'POST' || req.url !== '/internal/embeddings') {
    res.writeHead(501); return res.end();
  }
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    model: 'intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3',
    dimensions: 384, vectors: [[1, 0]],
  }));
}).listen(process.env.PORT);
