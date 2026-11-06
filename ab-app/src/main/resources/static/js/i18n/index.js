import zhCN from './zh-CN.js';
import ja from './ja.js';
import en from './en.js';

export const LANGS = [
  { code: 'zh-CN', name: '简体中文' },
  { code: 'ja', name: '日本語' },
  { code: 'en', name: 'English' },
];

const dicts = { 'zh-CN': zhCN, ja, en };
const LOCALES = { 'zh-CN': 'zh-CN', ja: 'ja-JP', en: 'en' };
const STORAGE_KEY = 'ab.lang';
const listeners = new Set();

function detect() {
  const n = (navigator.language || 'en').toLowerCase();
  if (n.startsWith('zh')) return 'zh-CN';
  if (n.startsWith('ja')) return 'ja';
  return 'en';
}

function initial() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && dicts[saved]) return saved;
  } catch { /* storage unavailable */ }
  return detect();
}

let lang = initial();

export const getLang = () => lang;
export const getLocale = () => LOCALES[lang];

export function setLang(code) {
  if (!dicts[code] || code === lang) return;
  lang = code;
  try { localStorage.setItem(STORAGE_KEY, code); } catch { /* ignore */ }
  listeners.forEach((fn) => fn(code));
}

export function onLangChange(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function hasKey(key) {
  return key in dicts[lang] || key in dicts['zh-CN'];
}

export function t(key, params) {
  const s = dicts[lang][key] ?? dicts['zh-CN'][key] ?? key;
  return params ? s.replace(/\{(\w+)\}/g, (m, k) => (params[k] ?? m)) : s;
}

// marks a static key stored in a data table; translated later via t(key)
export const tk = (key) => key;

export function tOr(key, fallback) {
  return hasKey(key) ? t(key) : fallback;
}

export function errorText(err) {
  if (err && err.code && hasKey(`error.${err.code}`)) return t(`error.${err.code}`);
  return (err && err.message) || t('error.UNKNOWN');
}
