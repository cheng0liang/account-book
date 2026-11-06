import { h, clear, append, debounce } from '../ui/dom.js';
import { icon } from '../ui/icons.js';
import { pageHeader, emptyState, skeleton, errorBlock } from '../ui/layout.js';
import { segmented } from '../ui/segmented.js';
import * as api from '../api.js';
import { t, tOr } from '../i18n/index.js';
import { countryName } from '../format.js';

const PAGE_SIZE = 20;
let tab = 'merchant';

export function render(ctx) {
  if (!ctx.reused && ['merchant', 'product'].includes(ctx.query.tab)) tab = ctx.query.tab;
  let q = '';
  let rows = [];
  let total = 0;
  let reqId = 0;

  const addBtn = h('a', { class: 'icon-btn', 'aria-label': t('common.add') }, icon('plus'));
  const input = h('input', { class: 'input', type: 'search', placeholder: t('common.search'), autocomplete: 'off' });
  const list = h('div', { class: 'card list' });
  const body = h('div', { class: 'lib-results' });
  const moreBtn = h('button', { class: 'btn block', type: 'button', hidden: true, onClick: () => loadMore() }, t('common.loadMore'));
  const seg = segmented([
    { value: 'merchant', label: t('library.merchants') },
    { value: 'product', label: t('library.products') },
  ], tab, (v) => { tab = v; load(); });

  const fetchPage = (page) => (tab === 'merchant' ? api.listMerchants : api.listProducts)({ q, page, size: PAGE_SIZE });

  function merchantRow(m) {
    const place = [m.country ? countryName(m.country) : null, m.city, m.district].filter(Boolean).join(' · ');
    return h('a', { class: 'list-row', href: `#/merchant/${m.id}` },
      h('div', { class: 'row-main' }, h('div', { class: 'row-title' }, m.name),
        h('div', { class: 'row-sub' }, [m.type ? tOr(`merchantType.${m.type}`, m.type) : null, place].filter(Boolean).join(' · '))),
      icon('chevronRight', 18));
  }

  function productRow(p) {
    return h('a', { class: 'list-row', href: `#/product/${p.id}` },
      h('div', { class: 'row-main' }, h('div', { class: 'row-title' }, p.name),
        h('div', { class: 'row-sub' }, [p.brand, p.specText, p.category ? tOr(`category.${p.category}`, p.category) : null].filter(Boolean).join(' · ')),
        p.tags?.length ? h('div', { class: 'chip-row' }, p.tags.map((x) => h('span', { class: 'chip static' }, x))) : null),
      icon('chevronRight', 18));
  }

  function paint() {
    clear(body);
    addBtn.href = `#/${tab}/new`;
    if (!rows.length) {
      body.append(emptyState({ iconName: 'search', title: q ? t('common.noResults') : t('library.empty') }));
      moreBtn.hidden = true;
      return;
    }
    append(clear(list), rows.map(tab === 'merchant' ? merchantRow : productRow));
    body.append(list);
    moreBtn.hidden = rows.length >= total;
  }

  async function load() {
    const id = ++reqId;
    rows = [];
    total = 0;
    moreBtn.hidden = true;
    addBtn.href = `#/${tab}/new`;
    clear(body).append(skeleton(4));
    try {
      const res = await fetchPage(0);
      if (id !== reqId) return;
      rows = res.items;
      total = res.total;
      paint();
    } catch (e) {
      if (id === reqId) clear(body).append(errorBlock(e, load));
    }
  }

  async function loadMore() {
    const id = reqId;
    try {
      const res = await fetchPage(Math.floor(rows.length / PAGE_SIZE));
      if (id !== reqId) return;
      rows = rows.concat(res.items);
      total = res.total;
      paint();
    } catch { /* keep button for retry */ }
  }

  input.addEventListener('input', debounce(() => { q = input.value.trim(); load(); }, 300));
  load();
  return h('div', { class: 'page' },
    pageHeader({ title: t('library.title'), right: addBtn }),
    h('div', { class: 'page-body' }, seg, h('div', { class: 'search-row' }, input), body, moreBtn));
}
