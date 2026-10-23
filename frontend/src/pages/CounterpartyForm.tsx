import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, type Counterparty, type CounterpartyRole, type NewCounterparty } from '../api';
import { companyIdError } from '../identifiers';
import { useI18n } from '../i18n';
import { validate, type Errors } from './validateCounterparty';
import { ROLES } from '../constants';

interface Props {
  onClose(): void;
  onCreated(c: Counterparty): void;
}


export function CounterpartyForm({ onClose, onCreated }: Props) {
  const { t } = useI18n();
  const [form, setForm] = useState<NewCounterparty>({
    legalName: '', country: 'CZ', companyId: '', vatNumber: '', address: '', email: '', phone: '', roles: [],
  });
  const [errors, setErrors] = useState<Errors>({});
  const [note, setNote] = useState<{ text: string; bad?: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const first = useRef<HTMLInputElement>(null);

  useEffect(() => {
    first.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const set = <K extends keyof NewCounterparty>(k: K, v: NewCounterparty[K]) => {
    setForm((f) => ({ ...f, [k]: v }));
    setErrors((e) => (k in e ? { ...e, [k]: undefined } : e));
  };
  const toggleRole = (r: CounterpartyRole) =>
    set('roles', form.roles.includes(r) ? form.roles.filter((x) => x !== r) : [...form.roles, r]);

  const fill = async () => {
    const idErr = companyIdError(form.country.toUpperCase(), form.companyId ?? '');
    if (idErr) { setErrors({ ...errors, companyId: idErr }); return; }
    setBusy(true);
    setNote(null);
    try {
      const found = await api.lookup(form.country.toUpperCase(), form.companyId || undefined, form.vatNumber || undefined);
      if (!found) { setNote({ text: t('form.notFound') }); return; }
      setForm((f) => ({
        ...f,
        legalName: found.legalName ?? f.legalName,
        address: found.address ?? f.address,
        vatNumber: found.vatNumber ?? f.vatNumber,
      }));
      setNote({ text: t('form.filled', { source: found.source }) });
    } catch (e) {
      setNote({ text: e instanceof Error ? e.message : String(e), bad: true });
    } finally {
      setBusy(false);
    }
  };

  const submit = async (ev: FormEvent) => {
    ev.preventDefault();
    const e = validate(form);
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    setNote(null);
    try {
      const created = await api.createCounterparty({ ...form, country: form.country.toUpperCase() });
      await api.runChecks(created.id).catch(() => undefined); // the card shows check errors itself
      onCreated(created);
    } catch (err) {
      setNote({ text: err instanceof Error ? err.message : String(err), bad: true });
    } finally {
      setBusy(false);
    }
  };

  const err = (k: keyof Errors) => errors[k] && <span className="err" id={`err-${k}`}>{t(errors[k]!)}</span>;

  return (
    <div className="scrim" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form className="drawer" role="dialog" aria-modal="true" aria-labelledby="cp-form-title" onSubmit={submit} noValidate>
        <h2 id="cp-form-title">{t('form.title')}</h2>
        <div className="row2">
          <div className="field">
            <label htmlFor="f-country">{t('form.country')}</label>
            <input id="f-country" ref={first} className="input" maxLength={2} value={form.country}
              aria-invalid={!!errors.country} onChange={(e) => set('country', e.target.value.toUpperCase())} />
          </div>
          <div className="field">
            <label htmlFor="f-id">{t('form.companyId')}</label>
            <div className="fill-row">
              <input id="f-id" className="input" value={form.companyId} inputMode="numeric"
                aria-invalid={!!errors.companyId} aria-describedby={errors.companyId ? 'err-companyId' : undefined}
                onChange={(e) => set('companyId', e.target.value)} />
              <button type="button" className="btn" onClick={fill} disabled={busy || !form.companyId}>{t('form.fill')}</button>
            </div>
          </div>
        </div>
        {err('country')}{err('companyId')}
        {note && <p className={`note${note.bad ? ' bad' : ''}`} role="status">{note.text}</p>}
        <div className="field">
          <label htmlFor="f-name">{t('form.legalName')}</label>
          <input id="f-name" className="input" value={form.legalName} aria-invalid={!!errors.legalName}
            onChange={(e) => set('legalName', e.target.value)} />
          {err('legalName')}
        </div>
        <div className="field">
          <label htmlFor="f-vat">{t('form.vat')}</label>
          <input id="f-vat" className="input" value={form.vatNumber} aria-invalid={!!errors.vatNumber}
            onChange={(e) => set('vatNumber', e.target.value)} />
          {err('vatNumber')}
        </div>
        <div className="field">
          <label htmlFor="f-addr">{t('form.address')}</label>
          <input id="f-addr" className="input" value={form.address} onChange={(e) => set('address', e.target.value)} />
        </div>
        <div className="row2" style={{ gridTemplateColumns: '1fr 1fr' }}>
          <div className="field">
            <label htmlFor="f-email">{t('form.email')}</label>
            <input id="f-email" type="email" className="input" value={form.email} onChange={(e) => set('email', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="f-phone">{t('form.phone')}</label>
            <input id="f-phone" className="input" value={form.phone} onChange={(e) => set('phone', e.target.value)} />
          </div>
        </div>
        <fieldset className="field">
          <legend>{t('form.roles')}</legend>
          <div className="checks">
            {ROLES.map((r) => (
              <label key={r}>
                <input type="checkbox" checked={form.roles.includes(r)} onChange={() => toggleRole(r)} />
                {t(`role.${r}`)}
              </label>
            ))}
          </div>
          {err('roles')}
        </fieldset>
        <div className="actions">
          <button type="submit" className="btn primary" disabled={busy}>{t('form.save')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </div>
  );
}
