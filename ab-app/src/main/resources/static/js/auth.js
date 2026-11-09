const TOKEN_KEY = 'ab.token';
const USER_KEY = 'ab.user';

function read(key) {
  try { return localStorage.getItem(key); } catch { return null; }
}

function write(key, value) {
  try {
    if (value == null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch { /* storage unavailable */ }
}

export const getToken = () => read(TOKEN_KEY);
export const isLoggedIn = () => Boolean(getToken());

export function getUser() {
  try { return JSON.parse(read(USER_KEY) || 'null'); } catch { return null; }
}

export function setUser(user) {
  write(USER_KEY, user ? JSON.stringify(user) : null);
}

export function setSession(login) {
  write(TOKEN_KEY, login.token);
  setUser(login.user);
}

export function clearSession() {
  write(TOKEN_KEY, null);
  write(USER_KEY, null);
}
