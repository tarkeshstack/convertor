// Minimal service worker — its only job is to exist and respond to fetches,
// which satisfies Chrome/Android's "installable web app" requirement so the
// Add to Home Screen prompt can fire. It doesn't cache anything or work offline.
self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  event.respondWith(fetch(event.request));
});
