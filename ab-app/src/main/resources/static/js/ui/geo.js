// Resolves to { latitude, longitude, accuracy, locatedAt } or rejects with Error whose message is
// 'unsupported' | 'denied' | 'unavailable' | 'timeout'. Browsers only allow this on HTTPS or localhost.
export function getLocation({ timeout = 10000 } = {}) {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) { reject(new Error('unsupported')); return; }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({
        latitude: pos.coords.latitude,
        longitude: pos.coords.longitude,
        accuracy: pos.coords.accuracy,
        locatedAt: pos.timestamp || Date.now(),
      }),
      (err) => reject(new Error(err.code === 1 ? 'denied' : err.code === 3 ? 'timeout' : 'unavailable')),
      { enableHighAccuracy: false, timeout, maximumAge: 60000 });
  });
}
