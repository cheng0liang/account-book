import { h } from './dom.js';
import { segmented } from './segmented.js';
import { t } from '../i18n/index.js';
import { getFamily, getScope, setScope } from '../scope.js';

// "Me | Family" switch; null when the user has no family, so callers can drop it straight into a layout.
export function scopeSwitch() {
  if (!getFamily()) return null;
  return h('div', { class: 'scope-switch' },
    segmented([{ value: 'me', label: t('scope.me') }, { value: 'family', label: t('scope.family') }], getScope(), setScope));
}
