const routes = [];

export function addRoute(pattern, load, opts = {}) {
  const keys = [];
  const re = new RegExp(`^${pattern.replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)'; })}$`);
  routes.push({ re, keys, load, ...opts });
}

export function parseHash() {
  const raw = location.hash.slice(1) || '/home';
  const i = raw.indexOf('?');
  const path = i < 0 ? raw : raw.slice(0, i);
  const query = Object.fromEntries(new URLSearchParams(i < 0 ? '' : raw.slice(i + 1)));
  return { path, query };
}

export function matchRoute(path) {
  for (const route of routes) {
    const m = route.re.exec(path);
    if (m) {
      const params = {};
      route.keys.forEach((k, i) => { params[k] = decodeURIComponent(m[i + 1]); });
      return { route, params };
    }
  }
  return null;
}

export function buildHash(path, query) {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(query || {})) {
    if (v != null && v !== '') sp.set(k, v);
  }
  const s = sp.toString();
  return `#${path}${s ? `?${s}` : ''}`;
}

const norm = (to) => (to.startsWith('#') ? to : `#${to}`);
export const navigate = (to) => { location.hash = norm(to); };
export const replace = (to) => { location.replace(norm(to)); };

export function start(onChange) {
  window.addEventListener('hashchange', () => onChange(false));
  onChange(false);
}
