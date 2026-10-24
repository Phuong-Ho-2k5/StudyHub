const http = require('http');
http.createServer((req, res) => {
  res.writeHead(500, { 'Content-Type': 'text/html' });
  res.end('<html>upstream failure</html>');
}).listen(process.env.PORT);
