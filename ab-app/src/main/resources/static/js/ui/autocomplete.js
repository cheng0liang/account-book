import { h, clear } from './dom.js';

// input must live inside a positioned wrapper; the list is appended to that wrapper
export function autocomplete(input, { fetch, toItem, onSelect, minChars = 1, delay = 200 }) {
  const list = h('ul', { class: 'ac-list', role: 'listbox', hidden: true });
  const attach = () => {
    if (list.isConnected) return;
    const wrap = input.closest('.ac-wrap') || input.parentElement;
    if (!wrap) return;
    wrap.classList.add('ac-wrap');
    wrap.append(list);
  };
  let seq = 0;
  let timer;
  let results = [];
  let active = -1;

  const close = () => { list.hidden = true; active = -1; };

  function paint() {
    attach();
    clear(list);
    results.forEach((r, i) => {
      const it = toItem(r);
      list.append(h('li', {
        class: 'ac-item' + (i === active ? ' active' : ''),
        role: 'option',
        onMousedown: (e) => e.preventDefault(),
        onClick: () => { close(); onSelect(r); },
      }, h('span', null, it.label), it.sub ? h('small', null, it.sub) : null));
    });
    list.hidden = results.length === 0;
  }

  async function run() {
    const q = input.value.trim();
    if (input.readOnly || q.length < minChars) { close(); return; }
    const my = ++seq;
    try {
      const res = await fetch(q);
      if (my !== seq || document.activeElement !== input) return;
      results = res || [];
      active = -1;
      paint();
    } catch { close(); }
  }

  const schedule = () => { clearTimeout(timer); timer = setTimeout(run, delay); };
  input.addEventListener('input', schedule);
  input.addEventListener('focus', schedule);
  input.addEventListener('blur', () => { seq++; setTimeout(close, 120); });
  input.addEventListener('keydown', (e) => {
    if (list.hidden) return;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      active = (active + (e.key === 'ArrowDown' ? 1 : -1) + results.length) % results.length;
      paint();
    } else if (e.key === 'Enter' && active >= 0) {
      e.preventDefault();
      const r = results[active];
      close();
      onSelect(r);
    } else if (e.key === 'Escape') {
      close();
    }
  });
  return { close };
}
