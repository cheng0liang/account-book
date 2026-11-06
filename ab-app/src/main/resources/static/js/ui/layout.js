import { h } from './dom.js';
import { icon } from './icons.js';
import { t, errorText } from '../i18n/index.js';

export function pageHeader({ title, back, right }) {
  return h('header', { class: 'page-header' },
    back
      ? h('a', { class: 'icon-btn', href: back, 'aria-label': t('common.back') }, icon('chevronLeft'))
      : h('span', { class: 'icon-btn spacer' }),
    h('h1', { class: 'page-title' }, title),
    h('div', { class: 'page-actions' }, right));
}

export function emptyState({ iconName = 'receipt', title, text, action }) {
  return h('div', { class: 'empty' },
    h('div', { class: 'empty-icon' }, icon(iconName, 40)),
    h('h3', null, title),
    text ? h('p', null, text) : null,
    action);
}

export function skeleton(rows = 3) {
  return h('div', { class: 'skeleton-list', 'aria-busy': 'true' },
    Array.from({ length: rows }, () => h('div', { class: 'skeleton' })));
}

export function errorBlock(err, retry) {
  return h('div', { class: 'empty error' },
    h('h3', null, errorText(err)),
    retry ? h('button', { class: 'btn', type: 'button', onClick: retry }, t('common.retry')) : null);
}

export function sectionTitle(text, right) {
  return h('div', { class: 'section-title' }, h('h2', null, text), right);
}
