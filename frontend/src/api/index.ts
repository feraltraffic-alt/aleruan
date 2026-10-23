import { httpApi } from './http';
import { createMockApi } from './mock';
import type { Api } from './types';

export const usingMocks = import.meta.env.VITE_USE_MOCKS === 'true';

export const api: Api = usingMocks ? createMockApi() : httpApi;
export * from './types';
export { ApiError } from './http';
