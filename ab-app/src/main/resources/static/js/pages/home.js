import { h, clear } from '../ui/dom.js';
import { icon } from '../ui/icons.js';
import { emptyState, skeleton, errorBlock } from '../ui/layout.js';
import { barChart } from '../charts/barChart.js';
import * as api from '../api.js';
import { t, tOr } from '../i18n/index.js';
import { getCurrency } from '../store.js';
import { fmtMoney, fmtMonthTitle, fmtDayHeader, fmtTime, monthRange, fmtAxisLabel } from '../format.js';

const PAGE_SIZE = 20;
const now = new Date();
let cursor = { y: now.getFullYear(), m: now.getMonth() };

export function render(ctx) {
  const title = h('div', { class: 'month-title' });
  const prev = h('button', { class: 'icon-btn', type: 'button', 'aria-label': t('home.prevMonth'), onClick: () => shift(-1) }, icon('chevronLeft'));
  const next = h('button', { class: 'icon-btn', type: 'button', 'aria-label': t('home.nextMonth'), onClick: () => shift(1) }, icon('chevronRight'));
  const summary = h('section', { class: 'card summary' });
  const groups = h('section', { class: 'receipt-groups' });
  const moreBtn = h('button', { class: 'btn block', type: 'button', hidden: true, onClick: () => loadMore() }, t('common.loadMore'));
  const root = h('div', { class: 'page home' },
    h('header', { class: 'month-bar' }, prev, title, next), summary, groups, moreBtn);

  let reqId = 0;
  let loading = false;
  let rows = [];
  let total = 0;
  let range;
  let chart = null;

  const io = new IntersectionObserver((entries) => {
    if (entries.some((e) => e.isIntersecting) && !moreBtn.hidden) loadMore();
  });
  io.observe(moreBtn);
  ctx.onCleanup(() => { io.disconnect(); chart?.destroy(); });

  function shift(d) {
    const dt = new Date(cursor.y, cursor.m + d, 1);
    cursor = { y: dt.getFullYear(), m: dt.getMonth() };
    load();
  }

  function paintSummary(sp) {
    chart?.destroy();
    chart = null;
    clear(summary);
    const cur = sp?.currency || getCurrency();
    summary.append(
      h('div', { class: 'summary-label' }, t('home.monthTotal')),
      h('div', { class: 'summary-total' }, fmtMoney(sp?.total ?? 0, cur)),
      h('div', { class: 'summary-sub' }, t('home.receiptCount', { n: sp?.count ?? 0 })));
    if (sp && sp.series?.[0] && sp.labels?.length) {
      const host = h('div', { class: 'mini-chart' });
      summary.append(host);
      chart = barChart(host, {
        labels: sp.labels, series: [{ key: 'a', name: t('analytics.amount'), data: sp.series[0].data }],
        formatValue: (v) => fmtMoney(v, cur), formatLabel: fmtAxisLabel, height: 84, axis: false,
      });
    }
  }

  function paintList() {
    clear(groups);
    const byDay = new Map();
    for (const r of rows) {
      const day = r.purchasedAt.slice(0, 10);
      if (!byDay.has(day)) byDay.set(day, []);
      byDay.get(day).push(r);
    }
    for (const [day, list] of byDay) {
      groups.append(h('div', { class: 'group' },
        h('div', { class: 'group-title' }, fmtDayHeader(day)),
        h('div', { class: 'card list' }, list.map(row))));
    }
    moreBtn.hidden = rows.length >= total;
  }

  function row(r) {
    const items = (r.firstItems || []).join(' / ');
    const more = r.itemCount > (r.firstItems || []).length ? ` ${t('home.moreItems', { n: r.itemCount - r.firstItems.length })}` : '';
    return h('a', { class: 'receipt-row', href: `#/receipt/${r.id}` },
      h('div', { class: 'row-main' },
        h('div', { class: 'row-title' }, r.merchantName),
        h('div', { class: 'row-sub' }, items + more),
        h('div', { class: 'row-meta' }, [fmtTime(r.purchasedAt), r.paymentMethod ? tOr(`payment.${r.paymentMethod}`, r.paymentMethod) : null].filter(Boolean).join(' · '))),
      h('div', { class: 'row-amount' }, fmtMoney(r.totalAmount, r.currency)));
  }

  async function load() {
    const id = ++reqId;
    title.textContent = fmtMonthTitle(cursor.y, cursor.m);
    range = monthRange(cursor.y, cursor.m);
    rows = [];
    total = 0;
    moreBtn.hidden = true;
    clear(summary).append(skeleton(2));
    clear(groups).append(skeleton(4));
    const [sp, rc] = await Promise.allSettled([
      api.spending({ groupBy: 'day', ...range, currency: getCurrency() }),
      api.listReceipts({ ...range, page: 0, size: PAGE_SIZE }),
    ]);
    if (id !== reqId) return;
    paintSummary(sp.status === 'fulfilled' ? sp.value : null);
    clear(groups);
    if (rc.status === 'rejected') { groups.append(errorBlock(rc.reason, load)); return; }
    rows = rc.value.items;
    total = rc.value.total;
    if (!rows.length) {
      groups.append(emptyState({
        title: t('home.emptyTitle'), text: t('home.emptyText'),
        action: h('a', { class: 'btn primary', href: '#/scan' }, icon('camera', 20), t('home.scanCta')),
      }));
      return;
    }
    paintList();
  }

  async function loadMore() {
    if (loading || rows.length >= total) return;
    loading = true;
    const id = reqId;
    try {
      const res = await api.listReceipts({ ...range, page: Math.floor(rows.length / PAGE_SIZE), size: PAGE_SIZE });
      if (id !== reqId) return;
      rows = rows.concat(res.items);
      total = res.total;
      paintList();
    } catch (e) {
      moreBtn.textContent = t('common.retry');
    } finally {
      loading = false;
    }
  }

  load();
  return root;
}
