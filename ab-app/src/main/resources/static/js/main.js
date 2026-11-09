import { t, getLang, onLangChange } from './i18n/index.js';
import { loadMeta } from './store.js';
import { addRoute, parseHash, matchRoute, start } from './router.js';
import { createTabbar } from './ui/tabbar.js';
import { closeAllOverlays } from './ui/modal.js';
import { skeleton, errorBlock } from './ui/layout.js';
import { clear } from './ui/dom.js';
import { isLoggedIn } from './auth.js';
import { setFamily, onScopeChange } from './scope.js';
import * as api from './api.js';

addRoute('/home', () => import('./pages/home.js'), { tab: 'home' });
addRoute('/scan', () => import('./pages/scan.js'), { tab: 'scan' });
addRoute('/receipt/new', () => import('./pages/receipt-form.js'), { tab: 'scan', hideTabbar: true });
addRoute('/receipt/:id/edit', () => import('./pages/receipt-form.js'), { tab: 'home', hideTabbar: true });
addRoute('/receipt/:id', () => import('./pages/receipt-detail.js'), { tab: 'home' });
addRoute('/analytics', () => import('./pages/analytics.js'), { tab: 'analytics' });
addRoute('/library', () => import('./pages/library.js'), { tab: 'library' });
addRoute('/merchant/new', () => import('./pages/merchant-edit.js'), { tab: 'library', hideTabbar: true });
addRoute('/merchant/:id', () => import('./pages/merchant-edit.js'), { tab: 'library', hideTabbar: true });
addRoute('/product/new', () => import('./pages/product-edit.js'), { tab: 'library', hideTabbar: true });
addRoute('/product/:id', () => import('./pages/product-edit.js'), { tab: 'library', hideTabbar: true });
addRoute('/login', () => import('./pages/login.js'), { tab: 'settings', hideTabbar: true });
addRoute('/profile', () => import('./pages/profile.js'), { tab: 'settings' });
addRoute('/family', () => import('./pages/family.js'), { tab: 'settings' });
addRoute('/settings', () => import('./pages/settings.js'), { tab: 'settings' });

const app = document.getElementById('app');
const view = document.getElementById('view');
const tabbar = createTabbar();
app.append(tabbar.el);

let token = 0;
let cleanups = [];

async function show(reused) {
  const my = ++token;
  const scrollY = window.scrollY;
  closeAllOverlays();
  cleanups.forEach((fn) => fn());
  const local = [];
  cleanups = local;

  const { path, query } = parseHash();
  const found = matchRoute(path);
  if (!found) { location.replace('#/home'); return; }
  const { route, params } = found;
  const loggedIn = isLoggedIn();
  if (!loggedIn && path !== '/login') { location.replace('#/login'); return; }
  if (loggedIn && path === '/login' && !query.ticket && !query.error) { location.replace('#/home'); return; }
  tabbar.setActive(route.tab);
  tabbar.el.hidden = Boolean(route.hideTabbar);
  document.body.classList.toggle('no-tabbar', Boolean(route.hideTabbar));
  clear(view).append(skeleton(4));

  try {
    const mod = await route.load();
    const node = await mod.render({ params, query, reused, onCleanup: (fn) => local.push(fn), reload: () => show(true) });
    if (my !== token) { local.forEach((fn) => fn()); return; }
    clear(view).append(node);
    window.scrollTo(0, reused ? scrollY : 0);
  } catch (e) {
    if (my !== token) return;
    clear(view).append(errorBlock(e, () => show(reused)));
  }
}

function applyLang() {
  document.documentElement.lang = getLang();
  document.title = t('app.title');
}

async function boot() {
  applyLang();
  await Promise.race([loadMeta(), new Promise((resolve) => setTimeout(resolve, 3000))]);
  if (isLoggedIn()) {
    try { setFamily(await api.myFamily()); } catch { setFamily(null); }
  }
  onScopeChange(() => show(true));
  onLangChange(() => {
    applyLang();
    tabbar.refresh();
    show(true);
  });
  document.getElementById('boot')?.remove();
  start(show);
}

boot();
