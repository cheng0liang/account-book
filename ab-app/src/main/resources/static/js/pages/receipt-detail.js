import { h } from '../ui/dom.js';
import { icon } from '../ui/icons.js';
import { pageHeader, sectionTitle } from '../ui/layout.js';
import { toast } from '../ui/toast.js';
import { confirmDialog, openImageViewer } from '../ui/modal.js';
import * as api from '../api.js';
import { t, tOr, errorText } from '../i18n/index.js';
import { replace } from '../router.js';
import { fmtMoney, fmtDateTime, countryName, mapUrl } from '../format.js';

const kv = (k, v) => (v == null || v === '' ? null : h('div', { class: 'kv' }, h('span', null, k), h('span', null, v)));

export async function render(ctx) {
  const id = ctx.params.id;
  const r = await api.getReceipt(id);
  if (r.imageUrl) {
    r.imageUrl = await api.fetchBlobUrl(r.imageUrl).catch(() => null);
    if (r.imageUrl) ctx.onCleanup(() => URL.revokeObjectURL(r.imageUrl));
  }
  const m = r.merchant || {};
  const cur = r.currency;
  const hasGeo = m.latitude != null && m.longitude != null;
  const place = [m.country ? countryName(m.country) : null, m.city, m.district].filter(Boolean).join(' · ');

  async function remove() {
    const ok = await confirmDialog({ title: t('detail.deleteTitle'), message: t('detail.deleteMessage'), confirmText: t('common.delete'), danger: true });
    if (!ok) return;
    try {
      await api.deleteReceipt(id);
      toast(t('common.deleted'), 'success');
      replace('#/home');
    } catch (e) {
      toast(errorText(e), 'error');
    }
  }

  const items = (r.items || []).map((it) => {
    const p = it.product || {};
    const sub = [p.brand, p.specText].filter(Boolean).join(' · ');
    return h('div', { class: 'detail-item' },
      h('div', { class: 'row-main' },
        h('div', { class: 'row-title' }, p.name),
        sub ? h('div', { class: 'row-sub' }, sub) : null,
        h('div', { class: 'row-meta' }, `${it.quantity} × ${fmtMoney(it.unitPrice, cur)}${it.discount ? ` − ${fmtMoney(it.discount, cur)}` : ''}`)),
      h('div', { class: 'row-amount' }, fmtMoney(it.amount, cur)));
  });

  const sum = (r.items || []).reduce((a, it) => a + (it.amount || 0), 0);

  return h('div', { class: 'page' },
    pageHeader({
      title: t('detail.title'), back: '#/home',
      right: r.owner && !r.owner.mine ? null : [
        h('a', { class: 'icon-btn', href: `#/receipt/${id}/edit`, 'aria-label': t('common.edit') }, icon('edit')),
        h('button', { class: 'icon-btn danger', type: 'button', 'aria-label': t('common.delete'), onClick: remove }, icon('trash')),
      ],
    }),
    h('div', { class: 'page-body' },
      r.imageUrl ? h('button', { class: 'thumb wide', type: 'button', onClick: () => openImageViewer(r.imageUrl) },
        h('img', { src: r.imageUrl, alt: t('receipt.image') }), h('span', null, t('form.viewImage'))) : null,
      h('section', { class: 'card' },
        h('div', { class: 'detail-merchant' },
          m.id ? h('a', { class: 'detail-merchant-name', href: `#/merchant/${m.id}` }, m.name) : h('div', { class: 'detail-merchant-name' }, m.name),
          m.type ? h('span', { class: 'tag' }, tOr(`merchantType.${m.type}`, m.type)) : null),
        kv(t('merchant.region'), place),
        kv(t('merchant.address'), m.address),
        hasGeo ? h('a', { class: 'map-link', href: mapUrl(m.latitude, m.longitude), target: '_blank', rel: 'noopener' }, icon('pin', 16), t('detail.openMap')) : null,
        h('hr'),
        r.owner && !r.owner.mine ? kv(t('family.recordedBy'), r.owner.nickname || t('family.unknownMember')) : null,
        kv(t('form.purchasedAt'), fmtDateTime(r.purchasedAt)),
        kv(t('form.payment'), r.paymentMethod ? tOr(`payment.${r.paymentMethod}`, r.paymentMethod) : null),
        kv(t('form.note'), r.note)),
      sectionTitle(t('form.items')),
      h('section', { class: 'card list' }, items),
      h('section', { class: 'card' },
        kv(t('form.subtotal'), fmtMoney(sum, cur)),
        r.taxAmount ? kv(t('form.tax'), fmtMoney(r.taxAmount, cur)) : null,
        r.discountAmount ? kv(t('form.orderDiscount'), fmtMoney(-r.discountAmount, cur)) : null,
        h('div', { class: 'kv total' }, h('span', null, t('form.total')), h('span', null, fmtMoney(r.totalAmount, cur))))));
}
