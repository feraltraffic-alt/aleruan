import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import { installMocks } from '../api';

await installMocks();

afterEach(() => cleanup());
