import { h } from '../ui/dom.js';
import { pageHeader, sectionTitle } from '../ui/layout.js';
import { makeField, textInput } from '../ui/form.js';
import { toast } from '../ui/toast.js';
import { confirmDialog } from '../ui/modal.js';
import { setFamily, setScope } from '../scope.js';
import * as api from '../api.js';
import { t, errorText } from '../i18n/index.js';
import { fmtDate } from '../format.js';

export async function render(ctx) {
  const family = await api.myFamily();
  setFamily(family);
  const root = h('div', { class: 'page' });
  const reload = () => ctx.reload();

  async function run(fn, doneKey) {
    try {
      await fn();
      if (doneKey) toast(t(doneKey), 'success', 1500);
      setFamily(await api.myFamily());
      reload();
    } catch (e) {
      toast(errorText(e), 'error');
    }
  }

  async function ask(key, fn, doneKey) {
    if (await confirmDialog({ title: t(`${key}.title`), message: t(`${key}.message`), confirmText: t(`${key}.confirm`), danger: true })) {
      await run(fn, doneKey);
    }
  }

  if (!family) {
    const name = textInput({ maxlength: 30, placeholder: t('family.namePlaceholder') });
    const code = textInput({ maxlength: 16, placeholder: t('family.codePlaceholder'), autocapitalize: 'characters' });
    const fName = makeField(t('family.name'), name);
    const fCode = makeField(t('family.inviteCode'), code);
    root.append(
      pageHeader({ title: t('family.title'), back: '#/profile' }),
      h('div', { class: 'page-body' },
        h('p', { class: 'muted' }, t('family.intro')),
        h('section', { class: 'card' },
          h('h3', null, t('family.create')),
          fName,
          h('button', { class: 'btn primary block', type: 'button', onClick: () => run(() => api.createFamily(name.value.trim() || null), 'family.created') }, t('family.createAction'))),
        h('section', { class: 'card' },
          h('h3', null, t('family.join')),
          fCode,
          h('button', { class: 'btn block', type: 'button', onClick: () => run(() => api.joinFamily(code.value.trim()), 'family.joined') }, t('family.joinAction')))));
    return root;
  }

  const owner = family.role === 'OWNER';
  const copy = async () => {
    try { await navigator.clipboard.writeText(family.inviteCode); toast(t('family.copied'), 'success', 1200); } catch { toast(family.inviteCode, 'info'); }
  };
  const members = family.members.map((m) => h('div', { class: 'member-row' },
    h('span', { class: 'avatar' }, (m.nickname || '?').slice(0, 1)),
    h('div', { class: 'member-main' },
      h('strong', null, m.nickname || t('family.unknownMember'), m.you ? h('em', { class: 'tag' }, t('family.you')) : null),
      h('small', { class: 'muted' }, `${t(`family.role.${m.role}`)} · ${fmtDate(new Date(m.joinedAt).toISOString().slice(0, 10))}`)),
    owner && !m.you
      ? h('button', { class: 'btn small danger', type: 'button', onClick: () => ask('family.kick', () => api.removeFamilyMember(m.userId), 'family.kicked') }, t('family.kickAction'))
      : null));

  root.append(
    pageHeader({ title: family.name || t('family.title'), back: '#/profile' }),
    h('div', { class: 'page-body' },
      h('section', { class: 'card invite' },
        h('small', { class: 'muted' }, t('family.inviteCode')),
        h('div', { class: 'invite-code' }, family.inviteCode),
        h('div', { class: 'row-actions' },
          h('button', { class: 'btn small', type: 'button', onClick: copy }, t('family.copy')),
          owner ? h('button', { class: 'btn small', type: 'button', onClick: () => run(() => api.resetInviteCode(), 'family.codeReset') }, t('family.resetCode')) : null),
        h('small', { class: 'field-hint' }, t('family.inviteHint'))),
      h('section', { class: 'card' },
        sectionTitle(t('family.members', { n: family.members.length })),
        members),
      h('section', { class: 'card' },
        h('a', { class: 'btn block', href: '#/home', onClick: () => setScope('family') }, t('family.viewShared')),
        owner
          ? h('button', { class: 'btn danger block', type: 'button', onClick: () => ask('family.dissolve', () => api.dissolveFamily(), 'family.dissolved') }, t('family.dissolveAction'))
          : h('button', { class: 'btn danger block', type: 'button', onClick: () => ask('family.leave', () => api.leaveFamily(), 'family.left') }, t('family.leaveAction')))));
  return root;
}
