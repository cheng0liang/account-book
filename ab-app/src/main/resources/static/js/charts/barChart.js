import { s, color, niceTicks, mountChart, maxTickWidth, tipRow, labelStep, pointerX, truncate } from './common.js';
import { h } from '../ui/dom.js';

function draw({ opts, hidden, width, tooltip, animate }) {
  const { labels, series, formatValue = String, formatLabel = (x) => x, stacked = false } = opts;
  const formatTick = opts.formatTick || formatValue;
  const H = opts.height || 220;
  const axis = opts.axis !== false;
  const vis = series.map((sr, i) => ({ sr, i })).filter((o) => !hidden.has(o.i));
  const n = labels.length;

  let lo = 0;
  let hi = 0;
  for (let p = 0; p < n; p++) {
    let pos = 0;
    let neg = 0;
    for (const { sr } of vis) {
      const v = sr.data[p];
      if (v == null) continue;
      if (stacked) { if (v >= 0) pos += v; else neg += v; } else { pos = Math.max(pos, v); neg = Math.min(neg, v); }
    }
    hi = Math.max(hi, pos);
    lo = Math.min(lo, neg);
  }
  if (hi === 0 && lo === 0) hi = 1;
  const sc = niceTicks(lo, hi, 5);
  const tickTexts = sc.ticks.map(formatTick);

  const pad = { l: axis ? maxTickWidth(tickTexts) + 12 : 4, r: 8, t: 10, b: axis ? 26 : 4 };
  const pw = width - pad.l - pad.r;
  const ph = H - pad.t - pad.b;
  const Y = (v) => pad.t + ph * (1 - (v - sc.min) / (sc.max - sc.min));
  const band = pw / Math.max(n, 1);
  const k = Math.max(vis.length, 1);
  const bw = Math.max(2, Math.min(36, stacked ? band * 0.6 : (band * 0.72) / k));
  const groupW = stacked ? bw : bw * k;
  const cx = (p) => pad.l + band * (p + 0.5);

  const svg = s('svg', { viewBox: `0 0 ${width} ${H}`, width, height: H, class: 'chart-svg' });
  const hoverBg = s('rect', { class: 'chart-hover', y: pad.t, height: ph, width: band, visibility: 'hidden' });
  svg.append(hoverBg);

  if (axis) {
    sc.ticks.forEach((v, i) => svg.append(
      s('line', { class: 'chart-grid', x1: pad.l, x2: width - pad.r, y1: Y(v), y2: Y(v) }),
      s('text', { class: 'chart-text', x: pad.l - 6, y: Y(v) + 4, 'text-anchor': 'end' }, tickTexts[i]),
    ));
    const step = labelStep(n, 52, band);
    labels.forEach((l, p) => {
      if (p % step === 0) svg.append(s('text', { class: 'chart-text', x: cx(p), y: H - 6, 'text-anchor': 'middle' }, truncate(formatLabel(l), band * step)));
    });
  }

  const base = Y(0);
  for (let p = 0; p < n; p++) {
    let accPos = 0;
    let accNeg = 0;
    vis.forEach(({ sr, i }, j) => {
      const v = sr.data[p];
      if (v == null) return;
      let from;
      let to;
      if (stacked) {
        if (v >= 0) { from = accPos; accPos += v; to = accPos; } else { from = accNeg; accNeg += v; to = accNeg; }
      } else { from = 0; to = v; }
      const y1 = Y(from);
      const y2 = Y(to);
      svg.append(s('rect', {
        class: animate ? 'chart-bar anim' : 'chart-bar',
        x: cx(p) - groupW / 2 + (stacked ? 0 : j * bw), y: Math.min(y1, y2), width: bw, height: Math.max(Math.abs(y1 - y2), v === 0 ? 0 : 1),
        rx: 2, fill: color(i),
      }));
    });
  }
  svg.append(s('line', { class: 'chart-baseline', x1: pad.l, x2: width - pad.r, y1: base, y2: base }));

  const hit = s('rect', { x: pad.l, y: pad.t, width: pw, height: ph, fill: 'transparent', style: 'touch-action:pan-y' });
  svg.append(hit);
  function show(e) {
    if (!n) return;
    const idx = Math.max(0, Math.min(n - 1, Math.floor((pointerX(svg, e) - pad.l) / band)));
    hoverBg.setAttribute('x', pad.l + band * idx);
    hoverBg.setAttribute('visibility', 'visible');
    const rows = vis.map(({ sr, i }) => tipRow(i, sr.name, sr.data[idx] == null ? '–' : formatValue(sr.data[idx])));
    const extra = opts.tooltipExtra ? opts.tooltipExtra(idx) : null;
    tooltip.show([h('div', { class: 'tip-title' }, formatLabel(labels[idx])), ...rows, extra ? h('div', { class: 'tip-extra' }, extra) : null].filter(Boolean), cx(idx), pad.t + ph / 2);
  }
  hit.addEventListener('pointerdown', show);
  hit.addEventListener('pointermove', show);
  hit.addEventListener('pointerleave', (e) => {
    if (e.pointerType === 'mouse') { hoverBg.setAttribute('visibility', 'hidden'); tooltip.hide(); }
  });
  return svg;
}

export function barChart(el, opts) {
  return mountChart(el, opts, draw);
}
