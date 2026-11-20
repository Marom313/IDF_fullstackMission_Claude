import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { subscribeEvents } from '../shared/api/http';
import { useAuth } from '../shared/auth/AuthContext';
import { NAV } from '../shared/auth/nav';
import { LANGUAGES } from '../shared/i18n/i18n';
import { Logo } from '../shared/ui/Logo';
import s from './Shell.module.scss';

function useTheme() {
  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    try {
      return (localStorage.getItem('ipp.theme') as 'light' | 'dark' | null)
        ?? (window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    } catch {
      return 'light';
    }
  });
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem('ipp.theme', theme);
    } catch {
      /* ignore */
    }
  }, [theme]);
  return [theme, setTheme] as const;
}

/** Live updates: SSE "something changed" → invalidate the related queries (no stale data on screen). */
function useLiveInvalidation(enabled: boolean) {
  const qc = useQueryClient();
  useEffect(() => {
    if (!enabled) return undefined;
    return subscribeEvents((e) => {
      void qc.invalidateQueries({ queryKey: [e.kind === 'permit' ? 'permits' : 'enforcement'] });
      void qc.invalidateQueries({ queryKey: ['eligibility'] });
    });
  }, [enabled, qc]);
}

export function Shell() {
  const { t, i18n } = useTranslation();
  const { session, login, logout, hasAny } = useAuth();
  const [theme, setTheme] = useTheme();
  const location = useLocation();
  useLiveInvalidation(!!session);

  // move focus to the page heading on navigation (screen-reader friendly SPA routing)
  useEffect(() => {
    document.getElementById('page-title')?.focus();
  }, [location.pathname]);

  return (
    <>
      <a href="#main" className={s.skip}>{t('app.skip')}</a>
      <div className={s.banner}>{t('app.specimen')}</div>
      <header className={s.header}>
        <Link to="/" className={s.brand}>
          <Logo />
          <div>
            <strong>IPP</strong>
            <span>{t('app.subtitle')}</span>
          </div>
        </Link>
        {session && (
          <nav className={s.nav} aria-label={t('nav.main')}>
            <ul>
              {NAV.filter((n) => hasAny(...n.roles)).map((n) => (
                <li key={n.path}><NavLink to={n.path}>{t(n.labelKey)}</NavLink></li>
              ))}
            </ul>
          </nav>
        )}
        <div className={s.tools}>
          <label className="visually-hidden" htmlFor="lang">{t('app.language')}</label>
          <select id="lang" value={i18n.language} onChange={(e) => void i18n.changeLanguage(e.target.value)}>
            {LANGUAGES.map((l) => <option key={l.code} value={l.code} lang={l.code}>{l.label}</option>)}
          </select>
          <button type="button" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')} aria-pressed={theme === 'dark'}>
            {theme === 'dark' ? t('app.themeLight') : t('app.themeDark')}
          </button>
          {session ? (
            <>
              <span className={s.user}>{t('app.welcome', { name: session.username })}</span>
              <button type="button" onClick={() => void logout()}>{t('app.logout')}</button>
            </>
          ) : (
            <button type="button" onClick={() => void login()}>{t('app.login')}</button>
          )}
        </div>
      </header>
      <main id="main" className={s.main} tabIndex={-1}>
        <Outlet />
      </main>
      <footer className={s.footer}>
        <Link to="/accessibility">{t('nav.accessibility')}</Link> · {t('app.specimen')}
      </footer>
    </>
  );
}
