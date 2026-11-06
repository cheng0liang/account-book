import { h } from './dom.js';

export function makeField(label, control, { required = false, hint } = {}) {
  const err = h('div', { class: 'field-error', role: 'alert' });
  const el = h('div', { class: 'field' },
    label ? h('label', { class: 'field-label' }, label, required ? h('em', null, '*') : null) : null,
    control,
    hint ? h('small', { class: 'field-hint' }, hint) : null,
    err);
  el.setError = (msg) => {
    err.textContent = msg || '';
    el.classList.toggle('invalid', Boolean(msg));
  };
  const reset = () => { if (el.classList.contains('invalid')) el.setError(''); };
  el.addEventListener('input', reset);
  el.addEventListener('change', reset);
  return el;
}

export function textInput(props) {
  return h('input', { type: 'text', class: 'input', autocomplete: 'off', ...props });
}

export function numInput({ value, step = 'any', min, onValue, onCommit, disabled, placeholder }) {
  const input = h('input', {
    type: 'number', class: 'input num', inputmode: 'decimal', step, min, disabled, placeholder,
    value: value == null ? '' : value,
  });
  input.addEventListener('input', () => {
    const raw = input.value;
    const n = raw === '' ? null : Number(raw);
    onValue(n == null || Number.isNaN(n) ? null : n);
  });
  if (onCommit) input.addEventListener('change', onCommit);
  return input;
}

export function selectEl(options, value, onChange, { blank, disabled } = {}) {
  const opts = [...options];
  if (value && !opts.some((o) => o.value === value)) opts.push({ value, label: value });
  const sel = h('select', { class: 'input', disabled, onChange: (e) => onChange(e.target.value) },
    blank != null ? h('option', { value: '' }, blank) : null,
    opts.map((o) => h('option', { value: o.value }, o.label)));
  sel.value = value ?? '';
  return sel;
}

export function switchEl(label, checked, onChange) {
  return h('label', { class: 'switch' },
    h('input', { type: 'checkbox', checked, onChange: (e) => onChange(e.target.checked) }),
    h('i', { class: 'switch-track' }),
    h('span', null, label));
}

export const nz = (v) => {
  if (v == null) return null;
  const s = typeof v === 'string' ? v.trim() : v;
  return s === '' ? null : s;
};
