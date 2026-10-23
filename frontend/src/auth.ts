import Keycloak from 'keycloak-js';

/**
 * Keycloak login (Authorization Code + PKCE). Enabled when VITE_KEYCLOAK_URL is set and demo mode is off;
 * otherwise the app runs without login (backend profile "local").
 */
export interface AuthUser {
  name: string;
  roles: string[];
}

const url = import.meta.env.VITE_KEYCLOAK_URL as string | undefined;
export const authEnabled = !!url && import.meta.env.VITE_USE_MOCKS !== 'true';

const ROLES = ['ADMIN', 'SALES_MANAGER', 'ACCOUNT_MANAGER', 'ACCOUNTANT', 'PROJECT_MANAGER', 'FOREMAN', 'WAREHOUSE_KEEPER'];

let keycloak: Keycloak | null = null;

/** Pages opened by outside parties through a private link: no Keycloak, the link token is the credential. */
export const isPublicPath = (path: string) => path.startsWith('/offer/');

export async function initAuth(): Promise<AuthUser | null> {
  if (!authEnabled || isPublicPath(window.location.pathname)) return null;
  keycloak = new Keycloak({
    url: url!,
    realm: (import.meta.env.VITE_KEYCLOAK_REALM as string | undefined) ?? 'aleruan',
    clientId: (import.meta.env.VITE_KEYCLOAK_CLIENT as string | undefined) ?? 'dealos-web',
  });
  await keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });
  return currentUser();
}

export function currentUser(): AuthUser | null {
  const parsed = keycloak?.tokenParsed as
    | { preferred_username?: string; name?: string; realm_access?: { roles?: string[] } }
    | undefined;
  if (!parsed) return null;
  return {
    name: parsed.name ?? parsed.preferred_username ?? '',
    roles: (parsed.realm_access?.roles ?? []).filter((r) => ROLES.includes(r)),
  };
}

/** A fresh access token for API calls (refreshed when it expires within 30 s), or null without login. */
export async function accessToken(): Promise<string | null> {
  if (!keycloak) return null;
  try {
    await keycloak.updateToken(30);
  } catch {
    await keycloak.login();
  }
  return keycloak.token ?? null;
}

export function logout() {
  keycloak?.logout({ redirectUri: window.location.origin });
}
