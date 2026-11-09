import { h } from '../ui/dom.js';
import { pageHeader, sectionTitle } from '../ui/layout.js';
import { makeField, textInput, selectEl } from '../ui/form.js';
import { icon } from '../ui/icons.js';
import { COUNTRIES } from '../countries.js';
import { toast } from '../ui/toast.js';
import { confirmDialog } from '../ui/modal.js';
import { clearSession, setUser } from '../auth.js';
import { setFamily, getFamily } from '../scope.js';
import * as api from '../api.js';
import { t, tOr, errorText } from '../i18n/index.js';
import { fmtDateTime, countryName, mapUrl } from '../format.js';

const GENDERS = ['UNKNOWN', 'MALE', 'FEMALE', 'OTHER'];

const initial = (name) => (name || '?').trim().slice(0, 1).toUpperCase() || '?';

export async function render(ctx) {
  const { bound, error } = ctx.query;
  if (bound || error) history.replaceState(null, '', '#/profile');
  if (bound) toast(t('profile.bound', { name: tOr(`provider.${bound}`, bound) }), 'success');
  if (error) toast(tOr(`login.err.${error}`, t('login.err.failed')), 'error');

  const [me, accounts, locations, cfg] = await Promise.all([
    api.getMe(), api.myAccounts(), api.myLocations(5), api.authProviders().catch(() => ({ providers: [] })),
  ]);

  const nickname = textInput({ value: me.nickname || '', maxlength: 40 });
  const gender = selectEl(GENDERS.map((g) => ({ value: g, label: t(`gender.${g}`) })), me.gender || 'UNKNOWN', () => {});
  const birth = textInput({ type: 'date', value: me.birthDate || '' });
  const countryOptions = COUNTRIES.map((c) => ({ value: c.code, label: countryName(c.code) }));
  const country = selectEl(countryOptions, me.country || '', () => {}, { blank: t('profile.countryNone') });
  const address = textInput({ value: me.address || '', maxlength: 200 });
  const fNick = makeField(t('profile.nickname'), nickname, { required: true });
  const fCountry = makeField(t('profile.country'), country);
  const fBirth = makeField(t('profile.birthDate'), birth);

  async function save() {
    [fNick, fCountry, fBirth].forEach((f) => f.setError(''));
    try {
      const u = await api.updateMe({
        nickname: nickname.value, gender: gender.value, address: address.value,
        birthDate: birth.value || null, country: country.value || null,
      });
      setUser(u);
      head.nick.textContent = u.nickname;
      head.avatar.textContent = initial(u.nickname);
      toast(t('common.saved'), 'success', 1200);
    } catch (e) {
      const field = { nickname: fNick, country: fCountry, birthDate: fBirth }[e.details?.[0]?.field];
      if (field) field.setError(errorText(e));
      toast(errorText(e), 'error');
    }
  }

  async function logout() {
    if (!await confirmDialog({ title: t('profile.logoutTitle'), message: t('profile.logoutMessage'), confirmText: t('profile.logout'), danger: true })) return;
    try { await api.logout(); } catch { /* the session is dropped locally regardless */ }
    clearSession();
    setFamily(null);
    location.hash = '#/login';
  }

  async function deleteAccount() {
    if (!await confirmDialog({ title: t('profile.deleteTitle'), message: t('profile.deleteMessage'), confirmText: t('profile.deleteConfirm'), danger: true })) return;
    try {
      await api.deleteAccount();
    } catch (e) {
      toast(errorText(e), 'error');
      return;
    }
    clearSession();
    setFamily(null);
    location.hash = '#/login';
    toast(t('profile.deleted'), 'success');
  }

  async function bind(id, mock) {
    try {
      const { url } = await api.oauthAuthorize(id, mock ? 'demo' : undefined);
      window.location.href = url;
    } catch (e) {
      toast(errorText(e), 'error');
    }
  }

  const head = {
    avatar: h('div', { class: 'avatar', 'aria-hidden': 'true' }, initial(me.nickname)),
    nick: h('div', { class: 'me-nick' }, me.nickname),
  };
  const nav = (href, label, value) => h('a', { class: 'link-row', href },
    h('span', null, label), h('span', { class: 'muted' }, value || ''), icon('chevronRight', 18));

  const linked = new Map(accounts.map((a) => [a.provider.toLowerCase(), a]));
  const providerRows = [
    h('div', { class: 'kv' }, h('span', null, t('provider.phone')), h('span', null, me.phone || t('profile.notBound'))),
    ...(cfg.providers || []).map((p) => {
      const a = linked.get(p.id);
      return h('div', { class: 'kv' },
        h('span', null, tOr(`provider.${p.id}`, p.id)),
        a ? h('span', { class: 'muted' }, a.nickname || a.email || t('profile.bindDone'))
          : h('button', { class: 'btn small', type: 'button', onClick: () => bind(p.id, p.mock) }, t('profile.bind')));
    }),
  ];

  const locRows = locations.length
    ? locations.map((l) => h('div', { class: 'kv' },
      h('a', { href: mapUrl(l.latitude, l.longitude), target: '_blank', rel: 'noopener' },
        `${l.latitude.toFixed(4)}, ${l.longitude.toFixed(4)}`),
      h('span', { class: 'muted' }, fmtDateTime(new Date(l.locatedAt).toISOString().slice(0, 19)))))
    : [h('p', { class: 'muted' }, t('profile.noLocations'))];

  return h('div', { class: 'page' },
    pageHeader({ title: t('me.title') }),
    h('div', { class: 'page-body' },
      h('section', { class: 'card me-card' },
        head.avatar,
        h('div', { class: 'me-meta' }, head.nick,
          h('div', { class: 'muted' }, me.phone || ''),
          h('div', { class: 'muted' }, `${t('me.userId')}: ${me.id}`))),
      h('section', { class: 'card list' },
        nav('#/family', t('settings.family'), getFamily()?.name),
        nav('#/settings', t('settings.title'))),
      h('section', { class: 'card' },
        sectionTitle(t('profile.title')),
        fNick,
        makeField(t('profile.gender'), gender),
        fBirth,
        fCountry,
        makeField(t('profile.address'), address),
        h('button', { class: 'btn primary block', type: 'button', onClick: save }, t('common.save'))),
      h('section', { class: 'card' }, sectionTitle(t('profile.accounts')), providerRows),
      h('section', { class: 'card' }, sectionTitle(t('profile.locations')), locRows),
      h('section', { class: 'card' },
        h('button', { class: 'btn block', type: 'button', onClick: logout }, t('profile.logout')),
        h('button', { class: 'btn danger-outline block', type: 'button', onClick: deleteAccount }, t('profile.deleteAccount')),
        h('small', { class: 'field-hint' }, t('profile.deleteHint')))));
}
