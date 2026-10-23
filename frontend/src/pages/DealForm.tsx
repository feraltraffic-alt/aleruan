import { useState, type FormEvent } from 'react';
import { api, type Deal, type Direction, type NewDeal } from '../api';
import { Drawer } from '../components/Drawer';
import { CURRENCIES, DIRECTIONS } from '../components/dealData';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';
import { marginOnPrice } from '../numbers';

export function DealForm({ onClose, onCreated }: { onClose(): void; onCreated(d: Deal): void }) {
  const { t, num } = useI18n();
  const customers = useLoad(() => api.listCounterparties(undefined, 'CUSTOMER'), []);
  const [form, setForm] = useState({ title: '', customerId: '', direction: 'CONSTRUCTION' as Direction,
    siteCountry: '', currency: 'EUR', markup: '25' });
  const [errors, setErrors] = useState<Partial<Record<'title' | 'customerId' | 'siteCountry' | 'markup', string>>>({});
  const action = useAction();

  const set = (k: keyof typeof form, v: string) => {
    setForm((f) => ({ ...f, [k]: v }));
    setErrors((e) => ({ ...e, [k]: undefined }));
  };

  const markup = Number(form.markup.replace(',', '.'));
  const markupValid = form.markup.trim() !== '' && Number.isFinite(markup) && markup > -100;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: typeof errors = {};
    if (!form.title.trim()) errs.title = t('deal.form.titleRequired');
    if (!form.customerId) errs.customerId = t('deal.form.customerRequired');
    if (form.siteCountry && !/^[A-Za-z]{2}$/.test(form.siteCountry.trim())) errs.siteCountry = t('deal.form.countryFormat');
    if (!markupValid) errs.markup = t('deal.form.markupFormat');
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const input: NewDeal = { title: form.title.trim(), customerId: form.customerId, direction: form.direction,
      siteCountry: form.siteCountry.trim() || undefined, currency: form.currency, defaultMarkupPct: markup };
    const d = await action.run(() => api.createDeal(input));
    if (d) onCreated(d);
  };

  return (
    <Drawer title={t('deal.add')} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="d-title">{t('deal.form.title')}</label>
          <input id="d-title" className="input" value={form.title} onChange={(e) => set('title', e.target.value)}
            aria-invalid={!!errors.title} placeholder={t('deal.form.titleHint')} />
          {errors.title && <span className="err">{errors.title}</span>}
        </div>
        <div className="field">
          <label htmlFor="d-customer">{t('deal.form.customer')}</label>
          <select id="d-customer" className="select" value={form.customerId} aria-invalid={!!errors.customerId}
            onChange={(e) => set('customerId', e.target.value)}>
            <option value="">{t('deal.form.pickCustomer')}</option>
            {customers.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.legalName}{c.checkStatus === 'RED' ? ` — ${t('status.RED')}` : ''}
              </option>
            ))}
          </select>
          {errors.customerId && <span className="err">{errors.customerId}</span>}
          {customers.data?.length === 0 && <span className="sub">{t('deal.form.noCustomers')}</span>}
        </div>
        <div className="grid2">
          <div className="field">
            <label htmlFor="d-dir">{t('deal.form.direction')}</label>
            <select id="d-dir" className="select" value={form.direction}
              onChange={(e) => set('direction', e.target.value)}>
              {DIRECTIONS.map((d) => <option key={d} value={d}>{t(`dir.${d}`)}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="d-country">{t('deal.form.siteCountry')}</label>
            <input id="d-country" className="input" value={form.siteCountry} maxLength={2} placeholder="CZ"
              aria-invalid={!!errors.siteCountry} onChange={(e) => set('siteCountry', e.target.value.toUpperCase())} />
            {errors.siteCountry && <span className="err">{errors.siteCountry}</span>}
          </div>
          <div className="field">
            <label htmlFor="d-cur">{t('deal.form.currency')}</label>
            <select id="d-cur" className="select" value={form.currency} onChange={(e) => set('currency', e.target.value)}>
              {CURRENCIES.map((c) => <option key={c}>{c}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="d-markup">{t('deal.form.markup')}</label>
            <input id="d-markup" className="input" inputMode="decimal" value={form.markup}
              aria-invalid={!!errors.markup} onChange={(e) => set('markup', e.target.value)} />
            {errors.markup && <span className="err">{errors.markup}</span>}
          </div>
        </div>
        {markupValid && (
          <p className="note">{t('deal.form.markupExplain', { markup: num(markup, 2),
            margin: num(marginOnPrice(markup), 1) })}</p>
        )}
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{t('deal.form.create')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}
