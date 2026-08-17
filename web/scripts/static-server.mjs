import { createReadStream, existsSync, statSync } from 'node:fs'
import { createServer, request as proxyRequest } from 'node:http'
import { extname, join, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = normalize(join(fileURLToPath(new URL('.', import.meta.url)), '..', 'dist'))
const port = Number(process.env.PORT || 5173)
const backend = new URL(process.env.BACKEND_URL || 'http://127.0.0.1:8080')
const types = { '.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon' }

createServer((req, res) => {
  if (req.url?.startsWith('/api/') || req.url?.startsWith('/actuator/')) {
    const upstream = proxyRequest({ hostname: backend.hostname, port: backend.port, path: req.url, method: req.method, headers: req.headers }, r => {
      res.writeHead(r.statusCode || 502, r.headers); r.pipe(res)
    })
    upstream.on('error', () => { res.writeHead(502); res.end('Backend unavailable') })
    req.pipe(upstream); return
  }
  const pathname = decodeURIComponent((req.url || '/').split('?')[0])
  let file = normalize(join(root, pathname === '/' ? 'index.html' : pathname))
  if (!file.startsWith(root) || !existsSync(file) || statSync(file).isDirectory()) file = join(root, 'index.html')
  res.writeHead(200, { 'Content-Type': types[extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' })
  createReadStream(file).pipe(res)
}).listen(port, '127.0.0.1', () => console.log(`AI BI UI: http://127.0.0.1:${port}`))
