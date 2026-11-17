// VORNX customer app: service worker (makes the app installable and fast).
//
// Rules - kept small and safe on purpose:
//  - Only GET requests of THIS site are handled. API calls (another domain) and anything with a login token are
//    never touched or cached: money and permits always come fresh from the server.
//  - Pages: always from the network; offline -> offline.html ("no connection, retry"). Without the API the app
//    can't show a card or a balance anyway, so a clear offline page is better than a half-working app.
//  - /assets/* have a content hash in their name: cache first (faster starts; a new deploy has new names).
//  - /brand/*, /icons/*: served from cache, refreshed in the background.
// Change VERSION to drop old caches.
const VERSION = 'v1'
const SHELL = `vornx-shell-${VERSION}`
const STATIC = `vornx-static-${VERSION}`

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL)
      .then((c) => c.addAll(['/offline.html', '/icons/icon-192.png']))
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== SHELL && k !== STATIC).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  const url = new URL(req.url)
  if (req.method !== 'GET' || url.origin !== self.location.origin || req.headers.has('Authorization')) return

  if (req.mode === 'navigate') {
    event.respondWith(fetch(req).catch(() => caches.match('/offline.html')))
    return
  }

  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(
      caches.match(req).then((hit) => hit || fetch(req).then((res) => {
        if (res.ok) { const copy = res.clone(); caches.open(STATIC).then((c) => c.put(req, copy)) }
        return res
      })),
    )
    return
  }

  if (url.pathname.startsWith('/brand/') || url.pathname.startsWith('/icons/')) {
    event.respondWith(
      caches.match(req).then((hit) => {
        const fresh = fetch(req).then((res) => {
          if (res.ok) { const copy = res.clone(); caches.open(STATIC).then((c) => c.put(req, copy)) }
          return res
        }).catch(() => hit)
        return hit || fresh
      }),
    )
  }
})
