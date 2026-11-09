import { h, clear } from '../ui/dom.js';
import { makeField, textInput, selectEl } from '../ui/form.js';
import { toast } from '../ui/toast.js';
import { getLocation } from '../ui/geo.js';
import * as api from '../api.js';
import { setSession } from '../auth.js';
import { setFamily } from '../scope.js';
import { t, tOr, errorText } from '../i18n/index.js';
import { replace } from '../router.js';
import { countryName } from '../format.js';
import { COUNTRIES, DEFAULT_COUNTRY, countryAt, toInternational } from '../countries.js';

const CC_KEY = 'ab.cc';
const LOCATE_TIMEOUT_MS = 4000;
const SUBMIT_WAIT_MS = 1500;

function storedCountry() {
  try { return localStorage.getItem(CC_KEY); } catch { return null; }
}
function rememberCountry(code) {
  try { localStorage.setItem(CC_KEY, code); } catch { /* storage unavailable */ }
}

const PROVIDERS = {
  apple: { letter: 'A', cls: 'apple' },
  google: { letter: 'G', cls: 'google' },
  wechat: { letter: 'W', cls: 'wechat' },
  qq: { letter: 'Q', cls: 'qq' },
  line: { letter: 'L', cls: 'line' },
};

async function finish(login) {
  setSession(login);
  try { setFamily(await api.myFamily()); } catch { setFamily(null); }
  replace('#/home');
}

export async function render(ctx) {
  const { ticket, error } = ctx.query;
  // the ticket is single use and must not linger in the address bar or history
  if (ticket || error) history.replaceState(null, '', '#/login');

  let cfg = { sms: true, providers: [], requireLocation: false };
  try { cfg = { ...cfg, ...(await api.authProviders()) }; } catch { /* offline: keep defaults */ }

  let location = null;
  let locPromise = null;
  let cc = storedCountry() || DEFAULT_COUNTRY;
  let ccLocked = Boolean(storedCountry()); // a remembered or manual choice is never overridden by the location guess
  const locText = h('span', { class: 'loc-text' });
  const locRetry = h('button', { class: 'link-btn', type: 'button', hidden: true, onClick: () => locate() }, t('login.locateRetry'));
  const locBar = h('div', { class: 'loc-bar' }, locText, locRetry);
  const ccSelect = selectEl(COUNTRIES.map((c) => ({ value: c.code, label: `${countryName(c.code)} +${c.dial}` })), cc, (v) => {
    cc = v;
    ccLocked = true;
    rememberCountry(v);
  });
  ccSelect.classList.add('cc-select');

  function locate() {
    locRetry.hidden = true;
    locText.textContent = t('login.locating');
    locBar.className = 'loc-bar';
    locPromise = getLocation({ timeout: LOCATE_TIMEOUT_MS }).then((l) => {
      location = l;
      locText.textContent = t('login.located');
      locBar.className = 'loc-bar ok';
      const guess = countryAt(l.latitude, l.longitude);
      if (guess && !ccLocked) {
        cc = guess;
        ccSelect.value = guess;
      }
      return l;
    }).catch((e) => {
      location = null;
      locText.textContent = t('login.locSkipped', { reason: tOr(`login.loc.${e.message}`, t('login.loc.unavailable')) });
      locRetry.hidden = false;
      return null;
    });
    return locPromise;
  }

  // The position is best effort: wait briefly for a pending fix, then go on without it unless the server demands it.
  async function needLocation() {
    if (!location) {
      const pending = locPromise || locate();
      await Promise.race([pending, new Promise((r) => setTimeout(r, cfg.requireLocation ? LOCATE_TIMEOUT_MS : SUBMIT_WAIT_MS))]);
    }
    if (!location && cfg.requireLocation) throw Object.assign(new Error('loc'), { locationRequired: true });
    return location;
  }

  const errBox = h('div', { class: 'login-error', role: 'alert' });
  const fail = (e) => {
    errBox.textContent = e?.locationRequired ? t('login.locationRequired') : errorText(e);
  };
  if (error) errBox.textContent = tOr(`login.err.${error}`, t('login.err.failed'));

  // phone + code
  const phone = textInput({ type: 'tel', inputmode: 'tel', placeholder: t('login.phonePlaceholder'), autocomplete: 'tel-national' });
  const code = textInput({ inputmode: 'numeric', maxlength: 8, placeholder: t('login.codePlaceholder'), autocomplete: 'one-time-code' });
  const testCode = cfg.testCode;
  const hint = h('small', { class: 'field-hint' }, testCode ? t('login.devCode', { code: testCode }) : '');
  const fullPhone = () => toInternational(cc, phone.value.trim());
  let timer = null;
  const sendBtn = h('button', { class: 'btn small', type: 'button', onClick: () => sendCode() }, t('login.sendCode'));
  const loginBtn = h('button', { class: 'btn primary block', type: 'button', onClick: () => submit() }, t('login.submit'));
  ctx.onCleanup(() => clearInterval(timer));

  function countdown(sec) {
    clearInterval(timer);
    let left = sec;
    const tick = () => {
      sendBtn.disabled = left > 0;
      sendBtn.textContent = left > 0 ? t('login.resendIn', { n: left }) : t('login.sendCode');
      if (left <= 0) clearInterval(timer);
      left -= 1;
    };
    tick();
    timer = setInterval(tick, 1000);
  }

  async function sendCode() {
    errBox.textContent = '';
    if (!phone.value.trim()) { phone.focus(); return; }
    sendBtn.disabled = true;
    try {
      rememberCountry(cc);
      const r = await api.sendSms(fullPhone());
      countdown(r.resendSeconds || 60);
      if (r.debugCode) hint.textContent = t('login.devCode', { code: r.debugCode });
      code.focus();
    } catch (e) {
      sendBtn.disabled = false;
      fail(e);
    }
  }

  async function submit() {
    errBox.textContent = '';
    if (!phone.value.trim() || !code.value.trim()) return;
    loginBtn.disabled = true;
    try {
      const loc = await needLocation();
      rememberCountry(cc);
      await finish(await api.smsLogin(fullPhone(), code.value.trim(), loc));
    } catch (e) {
      loginBtn.disabled = false;
      fail(e);
    }
  }

  async function startOAuth(id, mock) {
    errBox.textContent = '';
    try {
      const { url } = await api.oauthAuthorize(id, mock ? 'demo' : undefined);
      window.location.href = url;
    } catch (e) {
      fail(e);
    }
  }

  const providerBtns = (cfg.providers || []).map((p) => {
    const meta = PROVIDERS[p.id] || { letter: p.id[0].toUpperCase(), cls: '' };
    return h('button', { class: `provider-btn ${meta.cls}`, type: 'button', onClick: () => startOAuth(p.id, p.mock) },
      h('span', { class: 'provider-badge' }, meta.letter),
      h('span', null, tOr(`provider.${p.id}`, p.id)),
      p.mock ? h('em', { class: 'mock-tag' }, t('login.demo')) : null);
  });

  const root = h('div', { class: 'page login' },
    h('div', { class: 'login-head' },
      h('img', { class: 'login-logo', src: '/favicon.svg', alt: '' }),
      h('h1', null, t('app.title')),
      h('p', null, t('login.subtitle'))),
    h('div', { class: 'page-body' },
      locBar,
      errBox,
      cfg.sms ? h('section', { class: 'card' },
        makeField(t('login.phone'), h('div', { class: 'phone-row' }, ccSelect, phone)),
        makeField(t('login.code'), h('div', { class: 'code-row' }, code, sendBtn), { hint: null }),
        hint,
        loginBtn) : null,
      providerBtns.length ? h('section', { class: 'card' },
        h('h3', null, t('login.orContinue')),
        h('div', { class: 'provider-list' }, providerBtns)) : null,
      h('p', { class: 'login-note' }, t('login.locationNote'))));

  locate();

  if (ticket) {
    clear(root.querySelector('.page-body')).append(h('div', { class: 'empty' }, h('h3', null, t('login.signingIn')), locBar, errBox));
    (async () => {
      try {
        const loc = await needLocation();
        await finish(await api.exchangeTicket(ticket, loc));
      } catch (e) {
        fail(e);
        const body = root.querySelector('.page-body');
        body.append(h('a', { class: 'btn', href: '#/login' }, t('login.backToLogin')));
        toast(e?.locationRequired ? t('login.locationRequired') : errorText(e), 'error');
      }
    })();
  }

  return root;
}
