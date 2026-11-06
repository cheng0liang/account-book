import { getLocale } from './i18n/index.js';

const cache = new Map();

function memo(kind, opts, make) {
  const loc = getLocale();
  const key = `${kind}|${loc}|${JSON.stringify(opts)}`;
  let f = cache.get(key);
  if (!f) { f = make(loc); cache.set(key, f); }
  return f;
}

const nf = (opts) => memo('n', opts, (loc) => new Intl.NumberFormat(loc, opts));
const df = (opts) => memo('d', opts, (loc) => new Intl.DateTimeFormat(loc, opts));

export function currencyDigits(cur) {
  try {
    return new Intl.NumberFormat('en', { style: 'currency', currency: cur }).resolvedOptions().maximumFractionDigits;
  } catch {
    return 2;
  }
}

export function roundMoney(v, cur) {
  const f = 10 ** currencyDigits(cur);
  return Math.round((v + Number.EPSILON) * f) / f;
}

export function fmtNumber(v) {
  return v == null ? '–' : nf({ maximumFractionDigits: 2 }).format(v);
}

export function fmtCompact(v) {
  return nf({ notation: 'compact', maximumFractionDigits: 1 }).format(v);
}

export function fmtPercent(v) {
  return nf({ style: 'percent', maximumFractionDigits: 1 }).format(v);
}

export function fmtMoney(v, cur) {
  if (v == null || Number.isNaN(v)) return '–';
  try {
    return nf({ style: 'currency', currency: cur || 'JPY' }).format(v);
  } catch {
    return `${fmtNumber(v)} ${cur || ''}`.trim();
  }
}

// 'yyyy-MM-dd' or 'yyyy-MM-ddTHH:mm' parsed as local time
export function parseLocal(s) {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/.exec(s || '');
  if (!m) return null;
  return new Date(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0));
}

function fmtWith(s, opts) {
  const d = parseLocal(s);
  return d ? df(opts).format(d) : (s || '');
}

export const fmtDate = (s) => fmtWith(s, { dateStyle: 'medium' });
export const fmtDateTime = (s) => fmtWith(s, { dateStyle: 'medium', timeStyle: 'short' });
export const fmtTime = (s) => fmtWith(s, { hour: '2-digit', minute: '2-digit' });
export const fmtDayHeader = (s) => fmtWith(s, { month: 'long', day: 'numeric', weekday: 'short' });

export function fmtMonthTitle(y, m) {
  return df({ year: 'numeric', month: 'long' }).format(new Date(y, m, 1));
}

// chart axis label: day / month; anything else is returned as-is
export function fmtAxisLabel(l) {
  if (/^\d{4}-\d{2}-\d{2}$/.test(l)) return fmtWith(l, { month: 'numeric', day: 'numeric' });
  const m = /^(\d{4})-(\d{2})$/.exec(l);
  if (m) return df({ year: '2-digit', month: 'numeric' }).format(new Date(+m[1], +m[2] - 1, 1));
  return l;
}

const pad = (n) => String(n).padStart(2, '0');
export const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

export function monthRange(y, m) {
  return { from: ymd(new Date(y, m, 1)), to: ymd(new Date(y, m + 1, 0)) };
}

export function nowLocalInput() {
  const d = new Date();
  return `${ymd(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function countryName(code) {
  if (!code) return '';
  try {
    return memo('c', {}, (loc) => new Intl.DisplayNames([loc], { type: 'region' })).of(code) || code;
  } catch {
    return code;
  }
}

export function mapUrl(lat, lon) {
  return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=17/${lat}/${lon}`;
}
