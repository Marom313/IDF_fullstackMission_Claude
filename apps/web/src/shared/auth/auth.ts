import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';

/** OIDC Authorization Code + PKCE against the local Keycloak (same origin via NGINX /auth). */
export const userManager = new UserManager({
  authority: `${window.location.origin.replace(':5173', '')}/auth/realms/ipp`,
  client_id: 'ipp-web',
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid profile',
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
});

export const ROLES = ['WORKER', 'DRIVER', 'EMPLOYER', 'CROSSING_OFFICER', 'CLERK', 'POLICY_ADMIN',
  'ENFORCEMENT_OFFICER', 'AUDITOR', 'SYS_ADMIN'] as const;
export type Role = (typeof ROLES)[number];

export interface Session {
  username: string;
  name: string;
  roles: Role[];
  accessToken: string;
}

export function toSession(user: User | null): Session | null {
  if (!user || user.expired) return null;
  const payload = JSON.parse(atob(user.access_token.split('.')[1]!.replace(/-/g, '+').replace(/_/g, '/'))) as {
    realm_access?: { roles?: string[] };
    preferred_username?: string;
    given_name?: string;
  };
  const roles = (payload.realm_access?.roles ?? []).filter((r): r is Role => (ROLES as readonly string[]).includes(r));
  return {
    username: payload.preferred_username ?? 'unknown',
    name: payload.given_name ?? payload.preferred_username ?? '',
    roles,
    accessToken: user.access_token,
  };
}

let current: Session | null = null;
export const setCurrentSession = (s: Session | null) => {
  current = s;
};
export const currentToken = () => current?.accessToken ?? null;
