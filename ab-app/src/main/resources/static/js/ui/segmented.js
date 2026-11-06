import { h } from './dom.js';

export function segmented(options, value, onChange) {
  const el = h('div', { class: 'segmented', role: 'tablist' });
  function paint() {
    el.replaceChildren(...options.map((o) => h('button', {
      type: 'button', role: 'tab', class: o.value === value ? 'on' : '', 'aria-selected': String(o.value === value),
      onClick: () => { if (o.value === value) return; value = o.value; paint(); onChange(value); },
    }, o.label)));
  }
  paint();
  return el;
}
