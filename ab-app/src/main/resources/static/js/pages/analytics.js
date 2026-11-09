import { h, clear, append, debounce } from '../ui/dom.js';
import { icon } from '../ui/icons.js';
import { pageHeader, sectionTitle, emptyState, skeleton, errorBlock } from '../ui/layout.js';
import { segmented } from '../ui/segmented.js';
import { scopeSwitch } from '../ui/scope.js';
import { getScope } from '../scope.js';
import { makeField, selectEl, switchEl } from '../ui/form.js';
import { openPicker, pickerButton } from '../ui/picker.js';
import { lineChart } from '../charts/lineChart.js';
import { barChart } from '../charts/barChart.js';
import * as api from '../api.js';
import { t, tk, tOr } from '../i18n/index.js';
import { getMeta, getCurrency } from '../store.js';
import { fmtMoney, fmtDate, fmtPercent, fmtAxisLabel, countryName } from '../format.js';

const TABS = [['trend', tk('analytics.tab.trend')], ['compare', tk('analytics.tab.compare')], ['spending', tk('analytics.tab.spending')]];
const GRANULARITIES = [['day', tk('granularity.day')], ['week', tk('granularity.week')], ['month', tk('granularity.month')]];
const METRICS = [['avg', tk('metric.avg')], ['min', tk('metric.min')], ['max', tk('metric.max')]];
const DIMENSIONS = [
  ['merchant', tk('dimension.merchant')], ['city', tk('dimension.city')], ['district', tk('dimension.district')],
  ['country', tk('dimension.country')], ['merchantType', tk('dimension.merchantType')], ['brand', tk('dimension.brand')],
  ['product', tk('dimension.product')],
];
const GROUPS = [
  ['day', tk('groupBy.day')], ['month', tk('groupBy.month')], ['merchant', tk('groupBy.merchant')],
  ['merchantType', tk('groupBy.merchantType')], ['category', tk('groupBy.category')], ['brand', tk('groupBy.brand')],
  ['city', tk('groupBy.city')], ['district', tk('groupBy.district')],
];
const TIME_GROUPS = new Set(['day', 'month']);
const EMPTY_FACETS = { currencies: [], countries: [], cities: [], districts: [], merchantTypes: [], brands: [], categories: [], tags: [], dateRange: null };

const defaults = () => ({
  tab: 'trend',
  currency: '',
  trend: { product: null, merchants: [], granularity: 'month', metric: 'avg', normalized: false },
  compare: { dimension: 'merchant', product: null, merchant: null, brand: '', tag: '', category: '', city: '', district: '', from: '', to: '', normalized: false },
  spending: { groupBy: 'month', from: '', to: '', topN: 10 },
});

let state = defaults();
let facetsCache = null;
let facetsScope = null;

const cur = () => state.currency || getCurrency();
const opts = (pairs) => pairs.map(([value, key]) => ({ value, label: t(key) }));
const productItem = (p) => ({ id: p.id, label: p.name, sub: [p.brand, p.specText].filter(Boolean).join(' · ') });
const merchantItem = (m) => ({ id: m.id, label: m.name, sub: [m.city, m.district].filter(Boolean).join(' · ') });
const searchProducts = (q) => api.listProducts({ q, size: 30 }).then((r) => r.items);
const searchMerchants = (q) => api.listMerchants({ q, size: 30 }).then((r) => r.items);

function labelOf(kind, l) {
  if (kind === 'merchantType') return tOr(`merchantType.${l}`, l);
  if (kind === 'category') return tOr(`category.${l}`, l);
  if (kind === 'country') return countryName(l);
  return l;
}

function applyQuery(q) {
  if (!TABS.some(([v]) => v === q.tab)) return;
  state = { ...defaults(), currency: state.currency, tab: q.tab };
  const c = state.compare;
  if (q.dimension) c.dimension = q.dimension;
  if (q.merchantId) c.merchant = { id: Number(q.merchantId), label: q.merchantName || `#${q.merchantId}` };
  if (q.productId) c.product = { id: Number(q.productId), label: q.productName || `#${q.productId}` };
  for (const k of ['brand', 'tag', 'category', 'city', 'district', 'from', 'to']) if (q[k]) c[k] = q[k];
}

export async function render(ctx) {
  if (!ctx.reused) applyQuery(ctx.query);
  let facets = facetsCache;
  if (!ctx.reused || !facets || facetsScope !== getScope()) {
    try { facets = await api.facets(); } catch { facets = EMPTY_FACETS; }
    facetsCache = facets;
    facetsScope = getScope();
  }
  facets = { ...EMPTY_FACETS, ...facets };

  let charts = [];
  const destroyCharts = () => { charts.forEach((c) => c.destroy()); charts = []; };
  const track = (c) => { charts.push(c); return c; };
  ctx.onCleanup(destroyCharts);

  const currencyOptions = () => {
    const list = facets.currencies.length ? facets.currencies : getMeta().currencies;
    return [...new Set([cur(), ...list])].map((c) => ({ value: c, label: c }));
  };
  const currencyField = (schedule) => makeField(t('analytics.currency'),
    selectEl(currencyOptions(), cur(), (v) => { state.currency = v; schedule(); }));
  const selectField = (label, options, obj, key, schedule) => makeField(label,
    selectEl(options, obj[key], (v) => { obj[key] = v; schedule(); }, { blank: t('common.all') }));
  const dateFields = (obj, schedule) => h('div', { class: 'grid-2' },
    [['from', tk('analytics.from')], ['to', tk('analytics.to')]].map(([k, key]) => makeField(t(key), h('input', {
      type: 'date', class: 'input', value: obj[k], min: facets.dateRange?.min, max: facets.dateRange?.max,
      onChange: (e) => { obj[k] = e.target.value; schedule(); },
    }))));
  const plain = (list) => list.map((v) => ({ value: v, label: v }));

  // generic panel: filters + result area with debounced, stale-safe loading
  function makePanel(buildFilters, paint) {
    const out = h('div', { class: 'analytics-out' });
    let seq = 0;
    async function run() {
      const my = ++seq;
      const current = () => my === seq;
      destroyCharts();
      clear(out).append(skeleton(4));
      try {
        const node = await paint(current);
        if (!current() || !node) return;
        clear(out).append(node);
      } catch (e) {
        if (current()) clear(out).append(errorBlock(e, run));
      }
    }
    const schedule = debounce(run, 300);
    return { el: h('div', null, h('div', { class: 'card filters' }, buildFilters(schedule)), out), run };
  }

  const noData = () => emptyState({ iconName: 'chart', title: t('analytics.noData') });
  const hasData = (res) => res.series?.some((sr) => sr.data.some((v) => v != null));
  const chartCard = (build) => {
    const host = h('div', { class: 'chart-host' });
    const card = h('div', { class: 'card' }, host);
    track(build(host));
    return card;
  };

  // ---------- trend ----------
  function trendPanel() {
    const s = state.trend;
    return makePanel((schedule) => {
      const chips = h('div', { class: 'chip-row' });
      const paintChips = () => {
        append(clear(chips), [s.merchants.length
          ? s.merchants.map((m, i) => h('span', { class: 'chip' }, m.label,
            h('button', { type: 'button', 'aria-label': t('common.remove'), onClick: () => { s.merchants.splice(i, 1); paintChips(); schedule(); } }, icon('x', 14))))
          : h('small', { class: 'field-hint' }, t('analytics.merchantsAuto'))]);
      };
      paintChips();
      return [
        makeField(t('analytics.product'), pickerButton({
          placeholder: t('analytics.pickProduct'), value: s.product,
          open: () => openPicker({ title: t('analytics.pickProduct'), search: searchProducts, toItem: productItem }),
          onChange: (v) => { s.product = v; schedule(); },
        })),
        makeField(t('analytics.merchants'), h('div', null,
          h('button', {
            class: 'btn small', type: 'button',
            onClick: async () => {
              const r = await openPicker({ title: t('analytics.pickMerchants'), multi: true, selected: s.merchants, search: searchMerchants, toItem: merchantItem });
              if (r) { s.merchants = r; paintChips(); schedule(); }
            },
          }, t('analytics.pickMerchants')), chips)),
        h('div', { class: 'grid-2' },
          makeField(t('analytics.granularity'), segmented(opts(GRANULARITIES), s.granularity, (v) => { s.granularity = v; schedule(); })),
          makeField(t('analytics.metric'), segmented(opts(METRICS), s.metric, (v) => { s.metric = v; schedule(); }))),
        h('div', { class: 'grid-2' },
          currencyField(schedule),
          switchEl(t('analytics.normalized'), s.normalized, (v) => { s.normalized = v; schedule(); })),
      ];
    }, async (current) => {
      if (!s.product) return emptyState({ iconName: 'search', title: t('analytics.trendPick') });
      const base = { productId: s.product.id, currency: cur() };
      const [trend, table] = await Promise.all([
        api.priceTrend({ ...base, merchantIds: s.merchants.map((m) => m.id), granularity: s.granularity, metric: s.metric, normalized: s.normalized }),
        api.priceTable(base),
      ]);
      if (!current()) return null;
      const box = h('div');
      if (!hasData(trend)) box.append(noData());
      else {
        box.append(h('div', { class: 'unit-hint' }, tOr(`priceUnit.${trend.unit}`, trend.unit || '')),
          chartCard((host) => lineChart(host, {
            labels: trend.labels, series: trend.series,
            formatValue: (v) => fmtMoney(v, trend.currency), formatLabel: fmtAxisLabel,
            tooltipExtra: (i) => (trend.counts ? t('analytics.sampleCount', { n: trend.counts[i] ?? 0 }) : null),
            ariaLabel: `${s.product.label} ${t(`metric.${s.metric}`)}`,
          })));
      }
      if (table.rows?.length) {
        box.append(sectionTitle(t('analytics.cheapest')), h('div', { class: 'card list' }, table.rows.map((r, i) => h('a', {
          class: 'list-row', href: `#/merchant/${r.merchantId}`,
        }, h('div', { class: 'row-main' },
          h('div', { class: 'row-title' }, r.merchantName, i === 0 ? h('span', { class: 'tag best' }, t('analytics.best')) : null),
          h('div', { class: 'row-sub' }, [r.city, r.district, fmtDate(r.latestDate)].filter(Boolean).join(' · ')),
          h('div', { class: 'row-meta' }, t('analytics.priceRange', {
            min: fmtMoney(r.minPrice, table.currency), avg: fmtMoney(r.avgPrice, table.currency), max: fmtMoney(r.maxPrice, table.currency), n: r.count,
          }))),
        h('div', { class: 'row-amount' }, fmtMoney(r.latestPrice, table.currency))))));
      }
      return box;
    });
  }

  // ---------- compare ----------
  function comparePanel() {
    const s = state.compare;
    const ready = () => {
      if (s.dimension === 'merchant') return s.product || s.brand || s.tag || s.category;
      if (s.dimension === 'product') return s.merchant || s.brand || s.tag || s.category;
      return true;
    };
    return makePanel((schedule) => {
      const districtHost = h('div');
      const paintDistrict = () => {
        const list = facets.districts.filter((d) => !s.city || d.city === s.city).map((d) => d.district);
        clear(districtHost).append(selectField(t('analytics.district'), plain([...new Set(list)]), s, 'district', schedule));
      };
      paintDistrict();
      const picker = (label, key, search, toItem) => makeField(label, pickerButton({
        placeholder: t('common.all'), value: s[key],
        open: () => openPicker({ title: label, search, toItem }),
        onChange: (v) => { s[key] = v; schedule(); },
      }));
      return [
        makeField(t('analytics.dimension'), selectEl(opts(DIMENSIONS), s.dimension, (v) => { s.dimension = v; schedule(); })),
        h('div', { class: 'grid-2' },
          picker(t('analytics.product'), 'product', searchProducts, productItem),
          picker(t('analytics.merchant'), 'merchant', searchMerchants, merchantItem)),
        h('div', { class: 'grid-2' },
          selectField(t('analytics.brand'), plain(facets.brands), s, 'brand', schedule),
          selectField(t('analytics.tag'), plain(facets.tags), s, 'tag', schedule)),
        h('div', { class: 'grid-2' },
          selectField(t('analytics.category'), facets.categories.map((c) => ({ value: c, label: tOr(`category.${c}`, c) })), s, 'category', schedule),
          currencyField(schedule)),
        h('div', { class: 'grid-2' },
          selectField(t('analytics.city'), plain([...new Set(facets.cities.map((c) => c.city))]), s, 'city', () => { s.district = ''; paintDistrict(); schedule(); }),
          districtHost),
        dateFields(s, schedule),
        switchEl(t('analytics.normalized'), s.normalized, (v) => { s.normalized = v; schedule(); }),
      ];
    }, async (current) => {
      if (!ready()) return emptyState({ iconName: 'search', title: t('analytics.compareNeedFilter') });
      const res = await api.priceCompare({
        dimension: s.dimension, normalized: s.normalized, productId: s.product?.id, merchantId: s.merchant?.id,
        brand: s.brand, tag: s.tag, category: s.category, city: s.city, district: s.district, from: s.from, to: s.to, currency: cur(),
      });
      if (!current()) return null;
      if (!res.labels?.length || !hasData(res)) return noData();
      const series = res.series.map((sr) => ({ ...sr, name: tOr(`metric.${sr.key}`, sr.name) }));
      const data = (k) => res.series.find((x) => x.key === k)?.data || [];
      const [avg, min, max] = [data('avg'), data('min'), data('max')];
      const display = (l) => labelOf(s.dimension, l);
      const link = (i) => {
        const id = res.meta?.[i]?.id;
        if (id == null) return null;
        return s.dimension === 'merchant' ? `#/merchant/${id}` : s.dimension === 'product' ? `#/product/${id}` : null;
      };
      const box = h('div');
      box.append(h('div', { class: 'unit-hint' }, tOr(`priceUnit.${res.unit}`, res.unit || '')),
        chartCard((host) => barChart(host, {
          labels: res.labels, series, formatValue: (v) => fmtMoney(v, res.currency),
          formatLabel: display, tooltipExtra: (i) => t('analytics.sampleCount', { n: res.counts?.[i] ?? 0 }),
          ariaLabel: t(`dimension.${s.dimension}`),
        })),
        h('div', { class: 'card list' }, res.labels.map((l, i) => {
          const href = link(i);
          const content = [h('div', { class: 'row-main' },
            h('div', { class: 'row-title' }, display(l)),
            h('div', { class: 'row-meta' }, t('analytics.priceRange', {
              min: fmtMoney(min[i], res.currency), avg: fmtMoney(avg[i], res.currency), max: fmtMoney(max[i], res.currency), n: res.counts?.[i] ?? 0,
            }))),
          h('div', { class: 'row-amount' }, fmtMoney(avg[i], res.currency))];
          return href ? h('a', { class: 'list-row', href }, content) : h('div', { class: 'list-row' }, content);
        })));
      return box;
    });
  }

  // ---------- spending ----------
  function spendingPanel() {
    const s = state.spending;
    return makePanel((schedule) => {
      const topField = makeField(t('analytics.topN'), selectEl(
        [5, 10, 20].map((n) => ({ value: String(n), label: String(n) })), String(s.topN), (v) => { s.topN = Number(v); schedule(); }));
      const sync = () => { topField.hidden = TIME_GROUPS.has(s.groupBy); };
      sync();
      return [
        makeField(t('analytics.groupBy'), selectEl(opts(GROUPS), s.groupBy, (v) => { s.groupBy = v; sync(); schedule(); })),
        dateFields(s, schedule),
        h('div', { class: 'grid-2' }, currencyField(schedule), topField),
      ];
    }, async (current) => {
      const res = await api.spending({
        groupBy: s.groupBy, topN: TIME_GROUPS.has(s.groupBy) ? null : s.topN, from: s.from, to: s.to, currency: cur(),
      });
      if (!current()) return null;
      if (!res.labels?.length || !hasData(res)) return noData();
      const data = res.series[0].data;
      const time = TIME_GROUPS.has(s.groupBy);
      const display = (l) => (time ? fmtAxisLabel(l) : labelOf(s.groupBy, l));
      const money = (v) => fmtMoney(v, res.currency);
      const ranked = res.labels.map((l, i) => ({ l, v: data[i] })).filter((r) => r.v != null).sort((a, b) => b.v - a.v).slice(0, s.topN);
      const denom = res.total || ranked.reduce((a, r) => a + r.v, 0) || 1;
      return h('div', null,
        h('div', { class: 'card summary compact' },
          h('div', { class: 'summary-total' }, money(res.total)),
          h('div', { class: 'summary-sub' }, t('home.receiptCount', { n: res.count ?? 0 }))),
        chartCard((host) => barChart(host, {
          labels: res.labels, series: [{ key: 'amount', name: t('analytics.amount'), data }],
          formatValue: money, formatLabel: display, tooltipExtra: (i) => (res.counts ? t('analytics.sampleCount', { n: res.counts[i] ?? 0 }) : null),
          ariaLabel: t(`groupBy.${s.groupBy}`),
        })),
        sectionTitle(t('analytics.ranking')),
        h('div', { class: 'card list' }, ranked.map((r, i) => h('div', { class: 'list-row rank-row' },
          h('span', { class: 'rank-no' }, i + 1),
          h('div', { class: 'row-main' },
            h('div', { class: 'row-title' }, display(r.l)),
            h('div', { class: 'share' }, h('i', { style: { width: `${Math.max(2, (r.v / denom) * 100)}%` } }))),
          h('div', { class: 'rank-val' }, h('div', { class: 'row-amount' }, money(r.v)), h('small', null, fmtPercent(r.v / denom)))))));
    });
  }

  const panelHost = h('div');
  let panel;
  function mountPanel() {
    destroyCharts();
    panel = { trend: trendPanel, compare: comparePanel, spending: spendingPanel }[state.tab]();
    clear(panelHost).append(panel.el);
    panel.run();
  }
  mountPanel();

  return h('div', { class: 'page' },
    pageHeader({ title: t('analytics.title') }),
    h('div', { class: 'page-body' },
      scopeSwitch(),
      segmented(opts(TABS), state.tab, (v) => { state.tab = v; mountPanel(); }),
      panelHost));
}
