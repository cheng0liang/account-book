import { h, clear } from './dom.js';
import { icon } from './icons.js';
import { autocomplete } from './autocomplete.js';
import { t } from '../i18n/index.js';

export function chipButtons(items, onPick) {
  return h('div', { class: 'chip-row' },
    items.map((it) => h('button', { type: 'button', class: 'chip pick', onClick: () => onPick(it.value) }, it.label)));
}

export function chipInput({ value, suggest, onChange, placeholder, disabled = false }) {
  const tags = value;
  const box = h('div', { class: 'chip-input' + (disabled ? ' disabled' : '') });
  const root = h('div', { class: 'ac-wrap' }, box);
  const input = h('input', { type: 'text', class: 'chip-text', placeholder, disabled, autocomplete: 'off' });

  function add(raw) {
    const v = raw.trim();
    input.value = '';
    if (!v || tags.includes(v)) return;
    tags.push(v);
    paint();
    onChange(tags);
  }

  function paint() {
    clear(box);
    tags.forEach((tag, i) => box.append(h('span', { class: 'chip' }, tag,
      disabled ? null : h('button', {
        type: 'button', 'aria-label': t('common.remove'),
        onClick: () => { tags.splice(i, 1); paint(); onChange(tags); },
      }, icon('x', 14)))));
    box.append(input);
  }

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ',') { e.preventDefault(); add(input.value); }
    else if (e.key === 'Backspace' && !input.value && tags.length) { tags.pop(); paint(); onChange(tags); }
  });
  input.addEventListener('change', () => add(input.value));
  paint();
  if (suggest && !disabled) {
    autocomplete(input, { fetch: suggest, toItem: (s) => ({ label: s }), onSelect: add, minChars: 0 });
  }
  return root;
}
