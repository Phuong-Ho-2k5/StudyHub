const http = require('http');
http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end('{"documentId":42,"pageCount":1,"pages":[],"warnings":[]}');
}).listen(process.env.PORT);
