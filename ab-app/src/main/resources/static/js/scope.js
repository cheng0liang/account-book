// Whose data lists and charts cover: 'me' or the caller's whole 'family'. Kept free of imports so api.js can read it.
const KEY = 'ab.scope';
const listeners = new Set();
let family = null;

export const getFamily = () => family;
export const setFamily = (f) => { family = f || null; };

export function getScope() {
  if (!family) return 'me';
  try { return localStorage.getItem(KEY) === 'family' ? 'family' : 'me'; } catch { return 'me'; }
}

export function setScope(scope) {
  if (scope === getScope()) return;
  try { localStorage.setItem(KEY, scope); } catch { /* ignore */ }
  listeners.forEach((fn) => fn(scope));
}

export function onScopeChange(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}
