import { h } from './dom.js';
import { icon } from './icons.js';
import { t } from '../i18n/index.js';

const open = new Set();

function lock() {
  document.body.classList.toggle('no-scroll', open.size > 0);
}

function overlay(kind, content, onClose) {
  const backdrop = h('div', { class: `overlay ${kind}` });
  let closed = false;
  const close = (result) => {
    if (closed) return;
    closed = true;
    document.removeEventListener('keydown', onKey);
    backdrop.remove();
    open.delete(close);
    lock();
    if (onClose) onClose(result);
  };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  backdrop.addEventListener('pointerdown', (e) => { if (e.target === backdrop) close(); });
  document.addEventListener('keydown', onKey);
  backdrop.append(content(close));
  document.body.append(backdrop);
  open.add(close);
  lock();
  return { close };
}

export function closeAllOverlays() {
  [...open].forEach((c) => c());
}

export function openSheet({ title, build, onClose }) {
  return overlay('sheet-overlay', (close) =>
    h('div', { class: 'sheet', role: 'dialog', 'aria-modal': 'true' },
      h('div', { class: 'sheet-head' },
        h('h2', null, title),
        h('button', { class: 'icon-btn', type: 'button', 'aria-label': t('common.close'), onClick: () => close() }, icon('x'))),
      h('div', { class: 'sheet-body' }, build(close))), onClose);
}

export function confirmDialog({ title, message, confirmText, danger = false }) {
  return new Promise((resolve) => {
    overlay('dialog-overlay', (close) =>
      h('div', { class: 'dialog', role: 'alertdialog', 'aria-modal': 'true' },
        h('h2', null, title),
        message ? h('p', null, message) : null,
        h('div', { class: 'dialog-actions' },
          h('button', { class: 'btn', type: 'button', onClick: () => close(false) }, t('common.cancel')),
          h('button', { class: `btn ${danger ? 'danger' : 'primary'}`, type: 'button', onClick: () => close(true) }, confirmText || t('common.confirm')))),
    (result) => resolve(result === true));
  });
}

export function openImageViewer(src) {
  overlay('viewer-overlay', (close) =>
    h('div', { class: 'viewer', onClick: () => close() },
      h('img', { src, alt: t('receipt.image') })));
}
