import { h } from './dom.js';
import { icon } from './icons.js';
import { t, tk } from '../i18n/index.js';

const TABS = [
  { name: 'home', href: '#/home', label: tk('tab.home'), icon: 'home' },
  { name: 'analytics', href: '#/analytics', label: tk('tab.analytics'), icon: 'chart' },
  { name: 'scan', href: '#/scan', label: tk('tab.scan'), icon: 'plus', add: true },
  { name: 'library', href: '#/library', label: tk('tab.library'), icon: 'book' },
  { name: 'me', href: '#/profile', label: tk('tab.me'), icon: 'user' },
];

export function createTabbar() {
  const el = h('nav', { class: 'tabbar' });
  let active = '';

  function paint() {
    el.replaceChildren(...TABS.map((tab) => h('a', {
      class: `tab${tab.add ? ' tab-add' : ''}${tab.name === active ? ' active' : ''}`,
      href: tab.href,
      'aria-label': t(tab.label),
      'aria-current': tab.name === active ? 'page' : null,
    }, h('span', { class: 'tab-icon' }, icon(tab.icon, tab.add ? 28 : 24)),
    tab.add ? null : h('span', { class: 'tab-label' }, t(tab.label)))));
  }

  paint();
  return {
    el,
    setActive(name) { active = name; paint(); },
    refresh: paint,
  };
}
