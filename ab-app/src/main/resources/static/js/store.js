import * as api from './api.js';

const FALLBACK_META = {
  version: '',
  defaultCurrency: 'JPY',
  ocrProvider: '',
  currencies: ['JPY', 'CNY', 'TWD', 'USD', 'EUR'],
  merchantTypes: ['SUPERMARKET', 'CONVENIENCE', 'RESTAURANT', 'CAFE', 'DRUGSTORE', 'DEPARTMENT', 'ELECTRONICS', 'CLOTHING', 'ONLINE', 'GAS', 'OTHER'],
  productCategories: ['FOOD', 'BEVERAGE', 'FRESH', 'DAILY', 'HOUSEHOLD', 'MEDICINE', 'BEAUTY', 'CLOTHING', 'ELECTRONICS', 'OTHER'],
  paymentMethods: ['CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'E_MONEY', 'QR_PAY', 'OTHER'],
  specUnits: ['g', 'kg', 'ml', 'l', 'pcs'],
};

let meta = FALLBACK_META;

export async function loadMeta() {
  try {
    meta = { ...FALLBACK_META, ...(await api.meta()) };
  } catch { /* keep fallback */ }
  return meta;
}

export const getMeta = () => meta;

const CURRENCY_KEY = 'ab.currency';

export function getCurrency() {
  try {
    return localStorage.getItem(CURRENCY_KEY) || meta.defaultCurrency;
  } catch {
    return meta.defaultCurrency;
  }
}

export function setCurrency(code) {
  try { localStorage.setItem(CURRENCY_KEY, code); } catch { /* ignore */ }
}

let recognition = null;
export const setRecognition = (r) => { recognition = r; };
export function takeRecognition() {
  const r = recognition;
  recognition = null;
  return r;
}
