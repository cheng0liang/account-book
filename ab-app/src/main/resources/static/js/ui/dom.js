export function append(el, children) {
  for (const c of children.flat(Infinity)) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

export function h(tag, props, ...children) {
  const el = document.createElement(tag);
  if (props) {
    for (const [k, v] of Object.entries(props)) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'style') (typeof v === 'string' ? (el.style.cssText = v) : Object.assign(el.style, v));
      else if (k.length > 2 && k.startsWith('on')) el.addEventListener(k.slice(2).toLowerCase(), v);
      else if (/^(aria-|data-)/.test(k) || k === 'role' || k === 'inputmode') el.setAttribute(k, v === true ? '' : v);
      else if (k in el) el[k] = v;
      else el.setAttribute(k, v === true ? '' : v);
    }
  }
  return append(el, children);
}

export function clear(el) {
  el.replaceChildren();
  return el;
}

export function debounce(fn, ms = 300) {
  let id;
  const d = (...args) => {
    clearTimeout(id);
    id = setTimeout(() => fn(...args), ms);
  };
  d.cancel = () => clearTimeout(id);
  return d;
}
