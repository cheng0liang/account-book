import { h } from '../ui/dom.js';
import { pageHeader, sectionTitle } from '../ui/layout.js';
import { makeField, textInput, selectEl } from '../ui/form.js';
import { toast } from '../ui/toast.js';
import { confirmDialog } from '../ui/modal.js';
import { clearSession, setUser } from '../auth.js';
import { setFamily } from '../scope.js';
import * as api from '../api.js';
import { t, tOr, errorText } from '../i18n/index.js';
import { fmtDateTime, countryName, mapUrl } from '../format.js';

const GENDERS = ['UNKNOWN', 'MALE', 'FEMALE', 'OTHER'];

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
  const country = textInput({ value: me.country || '', maxlength: 2, placeholder: t('profile.countryPlaceholder') });
  const address = textInput({ value: me.address || '', maxlength: 200 });
  const fNick = makeField(t('profile.nickname'), nickname, { required: true });
  const fCountry = makeField(t('profile.country'), country);
  const fBirth = makeField(t('profile.birthDate'), birth);

  async function save() {
    [fNick, fCountry, fBirth].forEach((f) => f.setError(''));
    try {
      const u = await api.updateMe({
        nickname: nickname.value, gender: gender.value, address: address.value,
        birthDate: birth.value || null, country: country.value.trim().toUpperCase() || null,
      });
      setUser(u);
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

  async function bind(id, mock) {
    try {
      const { url } = await api.oauthAuthorize(id, mock ? 'demo' : undefined);
      window.location.href = url;
    } catch (e) {
      toast(errorText(e), 'error');
    }
  }

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
    pageHeader({ title: t('profile.title'), back: '#/settings' }),
    h('div', { class: 'page-body' },
      h('section', { class: 'card' },
        fNick,
        makeField(t('profile.gender'), gender),
        fBirth,
        fCountry,
        country.value ? h('small', { class: 'field-hint' }, countryName(country.value)) : null,
        makeField(t('profile.address'), address),
        h('button', { class: 'btn primary block', type: 'button', onClick: save }, t('common.save'))),
      h('section', { class: 'card' }, sectionTitle(t('profile.accounts')), providerRows),
      h('section', { class: 'card' }, sectionTitle(t('profile.locations')), locRows),
      h('section', { class: 'card' },
        h('button', { class: 'btn danger block', type: 'button', onClick: logout }, t('profile.logout')))));
}
