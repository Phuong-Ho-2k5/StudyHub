const http = require('http');
http.createServer((req, res) => {
  if (req.method !== 'POST' || req.url !== '/internal/embeddings') {
    res.writeHead(501); return res.end();
  }
  res.writeHead(422, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ detail: 'Embedding input exceeds the supported token budget.' }));
}).listen(process.env.PORT);
