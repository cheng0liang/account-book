import { h } from '../ui/dom.js';
import { pageHeader } from '../ui/layout.js';
import { selectEl } from '../ui/form.js';
import { toast } from '../ui/toast.js';
import { icon } from '../ui/icons.js';
import { getUser } from '../auth.js';
import { getFamily } from '../scope.js';
import { t, LANGS, getLang, setLang } from '../i18n/index.js';
import { getMeta, getCurrency, setCurrency } from '../store.js';

export function render() {
  const meta = getMeta();
  const cur = getCurrency();
  const currencies = [...new Set([cur, ...meta.currencies])].map((c) => ({ value: c, label: c }));

  const langs = LANGS.map((l) => h('label', { class: 'radio-row' },
    h('input', { type: 'radio', name: 'lang', value: l.code, checked: l.code === getLang(), onChange: () => setLang(l.code) }),
    h('span', null, l.name)));

  return h('div', { class: 'page' },
    pageHeader({ title: t('settings.title') }),
    h('div', { class: 'page-body' },
      h('section', { class: 'card list' },
        h('h3', { class: 'list-title' }, t('settings.account')),
        h('a', { class: 'link-row', href: '#/profile' },
          h('span', null, t('settings.profile')),
          h('span', { class: 'muted' }, getUser()?.nickname || ''), icon('chevronRight', 18)),
        h('a', { class: 'link-row', href: '#/family' },
          h('span', null, t('settings.family')),
          h('span', { class: 'muted' }, getFamily()?.name || ''), icon('chevronRight', 18))),
      h('section', { class: 'card' }, h('h3', null, t('settings.language')), langs),
      h('section', { class: 'card' },
        h('h3', null, t('settings.currency')),
        selectEl(currencies, cur, (v) => { setCurrency(v); toast(t('common.saved'), 'success', 1200); }),
        h('small', { class: 'field-hint' }, t('settings.currencyHint'))),
      h('section', { class: 'card' },
        h('h3', null, t('settings.about')),
        h('div', { class: 'kv' }, h('span', null, t('settings.version')), h('span', null, meta.version || '–')),
        h('div', { class: 'kv' }, h('span', null, t('settings.ocrProvider')), h('span', null, meta.ocrProvider || '–')))));
}
