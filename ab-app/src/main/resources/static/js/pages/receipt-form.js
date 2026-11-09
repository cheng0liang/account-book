import { h, clear, append } from '../ui/dom.js';
import { icon } from '../ui/icons.js';
import { pageHeader, sectionTitle } from '../ui/layout.js';
import { toast } from '../ui/toast.js';
import { openImageViewer } from '../ui/modal.js';
import { autocomplete } from '../ui/autocomplete.js';
import { chipButtons, chipInput } from '../ui/chips.js';
import { makeField, textInput, numInput, selectEl, nz } from '../ui/form.js';
import { merchantFields } from '../ui/merchant-fields.js';
import * as api from '../api.js';
import { t, tOr, errorText } from '../i18n/index.js';
import { getMeta, getCurrency, takeRecognition } from '../store.js';
import { replace } from '../router.js';
import { currencyDigits, roundMoney, fmtMoney, nowLocalInput } from '../format.js';

let session = null;
let seq = 0;

const SPEC_RE = /^\s*(\d+(?:\.\d+)?)\s*(kg|g|ml|l|pcs)\s*$/i;

const emptyMerchant = () => ({ id: null, name: '', country: '', city: '', district: '', type: '', address: '', latitude: null, longitude: null });

function toMerchant(m) {
  const e = emptyMerchant();
  if (!m) return e;
  return {
    id: m.id ?? null, name: m.name ?? '', country: m.country ?? '', city: m.city ?? '', district: m.district ?? '',
    type: m.type ?? '', address: m.address ?? '', latitude: m.latitude ?? null, longitude: m.longitude ?? null,
  };
}

function toProduct(p) {
  const e = { id: null, name: '', brand: '', category: '', specText: '', specValue: null, specUnit: '', barcode: '', tags: [] };
  if (!p) return e;
  const r = {
    id: p.id ?? null, name: p.name ?? '', brand: p.brand ?? '', category: p.category ?? '', specText: p.specText ?? '',
    specValue: p.specValue ?? null, specUnit: p.specUnit ?? '', barcode: p.barcode ?? '', tags: [...(p.tags || [])],
  };
  const m = SPEC_RE.exec(r.specText);
  if (r.specValue == null && !r.specUnit && m) { r.specValue = Number(m[1]); r.specUnit = m[2].toLowerCase(); }
  return r;
}

const calcAmount = (it, cur) => (it.quantity == null || it.unitPrice == null ? null : roundMoney(it.quantity * it.unitPrice - (it.discount || 0), cur));

function toItem(it, cur, candidates = []) {
  const item = {
    key: ++seq, id: it.id ?? null, product: toProduct(it.product),
    quantity: it.quantity ?? 1, unitPrice: it.unitPrice ?? null, discount: it.discount ?? null, amount: it.amount ?? null,
    amountTouched: false, note: it.note ?? null, expanded: false, candidates,
  };
  const calc = calcAmount(item, cur);
  item.amountTouched = item.amount != null && calc != null && Math.abs(item.amount - calc) > 1e-6;
  return item;
}

function toForm(src, rec) {
  const cur = src.currency || getCurrency();
  const cands = rec?.productCandidates || {};
  const items = (src.items || []).map((it, i) => toItem(it, cur, cands[i] || []));
  if (!items.length) items.push(toItem({}, cur));
  return {
    id: src.id ?? null,
    merchant: toMerchant(src.merchant),
    purchasedAt: (src.purchasedAt || nowLocalInput()).slice(0, 16),
    currency: cur,
    totalAmount: src.totalAmount ?? null,
    totalTouched: src.totalAmount != null,
    taxAmount: src.taxAmount ?? null,
    discountAmount: src.discountAmount ?? null,
    paymentMethod: src.paymentMethod || '',
    note: src.note || '',
    source: src.source || 'MANUAL',
    imageToken: rec?.imageToken || null,
    imageUrl: src.imageUrl || null,
    items,
  };
}

export async function render(ctx) {
  const isEdit = ctx.params.id != null;
  const key = isEdit ? `e${ctx.params.id}` : 'new';
  let form;
  let rec = null;
  if (ctx.reused && session?.key === key) {
    ({ form, rec } = session);
  } else {
    if (isEdit) {
      form = toForm(await api.getReceipt(ctx.params.id));
      if (form.imageUrl) form.imageUrl = await api.fetchBlobUrl(form.imageUrl).catch(() => null);
    } else {
      rec = takeRecognition();
      form = toForm(rec?.draft || {}, rec);
    }
    session = { key, form, rec };
  }

  const fields = new Map();
  const reg = (path, f) => { fields.set(path, f); return f; };
  const step = () => String(1 / 10 ** currencyDigits(form.currency));
  const itemsSum = () => roundMoney(form.items.reduce((a, it) => a + (it.amount ?? calcAmount(it, form.currency) ?? 0), 0), form.currency);

  // image
  const imgSrc = form.imageUrl || rec?.previewUrl;
  const thumb = imgSrc
    ? h('button', { class: 'thumb', type: 'button', onClick: () => openImageViewer(imgSrc) }, h('img', { src: imgSrc, alt: t('receipt.image') }), h('span', null, t('form.viewImage')))
    : null;

  // banners
  const notes = [];
  if (rec?.provider === 'noop') notes.push(t('scan.noop'));
  (rec?.warnings || []).forEach((w) => notes.push(tOr(`warning.${w}`, w)));
  const banner = notes.length ? h('div', { class: 'notice' }, notes.map((n) => h('div', null, n))) : null;

  // merchant
  const merchantBox = h('section', { class: 'card' });
  function renderMerchant() {
    const m = form.merchant;
    const linked = m.id != null;
    for (const k of [...fields.keys()]) if (k.startsWith('merchant.')) fields.delete(k);
    const nameInput = textInput({ value: m.name, readOnly: linked, placeholder: t('merchant.namePlaceholder'), onInput: (e) => { m.name = e.target.value; } });
    if (!linked) {
      autocomplete(nameInput, {
        fetch: (q) => api.listMerchants({ q, size: 8 }).then((r) => r.items),
        toItem: (x) => ({ label: x.name, sub: [x.city, x.district].filter(Boolean).join(' · ') }),
        onSelect: (x) => { form.merchant = toMerchant(x); renderMerchant(); },
      });
    }
    const cands = !linked && rec?.merchantCandidates?.length
      ? h('div', null, h('small', { class: 'field-hint' }, t('form.candidates')),
        chipButtons(rec.merchantCandidates.map((c) => ({ value: c, label: c.name })), (c) => { form.merchant = toMerchant(c); renderMerchant(); }))
      : null;
    append(clear(merchantBox), [
      sectionTitle(t('form.merchant'), linked
        ? h('span', { class: 'linked' }, icon('check', 14), t('form.linked'),
          h('button', { class: 'link-btn', type: 'button', onClick: () => { m.id = null; renderMerchant(); } }, t('form.unlink')))
        : null),
      cands,
      reg('merchant.name', makeField(t('merchant.name'), h('div', { class: 'ac-wrap' }, nameInput), { required: true })),
      merchantFields(m, { disabled: linked, register: reg })]);
  }

  // basics
  const meta = getMeta();
  const currencies = [...new Set([form.currency, ...meta.currencies])].map((c) => ({ value: c, label: c }));
  const basics = h('section', { class: 'card' },
    sectionTitle(t('form.basics')),
    reg('purchasedAt', makeField(t('form.purchasedAt'), h('input', {
      type: 'datetime-local', class: 'input', value: form.purchasedAt, onInput: (e) => { form.purchasedAt = e.target.value; },
    }), { required: true })),
    h('div', { class: 'grid-2' },
      reg('currency', makeField(t('form.currency'), selectEl(currencies, form.currency, (v) => {
        form.currency = v; renderItems(); updateTotals();
      }), { required: true })),
      reg('paymentMethod', makeField(t('form.payment'), selectEl(
        meta.paymentMethods.map((v) => ({ value: v, label: t(`payment.${v}`) })), form.paymentMethod,
        (v) => { form.paymentMethod = v; }, { blank: '—' })))),
    reg('note', makeField(t('form.note'), textInput({ value: form.note, onInput: (e) => { form.note = e.target.value; } }))));

  // items
  const itemsBox = h('div', { class: 'items' });
  const itemsErr = reg('items', makeField(null, h('span')));

  function renderItems() {
    for (const k of [...fields.keys()]) if (k.startsWith('items[')) fields.delete(k);
    clear(itemsBox);
    form.items.forEach((it, i) => itemsBox.append(itemCard(it, i)));
  }

  function itemCard(it, idx) {
    const p = it.product;
    const base = `items[${idx}]`;
    const linked = p.id != null;
    const recalc = () => {
      if (!it.amountTouched) {
        it.amount = calcAmount(it, form.currency);
        amt.value = it.amount ?? '';
      }
      updateTotals();
    };
    const nameInput = textInput({ value: p.name, readOnly: linked, placeholder: t('product.namePlaceholder'), onInput: (e) => { p.name = e.target.value; } });
    if (!linked) {
      autocomplete(nameInput, {
        fetch: (q) => api.listProducts({ q, size: 8 }).then((r) => r.items),
        toItem: (x) => ({ label: x.name, sub: [x.brand, x.specText].filter(Boolean).join(' · ') }),
        onSelect: (x) => { it.product = toProduct(x); renderItems(); },
      });
    }
    const qty = numInput({ value: it.quantity, min: 0, onValue: (v) => { it.quantity = v; recalc(); } });
    const price = numInput({ value: it.unitPrice, min: 0, step: step(), onValue: (v) => { it.unitPrice = v; recalc(); } });
    const disc = numInput({ value: it.discount, min: 0, step: step(), onValue: (v) => { it.discount = v; recalc(); } });
    const amt = numInput({
      value: it.amount, step: step(),
      onValue: (v) => { it.amount = v; it.amountTouched = v != null; updateTotals(); },
      onCommit: () => { if (it.amount == null) { it.amountTouched = false; recalc(); } },
    });

    const head = h('div', { class: 'item-head' },
      h('div', { class: 'item-name' },
        reg(`${base}.product.name`, makeField(null, h('div', { class: 'ac-wrap' }, nameInput))),
        linked ? h('span', { class: 'linked' }, icon('check', 14), t('form.linked'),
          h('button', { class: 'link-btn', type: 'button', onClick: () => { p.id = null; renderItems(); } }, t('form.unlinkProduct'))) : null),
      h('button', {
        class: 'icon-btn', type: 'button', 'aria-label': t('form.removeItem'), disabled: form.items.length < 2,
        onClick: () => { form.items.splice(idx, 1); renderItems(); updateTotals(); },
      }, icon('trash', 20)));

    const cands = !linked && it.candidates.length
      ? chipButtons(it.candidates.map((c) => ({ value: c, label: [c.name, c.brand, c.specText].filter(Boolean).join(' ') })), (c) => { it.product = toProduct(c); renderItems(); })
      : null;

    const nums = h('div', { class: 'grid-4' },
      reg(`${base}.quantity`, makeField(t('form.quantity'), qty)),
      reg(`${base}.unitPrice`, makeField(t('form.unitPrice'), price)),
      reg(`${base}.discount`, makeField(t('form.discount'), disc)),
      reg(`${base}.amount`, makeField(t('form.amount'), amt)));

    return h('div', { class: 'card item' }, head, cands, nums,
      h('button', { class: 'link-btn more-toggle', type: 'button', onClick: () => { it.expanded = !it.expanded; renderItems(); } },
        it.expanded ? t('form.less') : t('form.more')),
      it.expanded ? moreFields(it, linked) : null);
  }

  function moreFields(it, linked) {
    const p = it.product;
    const specPreview = h('small', { class: 'field-hint' });
    const paintSpec = () => { specPreview.textContent = p.specText ? `${t('product.specText')}: ${p.specText}` : ''; };
    const syncSpec = () => {
      p.specText = p.specValue != null && p.specUnit ? `${p.specValue}${p.specUnit}` : '';
      paintSpec();
    };
    paintSpec();
    const brandInput = textInput({ value: p.brand, disabled: linked, onInput: (e) => { p.brand = e.target.value; } });
    if (!linked) {
      autocomplete(brandInput, {
        fetch: (q) => api.productBrands(q), toItem: (s) => ({ label: s }), minChars: 0,
        onSelect: (s) => { p.brand = s; brandInput.value = s; },
      });
    }
    return h('div', { class: 'fields item-more' },
      makeField(t('product.brand'), h('div', { class: 'ac-wrap' }, brandInput)),
      makeField(t('product.category'), selectEl(
        meta.productCategories.map((v) => ({ value: v, label: t(`category.${v}`) })), p.category, (v) => { p.category = v; }, { blank: '—', disabled: linked })),
      h('div', { class: 'grid-2' },
        makeField(t('product.specValue'), numInput({ value: p.specValue, min: 0, disabled: linked, onValue: (v) => { p.specValue = v; syncSpec(); } })),
        makeField(t('product.specUnit'), selectEl(
          meta.specUnits.map((v) => ({ value: v, label: t(`unit.${v}`) })), p.specUnit, (v) => { p.specUnit = v; syncSpec(); }, { blank: '—', disabled: linked }))),
      specPreview,
      makeField(t('product.barcode'), textInput({ value: p.barcode, disabled: linked, inputmode: 'numeric', onInput: (e) => { p.barcode = e.target.value; } })),
      makeField(t('product.tags'), chipInput({
        value: p.tags, disabled: linked, placeholder: t('product.tagsPlaceholder'),
        suggest: (q) => api.productTags(q), onChange: () => {},
      })));
  }

  // totals
  const subtotalEl = h('span');
  const diffEl = h('div', { class: 'notice warn', hidden: true });
  const totalIn = numInput({
    value: form.totalAmount, step: step(),
    onValue: (v) => { form.totalAmount = v; form.totalTouched = v != null; if (v != null) updateDiff(); },
    onCommit: () => { if (form.totalAmount == null) updateTotals(); },
  });
  const taxIn = numInput({ value: form.taxAmount, min: 0, step: step(), onValue: (v) => { form.taxAmount = v; } });
  const discIn = numInput({ value: form.discountAmount, min: 0, step: step(), onValue: (v) => { form.discountAmount = v; } });

  function updateDiff() {
    const diff = roundMoney((form.totalAmount ?? 0) - itemsSum(), form.currency);
    diffEl.hidden = diff === 0;
    diffEl.textContent = t('form.diffWarn', { diff: fmtMoney(diff, form.currency) });
  }
  function updateTotals() {
    const sum = itemsSum();
    subtotalEl.textContent = fmtMoney(sum, form.currency);
    for (const el of [totalIn, taxIn, discIn]) el.step = step();
    if (!form.totalTouched) { form.totalAmount = sum; totalIn.value = sum; }
    updateDiff();
  }

  const totals = h('section', { class: 'card' },
    sectionTitle(t('form.totals')),
    h('div', { class: 'kv' }, h('span', null, t('form.subtotal')), subtotalEl),
    h('div', { class: 'grid-2' },
      makeField(t('form.tax'), taxIn),
      makeField(t('form.orderDiscount'), discIn)),
    reg('totalAmount', makeField(t('form.total'), totalIn)),
    diffEl);

  // validation
  function validate() {
    const req = t('validation.required');
    const errs = [];
    if (!form.merchant.name.trim()) errs.push(['merchant.name', req]);
    if (!form.purchasedAt) errs.push(['purchasedAt', req]);
    if (!form.currency) errs.push(['currency', req]);
    if (!form.items.length) errs.push(['items', t('validation.itemsRequired')]);
    form.items.forEach((it, i) => {
      if (!it.product.name.trim()) errs.push([`items[${i}].product.name`, req]);
      if (!(it.quantity > 0)) errs.push([`items[${i}].quantity`, t('validation.positive')]);
      if (it.unitPrice == null || it.unitPrice < 0) errs.push([`items[${i}].unitPrice`, req]);
    });
    return errs;
  }

  function showErrors(errs) {
    fields.forEach((f) => f.setError(''));
    let first = null;
    const loose = [];
    for (const [rawPath, msg] of errs) {
      const path = rawPath.replace(/^(items\[\d+\])\.name$/, '$1.product.name');
      const f = fields.get(path);
      if (f) { f.setError(msg); first = first || f; } else loose.push(msg);
    }
    first?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    return loose;
  }

  function payload() {
    const m = form.merchant;
    return {
      merchant: {
        id: m.id, name: m.name.trim(), country: nz(m.country), city: nz(m.city), district: nz(m.district),
        type: nz(m.type), address: nz(m.address), latitude: m.latitude, longitude: m.longitude,
      },
      purchasedAt: form.purchasedAt,
      currency: form.currency,
      totalAmount: form.totalAmount ?? itemsSum(),
      taxAmount: form.taxAmount ?? 0,
      discountAmount: form.discountAmount ?? 0,
      paymentMethod: nz(form.paymentMethod),
      note: form.note.trim(),
      source: isEdit ? form.source : form.imageToken ? 'OCR' : 'MANUAL',
      ...(isEdit ? {} : { imageToken: form.imageToken }),
      items: form.items.map((it) => ({
        id: it.id,
        product: {
          id: it.product.id, name: it.product.name.trim(), brand: nz(it.product.brand), category: nz(it.product.category),
          specText: nz(it.product.specText), specValue: it.product.specValue, specUnit: nz(it.product.specUnit),
          barcode: nz(it.product.barcode), tags: it.product.tags,
        },
        quantity: it.quantity,
        unitPrice: it.unitPrice,
        discount: it.discount ?? 0,
        amount: it.amount ?? calcAmount(it, form.currency),
        note: it.note,
      })),
    };
  }

  const saveBtn = h('button', { class: 'btn primary grow', type: 'button', onClick: save }, t('common.save'));

  async function save() {
    const errs = validate();
    if (errs.length) {
      showErrors(errs);
      toast(t('validation.fixErrors'), 'error');
      return;
    }
    saveBtn.disabled = true;
    try {
      const body = payload();
      const saved = isEdit ? await api.updateReceipt(form.id, body) : await api.createReceipt(body);
      if (rec?.previewUrl) URL.revokeObjectURL(rec.previewUrl);
      session = null;
      toast(t('common.saved'), 'success');
      replace(`#/receipt/${saved.id}`);
    } catch (e) {
      saveBtn.disabled = false;
      if (e.code === 'VALIDATION_FAILED' && e.details.length) {
        const loose = showErrors(e.details.map((d) => [d.field, d.message]));
        toast([errorText(e), ...loose].join(' '), 'error', 4500);
      } else {
        toast(errorText(e), 'error');
      }
    }
  }

  renderMerchant();
  renderItems();
  updateTotals();

  return h('div', { class: 'page form-page' },
    pageHeader({ title: isEdit ? t('form.editTitle') : t('form.newTitle'), back: isEdit ? `#/receipt/${form.id}` : '#/scan' }),
    h('div', { class: 'page-body' },
      banner, thumb, merchantBox, basics,
      sectionTitle(t('form.items')),
      itemsErr,
      itemsBox,
      h('button', {
        class: 'btn block', type: 'button',
        onClick: () => { form.items.push(toItem({}, form.currency)); renderItems(); updateTotals(); itemsBox.lastElementChild?.querySelector('input')?.focus(); },
      }, icon('plus', 18), t('form.addItem')),
      totals),
    h('div', { class: 'action-bar' },
      h('a', { class: 'btn', href: isEdit ? `#/receipt/${form.id}` : '#/scan' }, t('common.cancel')),
      saveBtn));
}
