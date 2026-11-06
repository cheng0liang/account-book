import { h, clear, append } from '../ui/dom.js';
import { icon } from '../ui/icons.js';
import { pageHeader } from '../ui/layout.js';
import { toast } from '../ui/toast.js';
import { compressImage } from '../ui/image.js';
import * as api from '../api.js';
import { t, errorText } from '../i18n/index.js';
import { setRecognition } from '../store.js';
import { navigate } from '../router.js';

export function render(ctx) {
  let alive = true;
  ctx.onCleanup(() => { alive = false; });
  const body = h('div', { class: 'page-body scan' });

  const fileInput = (props) => h('input', { type: 'file', accept: 'image/*', hidden: true, ...props, onChange: (e) => {
    const f = e.target.files[0];
    e.target.value = '';
    if (f) handle(f);
  } });
  const cam = fileInput({ capture: 'environment' });
  const pick = fileInput({});

  function idle(failure) {
    append(clear(body), [
      failure ? h('div', { class: 'notice error' }, failure) : null,
      h('p', { class: 'scan-hint' }, t('scan.hint')),
      h('button', { class: 'big-btn primary', type: 'button', onClick: () => cam.click() }, icon('camera', 32), h('span', null, t('scan.camera'))),
      h('button', { class: 'big-btn', type: 'button', onClick: () => pick.click() }, icon('image', 32), h('span', null, t('scan.gallery'))),
      h('a', { class: 'big-btn', href: '#/receipt/new' }, icon('edit', 32), h('span', null, t('scan.manual'))),
      cam, pick]);
  }

  function processing(url) {
    append(clear(body), [
      h('div', { class: 'scan-preview' }, h('img', { src: url, alt: t('receipt.image') }), h('div', { class: 'scan-line' })),
      h('p', { class: 'scan-status' }, t('scan.recognizing'))]);
  }

  async function handle(file) {
    let url;
    try {
      const blob = await compressImage(file);
      url = URL.createObjectURL(blob);
      processing(url);
      const res = await api.recognize(blob);
      if (!alive) return;
      setRecognition({ ...res, previewUrl: url });
      if (res.provider === 'noop') toast(t('scan.noop'), 'info', 4500);
      navigate('#/receipt/new');
    } catch (e) {
      if (url) URL.revokeObjectURL(url);
      if (alive) idle(`${t('scan.failed')} ${errorText(e)}`);
    }
  }

  idle();
  return h('div', { class: 'page' }, pageHeader({ title: t('scan.title') }), body);
}
