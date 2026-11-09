import { getLang } from './i18n/index.js';
import { getToken, clearSession } from './auth.js';
import { getScope } from './scope.js';

export class ApiError extends Error {
  constructor(code, message, details = [], status = 0) {
    super(message || code);
    this.name = 'ApiError';
    this.code = code;
    this.details = Array.isArray(details) ? details : [];
    this.status = status;
  }
}

function qs(params) {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params || {})) {
    if (v == null || v === '' || (Array.isArray(v) && !v.length)) continue;
    sp.set(k, Array.isArray(v) ? v.join(',') : String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : '';
}

async function request(method, path, { query, json, form } = {}) {
  const headers = { Accept: 'application/json', 'Accept-Language': getLang() };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  let body;
  if (form) {
    body = form;
  } else if (json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(json);
  }
  let res;
  try {
    res = await fetch(`/api${path}${qs(query)}`, { method, headers, body });
  } catch {
    throw new ApiError('NETWORK_ERROR', '');
  }
  if (res.status === 204) return null;
  let data = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch { /* non-JSON body */ }
  }
  if (res.status === 401 && token && !path.startsWith('/auth/')) {
    clearSession();
    if (!location.hash.startsWith('#/login')) location.hash = '#/login';
  }
  if (!res.ok) {
    const fallback = res.status === 413 ? 'PAYLOAD_TOO_LARGE' : res.status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN';
    throw new ApiError(data?.code || fallback, data?.message, data?.details, res.status);
  }
  return data;
}

const get = (path, query) => request('GET', path, { query });
const send = (method, path, json) => request(method, path, { json });
const scoped = (path, q) => get(path, { ...q, scope: getScope() });

export const meta = () => get('/meta');

export const listMerchants = (q) => get('/merchants', q);
export const getMerchant = (id) => get(`/merchants/${id}`);
export const createMerchant = (m) => send('POST', '/merchants', m);
export const updateMerchant = (id, m) => send('PUT', `/merchants/${id}`, m);

export const listProducts = (q) => get('/products', q);
export const getProduct = (id) => get(`/products/${id}`);
export const createProduct = (p) => send('POST', '/products', p);
export const updateProduct = (id, p) => send('PUT', `/products/${id}`, p);
export const productBrands = (q) => get('/products/brands', { q });
export const productTags = (q) => get('/products/tags', { q });

export function recognize(blob) {
  const form = new FormData();
  form.append('image', blob, 'receipt.jpg');
  return request('POST', '/receipts/recognize', { form });
}
export const listReceipts = (q) => scoped('/receipts', q);
export const getReceipt = (id) => get(`/receipts/${id}`);
export const createReceipt = (r) => send('POST', '/receipts', r);
export const updateReceipt = (id, r) => send('PUT', `/receipts/${id}`, r);
export const deleteReceipt = (id) => send('DELETE', `/receipts/${id}`);

export const facets = (q) => scoped('/analytics/facets', q);
export const priceTrend = (q) => scoped('/analytics/price-trend', q);
export const priceCompare = (q) => scoped('/analytics/price-compare', q);
export const spending = (q) => scoped('/analytics/spending', q);
export const priceTable = (q) => scoped('/analytics/price-table', q);

export const authProviders = () => get('/auth/providers');
export const sendSms = (phone) => send('POST', '/auth/sms/send', { phone });
export const smsLogin = (phone, code, location) => send('POST', '/auth/sms/login', { phone, code, location });
export const exchangeTicket = (ticket, location) => send('POST', '/auth/ticket', { ticket, location });
export const oauthAuthorize = (provider, mockUser) => get(`/auth/oauth/${provider}/authorize`, { mockUser });

export const getMe = () => get('/me');
export const updateMe = (p) => send('PUT', '/me', p);
export const myAccounts = () => get('/me/accounts');
export const myLocations = (limit) => get('/me/locations', { limit });
export const logout = () => send('POST', '/me/logout');

export const myFamily = () => get('/family');
export const createFamily = (name) => send('POST', '/family', { name });
export const joinFamily = (code) => send('POST', '/family/join', { code });
export const leaveFamily = () => send('POST', '/family/leave');
export const dissolveFamily = () => send('DELETE', '/family');
export const removeFamilyMember = (userId) => send('DELETE', `/family/members/${userId}`);
export const resetInviteCode = () => send('POST', '/family/invite-code/reset');

// <img> cannot send the Authorization header, so protected images are fetched and shown through a blob URL.
export async function fetchBlobUrl(url) {
  const token = getToken();
  let res;
  try {
    res = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  } catch {
    throw new ApiError('NETWORK_ERROR', '');
  }
  if (!res.ok) throw new ApiError(res.status === 404 ? 'NOT_FOUND' : 'UNKNOWN', '', [], res.status);
  return URL.createObjectURL(await res.blob());
}
