import { s, color, niceTicks, mountChart, maxTickWidth, tipRow, labelStep, pointerX } from './common.js';
import { h } from '../ui/dom.js';

const MAX_DOTS = 200;

function draw({ opts, hidden, width, tooltip, animate }) {
  const { labels, series, formatValue = String, formatLabel = (x) => x } = opts;
  const formatTick = opts.formatTick || formatValue;
  const H = opts.height || 220;
  const axis = opts.axis !== false;
  const vis = series.map((sr, i) => ({ sr, i })).filter((o) => !hidden.has(o.i));

  let min = Infinity;
  let max = -Infinity;
  for (const { sr } of vis) for (const v of sr.data) if (v != null) { min = Math.min(min, v); max = Math.max(max, v); }
  if (min === Infinity) { min = 0; max = 1; }
  const sc = niceTicks(min, max, 5);
  const tickTexts = sc.ticks.map(formatTick);

  const pad = { l: axis ? maxTickWidth(tickTexts) + 12 : 8, r: 14, t: 10, b: axis ? 26 : 6 };
  const pw = width - pad.l - pad.r;
  const ph = H - pad.t - pad.b;
  const n = labels.length;
  const X = (i) => pad.l + (n <= 1 ? pw / 2 : (pw * i) / (n - 1));
  const Y = (v) => pad.t + ph * (1 - (v - sc.min) / (sc.max - sc.min));

  const svg = s('svg', { viewBox: `0 0 ${width} ${H}`, width, height: H, class: 'chart-svg' });

  if (axis) {
    sc.ticks.forEach((v, k) => {
      svg.append(
        s('line', { class: 'chart-grid', x1: pad.l, x2: width - pad.r, y1: Y(v), y2: Y(v) }),
        s('text', { class: 'chart-text', x: pad.l - 6, y: Y(v) + 4, 'text-anchor': 'end' }, tickTexts[k]),
      );
    });
    const step = labelStep(n, 60, n <= 1 ? pw : pw / (n - 1));
    labels.forEach((l, i) => {
      if (i % step === 0) svg.append(s('text', { class: 'chart-text', x: X(i), y: H - 6, 'text-anchor': 'middle' }, formatLabel(l)));
    });
  }

  const dots = n <= MAX_DOTS;
  for (const { sr, i } of vis) {
    let seg = [];
    const flush = () => {
      if (seg.length > 1) {
        svg.append(s('path', {
          class: `chart-line${animate ? ' anim' : ''}`, pathLength: 1, stroke: color(i), fill: 'none',
          d: seg.map((p, k) => `${k ? 'L' : 'M'}${X(p)} ${Y(sr.data[p])}`).join(''),
        }));
      } else if (seg.length === 1 && !dots) {
        svg.append(s('circle', { cx: X(seg[0]), cy: Y(sr.data[seg[0]]), r: 3, fill: color(i) }));
      }
      seg = [];
    };
    sr.data.forEach((v, p) => { if (v == null) flush(); else seg.push(p); });
    flush();
    if (dots) {
      sr.data.forEach((v, p) => {
        if (v != null) svg.append(s('circle', { class: 'chart-dot', cx: X(p), cy: Y(v), r: 2.8, fill: color(i) }));
      });
    }
  }

  const guide = s('line', { class: 'chart-guide', y1: pad.t, y2: pad.t + ph, visibility: 'hidden' });
  const marks = vis.map(({ i }) => s('circle', { r: 4.5, fill: color(i), class: 'chart-mark', visibility: 'hidden' }));
  const hit = s('rect', { x: pad.l, y: pad.t, width: pw, height: ph, fill: 'transparent', style: 'touch-action:pan-y' });
  svg.append(guide, ...marks, hit);

  function show(e) {
    if (!n) return;
    const x = pointerX(svg, e);
    const idx = n <= 1 ? 0 : Math.max(0, Math.min(n - 1, Math.round(((x - pad.l) / pw) * (n - 1))));
    guide.setAttribute('x1', X(idx));
    guide.setAttribute('x2', X(idx));
    guide.setAttribute('visibility', 'visible');
    vis.forEach(({ sr }, k) => {
      const v = sr.data[idx];
      marks[k].setAttribute('visibility', v == null ? 'hidden' : 'visible');
      if (v != null) { marks[k].setAttribute('cx', X(idx)); marks[k].setAttribute('cy', Y(v)); }
    });
    const rows = vis.map(({ sr, i }) => tipRow(i, sr.name, sr.data[idx] == null ? '–' : formatValue(sr.data[idx])));
    const extra = opts.tooltipExtra ? opts.tooltipExtra(idx) : null;
    tooltip.show([h('div', { class: 'tip-title' }, formatLabel(labels[idx])), ...rows, extra ? h('div', { class: 'tip-extra' }, extra) : null].filter(Boolean), X(idx), pad.t + ph / 2);
  }
  function hideAll() {
    guide.setAttribute('visibility', 'hidden');
    marks.forEach((m) => m.setAttribute('visibility', 'hidden'));
    tooltip.hide();
  }
  hit.addEventListener('pointerdown', show);
  hit.addEventListener('pointermove', show);
  hit.addEventListener('pointerleave', (e) => { if (e.pointerType === 'mouse') hideAll(); });
  return svg;
}

export function lineChart(el, opts) {
  return mountChart(el, opts, draw);
}
