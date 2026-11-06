import { h } from './dom.js';
import { t } from '../i18n/index.js';
import { getMeta } from '../store.js';
import { countryName } from '../format.js';
import { makeField, textInput, numInput, selectEl } from './form.js';
import { toast } from './toast.js';

export const COUNTRIES = ['JP', 'CN', 'TW', 'HK', 'US', 'KR', 'SG', 'TH', 'MY', 'VN', 'ID', 'PH', 'AU', 'GB', 'FR', 'DE', 'IT', 'ES', 'CA'];

function locate(onPosition, button) {
  if (!navigator.geolocation) { toast(t('geo.unavailable'), 'error'); return; }
  button.disabled = true;
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      button.disabled = false;
      onPosition(+pos.coords.latitude.toFixed(6), +pos.coords.longitude.toFixed(6));
    },
    (err) => {
      button.disabled = false;
      if (err.code === 1) toast(t('geo.denied'), 'error');
      else if (err.code === 3) toast(t('geo.timeout'), 'error');
      else toast(t('geo.unavailable'), 'error');
    },
    { enableHighAccuracy: true, timeout: 10000 },
  );
}

// m: {country, city, district, type, address, latitude, longitude}; mutated in place
export function merchantFields(m, { disabled = false, register } = {}) {
  const reg = (path, f) => { if (register) register(path, f); return f; };
  const types = getMeta().merchantTypes.map((v) => ({ value: v, label: t(`merchantType.${v}`) }));
  const countries = COUNTRIES.map((c) => ({ value: c, label: `${countryName(c)} (${c})` }));

  const lat = numInput({ value: m.latitude, min: -90, onValue: (v) => { m.latitude = v; }, disabled, placeholder: t('merchant.latitude') });
  const lon = numInput({ value: m.longitude, min: -180, onValue: (v) => { m.longitude = v; }, disabled, placeholder: t('merchant.longitude') });
  const geoBtn = h('button', { class: 'btn small', type: 'button', disabled, onClick: () => locate((la, lo) => {
    m.latitude = la; m.longitude = lo; lat.value = la; lon.value = lo;
  }, geoBtn) }, t('merchant.useLocation'));

  return h('div', { class: 'fields' },
    h('div', { class: 'grid-2' },
      reg('merchant.country', makeField(t('merchant.country'), selectEl(countries, m.country, (v) => { m.country = v; }, { blank: '—', disabled }))),
      reg('merchant.type', makeField(t('merchant.type'), selectEl(types, m.type, (v) => { m.type = v; }, { blank: '—', disabled })))),
    h('div', { class: 'grid-2' },
      reg('merchant.city', makeField(t('merchant.city'), textInput({ value: m.city, disabled, onInput: (e) => { m.city = e.target.value; } }))),
      reg('merchant.district', makeField(t('merchant.district'), textInput({ value: m.district, disabled, onInput: (e) => { m.district = e.target.value; } })))),
    reg('merchant.address', makeField(t('merchant.address'), textInput({ value: m.address, disabled, onInput: (e) => { m.address = e.target.value; } }))),
    h('div', { class: 'grid-2' },
      reg('merchant.latitude', makeField(t('merchant.latitude'), lat)),
      reg('merchant.longitude', makeField(t('merchant.longitude'), lon))),
    geoBtn);
}
