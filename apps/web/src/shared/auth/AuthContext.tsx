import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { setCurrentSession, toSession, userManager, type Role, type Session } from './auth';

interface AuthState {
  session: Session | null;
  ready: boolean;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  hasAny: (...roles: Role[]) => boolean;
}

const Ctx = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const apply = (s: Session | null) => {
      setCurrentSession(s);
      setSession(s);
    };
    (async () => {
      try {
        if (window.location.pathname === '/callback') {
          const user = await userManager.signinRedirectCallback();
          apply(toSession(user));
          window.history.replaceState({}, '', (user.state as string | undefined) ?? '/');
        } else {
          apply(toSession(await userManager.getUser()));
        }
      } catch {
        apply(null);
      } finally {
        setReady(true);
      }
    })();
    const onLoaded = (u: Parameters<Parameters<typeof userManager.events.addUserLoaded>[0]>[0]) => apply(toSession(u));
    const onExpired = () => apply(null);
    userManager.events.addUserLoaded(onLoaded);
    userManager.events.addAccessTokenExpired(onExpired);
    return () => {
      userManager.events.removeUserLoaded(onLoaded);
      userManager.events.removeAccessTokenExpired(onExpired);
    };
  }, []);

  const login = useCallback(() => userManager.signinRedirect({ state: window.location.pathname }), []);
  const logout = useCallback(async () => {
    setCurrentSession(null);
    await userManager.signoutRedirect();
  }, []);
  const hasAny = useCallback((...roles: Role[]) => !!session && roles.some((r) => session.roles.includes(r)),
    [session]);

  const value = useMemo(() => ({ session, ready, login, logout, hasAny }), [session, ready, login, logout, hasAny]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthState {
  const v = useContext(Ctx);
  if (!v) throw new Error('useAuth outside AuthProvider');
  return v;
}
