#!/usr/bin/env node
// Usage: node ab-app/src/test/frontend/i18n-check.mjs   (Node >= 18, built-in modules only)
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';

const here = dirname(fileURLToPath(import.meta.url));
const staticDir = join(here, '..', '..', 'main', 'resources', 'static');
const jsDir = join(staticDir, 'js');
const LANGS = ['zh-CN', 'ja', 'en'];

// enums from specs/001-receipt-price-tracker/contracts/api.md
const ENUMS = {
  merchantType: ['SUPERMARKET', 'CONVENIENCE', 'RESTAURANT', 'CAFE', 'DRUGSTORE', 'DEPARTMENT', 'ELECTRONICS', 'CLOTHING', 'ONLINE', 'GAS', 'OTHER'],
  category: ['FOOD', 'BEVERAGE', 'FRESH', 'DAILY', 'HOUSEHOLD', 'MEDICINE', 'BEAUTY', 'CLOTHING', 'ELECTRONICS', 'OTHER'],
  payment: ['CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'E_MONEY', 'QR_PAY', 'OTHER'],
  unit: ['g', 'kg', 'ml', 'l', 'pcs'],
  error: ['VALIDATION_FAILED', 'NOT_FOUND', 'CONFLICT', 'OCR_FAILED', 'PAYLOAD_TOO_LARGE', 'INTERNAL_ERROR', 'NETWORK_ERROR', 'UNKNOWN'],
  priceUnit: ['item', '100g', '100ml'],
  warning: ['merchant_not_matched'],
  metric: ['avg', 'min', 'max'],
};

const problems = [];
const fail = (msg) => problems.push(msg);

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    return statSync(p).isDirectory() ? walk(p) : [p];
  });
}

// 1. dictionaries: identical key sets, no empty values
const dicts = {};
for (const lang of LANGS) {
  dicts[lang] = (await import(pathToFileURL(join(jsDir, 'i18n', `${lang}.js`)).href)).default;
}
const base = dicts['zh-CN'];
const baseKeys = new Set(Object.keys(base));
for (const lang of LANGS) {
  const keys = new Set(Object.keys(dicts[lang]));
  for (const k of baseKeys) if (!keys.has(k)) fail(`[${lang}] missing key: ${k}`);
  for (const k of keys) if (!baseKeys.has(k)) fail(`[${lang}] extra key not in zh-CN: ${k}`);
  for (const [k, v] of Object.entries(dicts[lang])) {
    if (typeof v !== 'string' || v.trim() === '') fail(`[${lang}] empty value: ${k}`);
  }
}
for (const lang of LANGS.slice(1)) {
  for (const [k, v] of Object.entries(dicts[lang])) {
    const a = [...String(base[k] ?? '').matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join();
    const b = [...String(v).matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join();
    if (a !== b) fail(`[${lang}] placeholder mismatch: ${k}`);
  }
}

// 2. static keys: t('key' / t("key" / tk('key'
const files = walk(jsDir).filter((f) => f.endsWith('.js'));
const callRe = /(?<![\w$.])(?:t|tk|tOr)\(\s*(['"])([A-Za-z][\w.-]*)\1\s*[,)]/g;
for (const file of files) {
  const src = readFileSync(file, 'utf8');
  for (const m of src.matchAll(callRe)) {
    if (!baseKeys.has(m[2])) fail(`${relative(staticDir, file)}: key not in zh-CN: ${m[2]}`);
  }
}

// 3. dynamic key families
for (const [family, values] of Object.entries(ENUMS)) {
  for (const v of values) {
    const key = `${family}.${v}`;
    if (!baseKeys.has(key)) fail(`dynamic key missing: ${key}`);
  }
}

// 4. syntax check of every JS file as an ES module
for (const file of files) {
  const r = spawnSync(process.execPath, ['--check', '--input-type=module'], { input: readFileSync(file) , encoding: 'utf8' });
  if (r.status !== 0) fail(`syntax error in ${relative(staticDir, file)}:\n${(r.stderr || '').trim()}`);
}

if (problems.length) {
  console.error(`i18n-check FAILED (${problems.length})`);
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log(`i18n-check OK: ${LANGS.length} dictionaries x ${baseKeys.size} keys, ${files.length} JS files syntax-checked`);
