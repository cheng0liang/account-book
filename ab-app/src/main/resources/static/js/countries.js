// Dial codes offered on the login form. `boxes` are coarse [minLat, maxLat, minLon, maxLon] rectangles used to
// guess the country from a device fix; China is the default and has no box, so anything unmatched stays +86.
export const COUNTRIES = [
  { code: 'CN', dial: '86' },
  { code: 'HK', dial: '852' },
  { code: 'MO', dial: '853' },
  { code: 'TW', dial: '886', boxes: [[21.8, 25.4, 119.3, 122.1]] },
  { code: 'JP', dial: '81', boxes: [[30, 41.6, 129.7, 142.2], [41.4, 45.6, 139.3, 145.9], [24, 28.5, 122.9, 131.5]] },
  { code: 'KR', dial: '82', boxes: [[33, 38.7, 125, 129.6]] },
  { code: 'SG', dial: '65', boxes: [[1.15, 1.48, 103.6, 104.1]] },
  { code: 'US', dial: '1', boxes: [[24.5, 49.5, -125, -66.9], [51, 71.5, -170, -130], [18.8, 22.3, -160.5, -154.7]] },
  { code: 'CA', dial: '1' },
  { code: 'GB', dial: '44', boxes: [[49.9, 60.9, -8.2, 1.8]] },
  { code: 'AU', dial: '61', boxes: [[-44, -10.5, 113, 154]] },
  { code: 'FR', dial: '33', boxes: [[41, 51.2, -5.2, 9.6]] },
  { code: 'DE', dial: '49', boxes: [[47.3, 55.1, 5.9, 15]] },
  { code: 'MY', dial: '60' },
  { code: 'TH', dial: '66' },
  { code: 'VN', dial: '84' },
  { code: 'IN', dial: '91' },
  { code: 'NZ', dial: '64' },
];

export const DEFAULT_COUNTRY = 'CN';

export const dialOf = (code) => COUNTRIES.find((c) => c.code === code)?.dial || '86';

export function countryAt(latitude, longitude) {
  const hit = COUNTRIES.find((c) => c.boxes?.some(([a, b, x, y]) => latitude >= a && latitude <= b && longitude >= x && longitude <= y));
  return hit ? hit.code : null;
}

// Builds the international number the API expects. A leading "+" means the user already typed the full number.
export function toInternational(code, input) {
  const raw = input.replace(/[\s\-()]/g, '');
  if (raw.startsWith('+')) return raw;
  return `+${dialOf(code)}${raw.replace(/^0/, '')}`;
}
