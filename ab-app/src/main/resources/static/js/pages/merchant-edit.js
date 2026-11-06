import { h } from '../ui/dom.js';
import { pageHeader } from '../ui/layout.js';
import { toast } from '../ui/toast.js';
import { makeField, textInput, nz } from '../ui/form.js';
import { merchantFields } from '../ui/merchant-fields.js';
import * as api from '../api.js';
import { t, errorText } from '../i18n/index.js';
import { buildHash, replace } from '../router.js';

const str = (v) => v ?? '';

export async function render(ctx) {
  const id = ctx.params.id;
  const isNew = id == null;
  const src = isNew ? {} : await api.getMerchant(id);
  const m = {
    name: str(src.name), country: str(src.country), city: str(src.city), district: str(src.district), type: str(src.type),
    address: str(src.address), latitude: src.latitude ?? null, longitude: src.longitude ?? null,
  };
  const fields = new Map();
  const nameField = makeField(t('merchant.name'), textInput({ value: m.name, onInput: (e) => { m.name = e.target.value; } }), { required: true });
  fields.set('name', nameField);
  const saveBtn = h('button', { class: 'btn primary grow', type: 'button', onClick: save }, t('common.save'));

  async function save() {
    if (!m.name.trim()) {
      nameField.setError(t('validation.required'));
      toast(t('validation.fixErrors'), 'error');
      return;
    }
    saveBtn.disabled = true;
    const body = {
      name: m.name.trim(), country: nz(m.country), city: nz(m.city), district: nz(m.district), type: nz(m.type),
      address: nz(m.address), latitude: m.latitude, longitude: m.longitude,
    };
    try {
      if (isNew) await api.createMerchant(body); else await api.updateMerchant(id, body);
      toast(t('common.saved'), 'success');
      replace('#/library?tab=merchant');
    } catch (e) {
      saveBtn.disabled = false;
      const hit = e.code === 'VALIDATION_FAILED' && e.details.find((d) => fields.has(d.field));
      if (hit) fields.get(hit.field).setError(hit.message);
      toast(errorText(e), 'error');
    }
  }

  const priceLink = isNew ? null : h('a', {
    class: 'btn block',
    href: buildHash('/analytics', { tab: 'compare', dimension: 'product', merchantId: id, merchantName: src.name }),
  }, t('merchant.viewPrices'));

  return h('div', { class: 'page form-page' },
    pageHeader({ title: isNew ? t('merchant.newTitle') : t('merchant.editTitle'), back: '#/library?tab=merchant' }),
    h('div', { class: 'page-body' },
      h('section', { class: 'card' }, nameField, merchantFields(m)),
      priceLink),
    h('div', { class: 'action-bar' },
      h('a', { class: 'btn', href: '#/library?tab=merchant' }, t('common.cancel')),
      saveBtn));
}
