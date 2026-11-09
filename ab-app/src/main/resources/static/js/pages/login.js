import { h, clear } from '../ui/dom.js';
import { makeField, textInput } from '../ui/form.js';
import { toast } from '../ui/toast.js';
import { getLocation } from '../ui/geo.js';
import * as api from '../api.js';
import { setSession } from '../auth.js';
import { setFamily } from '../scope.js';
import { t, tOr, errorText } from '../i18n/index.js';
import { replace } from '../router.js';

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

  let cfg = { sms: true, providers: [], requireLocation: true };
  try { cfg = { ...cfg, ...(await api.authProviders()) }; } catch { /* offline: keep defaults */ }

  let location = null;
  let locPromise = null;
  const locText = h('span', { class: 'loc-text' });
  const locRetry = h('button', { class: 'link-btn', type: 'button', hidden: true, onClick: () => locate() }, t('login.locateRetry'));
  const locBar = h('div', { class: 'loc-bar' }, locText, locRetry);

  function locate() {
    locRetry.hidden = true;
    locText.textContent = t('login.locating');
    locBar.className = 'loc-bar';
    locPromise = getLocation().then((l) => {
      location = l;
      locText.textContent = t('login.located');
      locBar.className = 'loc-bar ok';
      return l;
    }).catch((e) => {
      location = null;
      locText.textContent = tOr(`login.loc.${e.message}`, t('login.loc.unavailable'));
      locBar.className = 'loc-bar bad';
      locRetry.hidden = false;
      return null;
    });
    return locPromise;
  }

  // Resolves to the location, or null when it is optional and unavailable; throws when it is required.
  async function needLocation() {
    if (!location) await (locPromise || locate());
    if (!location && cfg.requireLocation) throw Object.assign(new Error('loc'), { locationRequired: true });
    return location;
  }

  const errBox = h('div', { class: 'login-error', role: 'alert' });
  const fail = (e) => {
    errBox.textContent = e?.locationRequired ? t('login.locationRequired') : errorText(e);
  };
  if (error) errBox.textContent = tOr(`login.err.${error}`, t('login.err.failed'));

  // phone + code
  const phone = textInput({ type: 'tel', inputmode: 'tel', placeholder: t('login.phonePlaceholder'), autocomplete: 'tel' });
  const code = textInput({ inputmode: 'numeric', maxlength: 8, placeholder: t('login.codePlaceholder'), autocomplete: 'one-time-code' });
  const hint = h('small', { class: 'field-hint' });
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
      const r = await api.sendSms(phone.value.trim());
      countdown(r.resendSeconds || 60);
      if (r.debugCode) {
        code.value = r.debugCode;
        hint.textContent = t('login.devCode', { code: r.debugCode });
      }
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
      await finish(await api.smsLogin(phone.value.trim(), code.value.trim(), loc));
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
        makeField(t('login.phone'), phone),
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
