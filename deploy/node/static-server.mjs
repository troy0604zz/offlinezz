import { createReadStream, existsSync, statSync } from 'node:fs'
import { createServer, request as proxyRequest } from 'node:http'
import { extname, resolve, sep } from 'node:path'

const webRoot = resolve(process.env.WEB_ROOT || './web')
const host = process.env.WEB_HOST || '127.0.0.1'
const port = Number(process.env.PORT || 5173)
const backend = new URL(process.env.BACKEND_URL || 'http://127.0.0.1:8080')

const contentTypes = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
}

function proxy(req, res) {
  const upstream = proxyRequest({
    protocol: backend.protocol,
    hostname: backend.hostname,
    port: backend.port,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: backend.host }
  }, upstreamResponse => {
    res.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers)
    upstreamResponse.pipe(res)
  })

  upstream.setTimeout(180_000, () => upstream.destroy(new Error('Backend timeout')))
  upstream.on('error', error => {
    if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end(`Backend unavailable: ${error.message}`)
  })
  req.pipe(upstream)
}

function serveFile(req, res) {
  const pathname = decodeURIComponent((req.url || '/').split('?')[0])
  const requested = resolve(webRoot, `.${pathname === '/' ? '/index.html' : pathname}`)
  const insideRoot = requested === webRoot || requested.startsWith(`${webRoot}${sep}`)
  const file = insideRoot && existsSync(requested) && !statSync(requested).isDirectory()
    ? requested
    : resolve(webRoot, 'index.html')

  if (!existsSync(file)) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end(`Frontend entry not found under ${webRoot}`)
    return
  }

  const headers = {
    'Content-Type': contentTypes[extname(file).toLowerCase()] || 'application/octet-stream',
    'Cache-Control': file.endsWith('index.html') ? 'no-cache' : 'public, max-age=86400'
  }
  res.writeHead(200, headers)
  if (req.method === 'HEAD') res.end()
  else createReadStream(file).pipe(res)
}

const server = createServer((req, res) => {
  if (req.url?.startsWith('/api/') || req.url?.startsWith('/actuator/')) proxy(req, res)
  else serveFile(req, res)
})

server.requestTimeout = 180_000
server.headersTimeout = 185_000
server.listen(port, host, () => {
  console.log(`AI BI UI listening on http://${host}:${port}`)
  console.log(`Frontend root: ${webRoot}`)
  console.log(`Backend proxy: ${backend}`)
})
