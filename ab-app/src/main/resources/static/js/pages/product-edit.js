import { h } from '../ui/dom.js';
import { pageHeader } from '../ui/layout.js';
import { toast } from '../ui/toast.js';
import { autocomplete } from '../ui/autocomplete.js';
import { chipInput } from '../ui/chips.js';
import { makeField, textInput, numInput, selectEl, nz } from '../ui/form.js';
import * as api from '../api.js';
import { t, errorText } from '../i18n/index.js';
import { getMeta } from '../store.js';
import { replace } from '../router.js';

export async function render(ctx) {
  const id = ctx.params.id;
  const isNew = id == null;
  const src = isNew ? {} : await api.getProduct(id);
  const meta = getMeta();
  const p = {
    name: src.name ?? '', brand: src.brand ?? '', category: src.category ?? '', specValue: src.specValue ?? null,
    specUnit: src.specUnit ?? '', barcode: src.barcode ?? '', tags: [...(src.tags || [])],
  };
  const fields = new Map();
  const reg = (path, f) => { fields.set(path, f); return f; };

  const brandInput = textInput({ value: p.brand, onInput: (e) => { p.brand = e.target.value; } });
  autocomplete(brandInput, {
    fetch: (q) => api.productBrands(q), toItem: (s) => ({ label: s }), minChars: 0,
    onSelect: (s) => { p.brand = s; brandInput.value = s; },
  });
  const specText = h('small', { class: 'field-hint' });
  const paintSpec = () => { specText.textContent = p.specValue != null && p.specUnit ? `${t('product.specText')}: ${p.specValue}${p.specUnit}` : ''; };
  paintSpec();

  const saveBtn = h('button', { class: 'btn primary grow', type: 'button', onClick: save }, t('common.save'));

  async function save() {
    if (!p.name.trim()) {
      fields.get('name').setError(t('validation.required'));
      toast(t('validation.fixErrors'), 'error');
      return;
    }
    const hasSpec = p.specValue != null && p.specUnit;
    const body = {
      name: p.name.trim(), brand: nz(p.brand), category: nz(p.category),
      specText: hasSpec ? `${p.specValue}${p.specUnit}` : null, specValue: hasSpec ? p.specValue : null, specUnit: hasSpec ? p.specUnit : null,
      barcode: nz(p.barcode), tags: p.tags,
    };
    saveBtn.disabled = true;
    try {
      if (isNew) await api.createProduct(body); else await api.updateProduct(id, body);
      toast(t('common.saved'), 'success');
      replace('#/library?tab=product');
    } catch (e) {
      saveBtn.disabled = false;
      const hit = e.code === 'VALIDATION_FAILED' && e.details.find((d) => fields.has(d.field));
      if (hit) fields.get(hit.field).setError(hit.message);
      toast(errorText(e), 'error');
    }
  }

  return h('div', { class: 'page form-page' },
    pageHeader({ title: isNew ? t('product.newTitle') : t('product.editTitle'), back: '#/library?tab=product' }),
    h('div', { class: 'page-body' },
      h('section', { class: 'card' },
        reg('name', makeField(t('product.name'), textInput({ value: p.name, onInput: (e) => { p.name = e.target.value; } }), { required: true })),
        makeField(t('product.brand'), h('div', { class: 'ac-wrap' }, brandInput)),
        makeField(t('product.category'), selectEl(
          meta.productCategories.map((v) => ({ value: v, label: t(`category.${v}`) })), p.category, (v) => { p.category = v; }, { blank: '—' })),
        h('div', { class: 'grid-2' },
          makeField(t('product.specValue'), numInput({ value: p.specValue, min: 0, onValue: (v) => { p.specValue = v; paintSpec(); } })),
          makeField(t('product.specUnit'), selectEl(
            meta.specUnits.map((v) => ({ value: v, label: t(`unit.${v}`) })), p.specUnit, (v) => { p.specUnit = v; paintSpec(); }, { blank: '—' }))),
        specText,
        makeField(t('product.barcode'), textInput({ value: p.barcode, inputmode: 'numeric', onInput: (e) => { p.barcode = e.target.value; } })),
        makeField(t('product.tags'), chipInput({ value: p.tags, placeholder: t('product.tagsPlaceholder'), suggest: (q) => api.productTags(q), onChange: () => {} })))),
    h('div', { class: 'action-bar' },
      h('a', { class: 'btn', href: '#/library?tab=product' }, t('common.cancel')),
      saveBtn));
}
