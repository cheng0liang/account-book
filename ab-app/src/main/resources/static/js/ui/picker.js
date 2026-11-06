import { h, clear, debounce } from './dom.js';
import { icon } from './icons.js';
import { openSheet } from './modal.js';
import { skeleton } from './layout.js';
import { t } from '../i18n/index.js';

// search(q) -> Promise<raw[]>, toItem(raw) -> {id,label,sub}; resolves {id,label} | [{id,label}] | null
export function openPicker({ title, search, toItem, multi = false, selected = [] }) {
  return new Promise((resolve) => {
    const chosen = new Map(selected.map((s) => [s.id, s]));
    let result = null;
    openSheet({
      title,
      onClose: () => resolve(multi && result === null ? null : result),
      build: (close) => {
        const list = h('ul', { class: 'picker-list' });
        let seq = 0;
        const input = h('input', { class: 'input', type: 'search', placeholder: t('common.search'), autocomplete: 'off' });

        function paint(rows) {
          clear(list);
          if (!rows.length) list.append(h('li', { class: 'picker-empty' }, t('common.noResults')));
          rows.forEach((raw) => {
            const it = toItem(raw);
            const on = chosen.has(it.id);
            list.append(h('li', {
              class: 'picker-item' + (on ? ' on' : ''),
              onClick: () => {
                if (!multi) { result = { id: it.id, label: it.label }; close(); return; }
                if (chosen.has(it.id)) chosen.delete(it.id); else chosen.set(it.id, { id: it.id, label: it.label });
                paint(rows);
              },
            }, h('div', null, h('span', null, it.label), it.sub ? h('small', null, it.sub) : null), on ? icon('check', 18) : null));
          });
        }

        async function load() {
          const my = ++seq;
          clear(list).append(skeleton(3));
          try {
            const rows = await search(input.value.trim());
            if (my === seq) paint(rows || []);
          } catch {
            if (my === seq) paint([]);
          }
        }

        input.addEventListener('input', debounce(load, 250));
        load();
        return h('div', { class: 'picker' },
          h('div', { class: 'picker-search' }, input),
          list,
          multi ? h('button', {
            class: 'btn primary block', type: 'button',
            onClick: () => { result = [...chosen.values()]; close(); },
          }, t('common.done')) : null);
      },
    });
  });
}

export function pickerButton({ placeholder, value, open, onChange }) {
  const label = h('span', { class: 'picker-label' });
  const clearBtn = h('button', {
    class: 'picker-clear', type: 'button', 'aria-label': t('common.clear'),
    onClick: (e) => { e.stopPropagation(); set(null); onChange(null); },
  }, icon('x', 16));
  const btn = h('div', {
    class: 'input picker-btn', role: 'button', tabindex: 0,
    onClick: async () => {
      const r = await open(value);
      if (r) { set(r); onChange(r); }
    },
    onKeydown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); btn.click(); } },
  }, label, clearBtn);

  function set(v) {
    value = v;
    label.textContent = v ? v.label : placeholder;
    label.classList.toggle('placeholder', !v);
    clearBtn.hidden = !v;
  }
  set(value);
  btn.set = set;
  return btn;
}
