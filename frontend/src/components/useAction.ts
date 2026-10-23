import { useState } from 'react';
import { FORBIDDEN } from '../api/http';
import { useI18n } from '../i18n';

/** Runs a mutation with a busy flag and a readable error; returns the result or undefined on failure. */
export function useAction() {
  const [busy, setBusy] = useState(false);
  const { t } = useI18n();
  const [error, setError] = useState<string | null>(null);
  const run = async <T,>(fn: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true);
    setError(null);
    try {
      return await fn();
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg === FORBIDDEN ? t('err.forbidden') : msg);
      return undefined;
    } finally {
      setBusy(false);
    }
  };
  return { busy, error, setError, run };
}
