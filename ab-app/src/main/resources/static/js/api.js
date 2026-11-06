import { getLang } from './i18n/index.js';

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
  if (!res.ok) {
    const fallback = res.status === 413 ? 'PAYLOAD_TOO_LARGE' : res.status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN';
    throw new ApiError(data?.code || fallback, data?.message, data?.details, res.status);
  }
  return data;
}

const get = (path, query) => request('GET', path, { query });
const send = (method, path, json) => request(method, path, { json });

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
export const listReceipts = (q) => get('/receipts', q);
export const getReceipt = (id) => get(`/receipts/${id}`);
export const createReceipt = (r) => send('POST', '/receipts', r);
export const updateReceipt = (id, r) => send('PUT', `/receipts/${id}`, r);
export const deleteReceipt = (id) => send('DELETE', `/receipts/${id}`);

export const facets = () => get('/analytics/facets');
export const priceTrend = (q) => get('/analytics/price-trend', q);
export const priceCompare = (q) => get('/analytics/price-compare', q);
export const spending = (q) => get('/analytics/spending', q);
export const priceTable = (q) => get('/analytics/price-table', q);
