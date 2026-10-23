import { NavLink, Outlet } from 'react-router-dom';
import { usingMocks } from '../api';
import { authEnabled, currentUser, logout } from '../auth';
import { LANGS, useI18n, type MessageKey } from '../i18n';

const LIVE: { to: string; key: MessageKey }[] = [
  { to: '/', key: 'nav.dashboard' },
  { to: '/counterparties', key: 'nav.counterparties' },
];
const UPCOMING: MessageKey[] = ['nav.deals', 'nav.projects', 'nav.invoices', 'nav.warehouse'];
const SYSTEM: { to: string; key: MessageKey }[] = [
  { to: '/audit', key: 'nav.audit' },
  { to: '/reference', key: 'nav.reference' },
];

export function Layout() {
  const { t, lang, setLang } = useI18n();
  const link = (l: { to: string; key: MessageKey }) => (
    <NavLink key={l.to} to={l.to} end={l.to === '/'}>{t(l.key)}</NavLink>
  );
  return (
    <div className="shell">
      <aside className="side">
        <NavLink to="/" className="brand" aria-label={t('app.name')}>
          <img src="/brand/sphere.png" alt="" />
          <span><b>Aleruan</b><small>Deal OS</small></span>
        </NavLink>
        <nav className="nav" aria-label="Main">
          {LIVE.map(link)}
          {UPCOMING.map((k) => (
            <span key={k} aria-disabled="true">{t(k)} <em>{t('nav.soon')}</em></span>
          ))}
          <hr />
          {SYSTEM.map(link)}
        </nav>
        {authEnabled && (
          <div className="side-user">
            <span>{currentUser()?.name}</span>
            <button onClick={logout}>{t('auth.logout')}</button>
          </div>
        )}
        <div className="side-foot" role="group" aria-label="Language">
          {LANGS.map((l) => (
            <button key={l} aria-pressed={lang === l} onClick={() => setLang(l)}>{l.toUpperCase()}</button>
          ))}
        </div>
      </aside>
      <main className="main">
        {usingMocks && <div className="demo">{t('demo.banner')}</div>}
        <Outlet />
      </main>
    </div>
  );
}
