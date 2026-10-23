import { describe, expect, it } from 'vitest';
import { DICTS, LANGS, translate } from '../i18n';

describe('i18n', () => {
  it('every language has every key, none empty', () => {
    const keys = Object.keys(DICTS.ru);
    for (const lang of LANGS) {
      expect(Object.keys(DICTS[lang]).sort()).toEqual([...keys].sort());
      for (const k of keys) expect(DICTS[lang][k as keyof typeof DICTS.ru].trim()).not.toBe('');
    }
  });
  it('substitutes variables', () => {
    expect(translate('ru', 'dash.records', { n: 5 })).toBe('записей проверено: 5');
    expect(translate('cs', 'dash.chainBroken', { seq: 3 })).toContain('3');
  });
});
