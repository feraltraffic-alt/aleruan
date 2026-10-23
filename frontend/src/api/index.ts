import { httpApi } from './http';
import type { Api } from './types';

export const usingMocks = import.meta.env.VITE_USE_MOCKS === 'true';

/**
 * The API the screens use. In demo mode the in-memory mock replaces the methods before the first
 * render (see installMocks); it is loaded on demand, so the demo data never ships in a production build.
 */
export const api: Api = { ...httpApi };

export async function installMocks(): Promise<void> {
  const { createMockApi } = await import('./mock');
  Object.assign(api, createMockApi());
}

export * from './types';
export { ApiError } from './http';
