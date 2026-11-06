import { h, clear } from '../ui/dom.js';

const NS = 'http://www.w3.org/2000/svg';

export function s(tag, attrs = {}, ...kids) {
  const el = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) if (v != null) el.setAttribute(k, v);
  for (const c of kids.flat()) if (c != null) el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  return el;
}

export const color = (i) => `var(--chart-${(i % 10) + 1})`;

const reducedMotion = () => window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function niceNum(x, round) {
  const exp = Math.floor(Math.log10(x));
  const f = x / 10 ** exp;
  let nf;
  if (round) nf = f < 1.5 ? 1 : f < 3 ? 2 : f < 7 ? 5 : 10;
  else nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
  return nf * 10 ** exp;
}

export function niceTicks(min, max, count = 5) {
  if (min === max) {
    const pad = Math.abs(min) * 0.1 || 1;
    min -= pad;
    max += pad;
  }
  const step = niceNum(niceNum(max - min, false) / (count - 1), true);
  const lo = Math.floor(min / step + 1e-9) * step;
  const hi = Math.ceil(max / step - 1e-9) * step;
  const n = Math.round((hi - lo) / step);
  const ticks = Array.from({ length: n + 1 }, (_, i) => +(lo + i * step).toPrecision(12));
  return { min: ticks[0], max: ticks[ticks.length - 1], ticks };
}

const textWidth = (str) => [...str].reduce((w, ch) => w + (ch.charCodeAt(0) > 255 ? 12 : 6.4), 0);

export function truncate(str, px) {
  if (textWidth(str) <= px) return str;
  let out = '';
  for (const ch of str) {
    if (textWidth(out + ch) > px - 10) break;
    out += ch;
  }
  return `${out}…`;
}

export const maxTickWidth = (texts) => Math.max(...texts.map(textWidth));

export function tipRow(i, name, value) {
  return h('div', { class: 'tip-row' },
    h('i', { class: 'legend-swatch', style: { background: color(i) } }),
    h('span', { class: 'tip-name' }, name),
    h('b', null, value));
}

export function mountChart(el, initial, draw) {
  let opts = initial;
  let first = true;
  let lastW = 0;
  const hidden = new Set();
  const legend = h('div', { class: 'chart-legend' });
  const plot = h('div', { class: 'chart-plot' });
  const tip = h('div', { class: 'chart-tip', hidden: true });
  plot.append(tip);
  el.replaceChildren(h('div', { class: 'chart' }, plot, legend));

  const tooltip = {
    show(nodes, x, y) {
      clear(tip).append(...nodes);
      tip.hidden = false;
      const w = plot.clientWidth;
      const tw = tip.offsetWidth;
      const th = tip.offsetHeight;
      const left = x + 14 + tw > w ? Math.max(0, x - 14 - tw) : x + 14;
      const top = Math.max(0, Math.min(y - th / 2, plot.clientHeight - th));
      tip.style.left = `${left}px`;
      tip.style.top = `${top}px`;
    },
    hide() { tip.hidden = true; },
  };

  function paintLegend() {
    clear(legend);
    legend.hidden = opts.series.length < 2;
    opts.series.forEach((sr, i) => legend.append(h('button', {
      type: 'button',
      class: `legend-item${hidden.has(i) ? ' off' : ''}`,
      'aria-pressed': String(!hidden.has(i)),
      onClick: () => {
        if (hidden.has(i)) hidden.delete(i); else hidden.add(i);
        render();
      },
    }, h('i', { class: 'legend-swatch', style: { background: color(i) } }), sr.name)));
  }

  function render() {
    const width = Math.floor(plot.clientWidth);
    if (!width) return;
    lastW = width;
    paintLegend();
    tooltip.hide();
    plot.querySelector('svg')?.remove();
    const svg = draw({ opts, hidden, width, tooltip, animate: first && !reducedMotion() && opts.animate !== false });
    first = false;
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', opts.ariaLabel || opts.series.map((x) => x.name).join(', '));
    plot.prepend(svg);
  }

  const ro = new ResizeObserver(() => {
    if (Math.floor(plot.clientWidth) !== lastW) render();
  });
  ro.observe(plot);
  render();

  return {
    update(next) {
      opts = next;
      for (const i of [...hidden]) if (i >= next.series.length) hidden.delete(i);
      first = true;
      render();
    },
    destroy() { ro.disconnect(); },
  };
}

export function labelStep(n, pxPerLabel, band) {
  return Math.max(1, Math.ceil(pxPerLabel / Math.max(band, 1)));
}

export function pointerX(svg, e) {
  return e.clientX - svg.getBoundingClientRect().left;
}
