/**
 * Loads the Google Maps JavaScript API once, with the key served by the backend (/api/config).
 * Needs the Maps JavaScript API and Geocoding API enabled for the key.
 */
let loading: Promise<void> | null = null;
let authFailed = false;
const authListeners = new Set<() => void>();

declare global {
  interface Window {
    __dealosMapsReady?: () => void;
    gm_authFailure?: () => void;
  }
}

export function loadGoogleMaps(key: string, language: string): Promise<void> {
  const g = (globalThis as { google?: { maps?: { importLibrary?: unknown } } }).google;
  if (typeof g?.maps?.importLibrary === 'function') return Promise.resolve();
  if (!loading) {
    loading = new Promise<void>((resolve, reject) => {
      window.__dealosMapsReady = () => resolve();
      // Google calls this when the key is wrong, restricted to other sites, or billing is off.
      window.gm_authFailure = () => {
        authFailed = true;
        authListeners.forEach((l) => l());
      };
      const s = document.createElement('script');
      s.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&v=weekly&loading=async`
        + `&language=${encodeURIComponent(language)}&callback=__dealosMapsReady`;
      s.async = true;
      s.onerror = () => { loading = null; s.remove(); reject(new Error('Google Maps did not load')); };
      document.head.appendChild(s);
    });
  }
  return loading;
}

/** Subscribes to Google's "key rejected" signal; returns an unsubscribe function. */
export function onMapsAuthFailure(listener: () => void): () => void {
  if (authFailed) listener();
  authListeners.add(listener);
  return () => authListeners.delete(listener);
}

/** Google's own demo map id: allows advanced markers without creating a Map ID in the Cloud console. */
export const FALLBACK_MAP_ID = 'DEMO_MAP_ID';
