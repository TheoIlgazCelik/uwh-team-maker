self.addEventListener('push', event => {
  const data = event.data ? event.data.json() : { title: 'New message', body: 'Open app' };
  const options = {
    body: data.body,
    icon: data.icon,
    badge: data.badge,
    data: data.url // optional: open on click
  };
  event.waitUntil(self.registration.showNotification(data.title, options));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const url = event.notification.data || '/';
  event.waitUntil(clients.matchAll({type: 'window', includeUncontrolled: true})
    .then(windowClients => {
      for (const client of windowClients) {
        if (client.url === url && 'focus' in client) return client.focus();
      }
      if (clients.openWindow) return clients.openWindow(url);
    })
  );
});
