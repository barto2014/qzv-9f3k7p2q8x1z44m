const CACHE_NAME = 'bartotv-v6';

// Cacheo individual resiliente: si un archivo falla, igual se cachea el resto.
const SHELL_FILES = [
  'index.html',
  'hls.html',
  'mpd.html',
  'youtube.html',
  'redirect.html',
  'manifest.json',
  'favicon.svg',
  'icon-192.png',
  'icon-512.png'
];

self.addEventListener('install', (e) => {
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE_NAME).then(cache =>
      Promise.allSettled(SHELL_FILES.map(f => cache.add(f).catch(() => null)))
    )
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);

  // datar.bin siempre va a la red (datos en vivo)
  if (url.pathname.endsWith('datar.bin')) {
    e.respondWith(fetch(e.request).catch(() => new Response('', { status: 503 })));
    return;
  }

  // APIs y streams proxificados siempre van a la red.
  if (url.pathname.startsWith('/api/')) {
    e.respondWith(fetch(e.request));
    return;
  }

  // CDN externos — network first, fallback a cache
  if (url.origin !== self.location.origin) {
    e.respondWith(
      fetch(e.request).then(response => {
        const clone = response.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(e.request, clone));
        return response;
      }).catch(() => caches.match(e.request))
    );
    return;
  }

  // Archivos locales — cache first, fallback a network
  e.respondWith(
    caches.match(e.request).then(cached => {
      return cached || fetch(e.request).then(response => {
        const clone = response.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(e.request, clone));
        return response;
      });
    })
  );
});
