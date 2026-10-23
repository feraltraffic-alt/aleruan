import { useState, type FormEvent } from 'react';
import { api, type Deal, type DealLine, type LineData, type LineKind } from '../api';
import { Drawer } from '../components/Drawer';
import { LINE_KINDS, useWorkTypes } from '../components/dealData';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';
import { parseNum } from '../numbers';
import { maxUnitCost, priceLine } from '../pricing';

type Mode = 'deal' | 'markup' | 'fixed';

interface Props { deal: Deal; line?: DealLine; onClose(): void; onSaved(d: Deal): void }

export function LineForm({ deal, line, onClose, onSaved }: Props) {
  const { t, money, num } = useI18n();
  const workTypes = useWorkTypes();
  const suppliers = useLoad(async () => (await api.listCounterparties())
    .filter((c) => c.roles.some((r) => r !== 'CUSTOMER')), []);
  const [f, setF] = useState({
    kind: (line?.kind ?? 'WORK') as LineKind,
    description: line?.description ?? '',
    workType: line?.workType ?? '',
    unit: line?.unit ?? '',
    quantity: line ? String(line.quantity) : '',
    unitCost: line ? String(line.unitCost) : '',
    mode: (line?.fixedUnitPrice != null ? 'fixed' : line && !line.markupFromDeal ? 'markup' : 'deal') as Mode,
    markupPct: line && !line.markupFromDeal && line.markupPct != null ? String(line.markupPct) : '',
    fixedUnitPrice: line?.fixedUnitPrice != null ? String(line.fixedUnitPrice) : '',
    subcontractorId: line?.subcontractor?.id ?? '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const action = useAction();
  const set = (k: keyof typeof f, v: string) => {
    setF((x) => ({ ...x, [k]: v }));
    setErrors((e) => ({ ...e, [k]: '' }));
  };

  const quantity = parseNum(f.quantity);
  const unitCost = parseNum(f.unitCost);
  const markupPct = f.mode === 'markup' ? parseNum(f.markupPct) : null;
  const fixedUnitPrice = f.mode === 'fixed' ? parseNum(f.fixedUnitPrice) : null;
  const ready = quantity > 0 && unitCost >= 0 && (markupPct == null || markupPct > -100)
    && (fixedUnitPrice == null || fixedUnitPrice >= 0);
  const preview = ready ? priceLine({ quantity, unitCost, markupPct, fixedUnitPrice }, deal.defaultMarkupPct) : null;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!f.description.trim()) errs.description = t('line.err.description');
    if (!(quantity > 0)) errs.quantity = t('line.err.quantity');
    if (!(unitCost >= 0)) errs.unitCost = t('line.err.unitCost');
    if (f.mode === 'markup' && !(markupPct! > -100)) errs.markupPct = t('deal.form.markupFormat');
    if (f.mode === 'fixed' && !(fixedUnitPrice! >= 0)) errs.fixedUnitPrice = t('line.err.price');
    setErrors(errs);
    if (Object.values(errs).some(Boolean)) return;
    const data: LineData = {
      kind: f.kind, description: f.description.trim(), workType: f.workType || null, unit: f.unit.trim() || null,
      quantity, unitCost, markupPct, fixedUnitPrice, subcontractorId: f.subcontractorId || null,
    };
    const d = await action.run(() => (line ? api.updateLine(deal.id, line.id, data) : api.addLine(deal.id, data)));
    if (d) onSaved(d);
  };

  const err = (k: string) => errors[k] ? <span className="err">{errors[k]}</span> : null;

  return (
    <Drawer title={line ? t('line.edit') : t('line.add')} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="grid2">
          <div className="field">
            <label htmlFor="l-kind">{t('line.kind')}</label>
            <select id="l-kind" className="select" value={f.kind} onChange={(e) => set('kind', e.target.value)}>
              {LINE_KINDS.map((k) => <option key={k} value={k}>{t(`kind.${k}`)}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="l-wt">{t('line.workType')}</label>
            <select id="l-wt" className="select" value={f.workType} onChange={(e) => set('workType', e.target.value)}>
              <option value="">—</option>
              {workTypes.list.map((w) => <option key={w.code} value={w.code}>{workTypes.name(w.code)}</option>)}
            </select>
          </div>
        </div>
        <div className="field">
          <label htmlFor="l-desc">{t('line.description')}</label>
          <input id="l-desc" className="input" value={f.description} aria-invalid={!!errors.description}
            onChange={(e) => set('description', e.target.value)} />
          {err('description')}
        </div>
        <div className="grid3">
          <div className="field">
            <label htmlFor="l-qty">{t('line.quantity')}</label>
            <input id="l-qty" className="input" inputMode="decimal" value={f.quantity} aria-invalid={!!errors.quantity}
              onChange={(e) => set('quantity', e.target.value)} />
            {err('quantity')}
          </div>
          <div className="field">
            <label htmlFor="l-unit">{t('line.unit')}</label>
            <input id="l-unit" className="input" value={f.unit} placeholder="m2" onChange={(e) => set('unit', e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="l-cost">{t('line.unitCost', { cur: deal.currency })}</label>
            <input id="l-cost" className="input" inputMode="decimal" value={f.unitCost} aria-invalid={!!errors.unitCost}
              onChange={(e) => set('unitCost', e.target.value)} />
            {err('unitCost')}
          </div>
        </div>
        <fieldset className="field">
          <legend>{t('line.pricing')}</legend>
          <div className="checks">
            {(['deal', 'markup', 'fixed'] as Mode[]).map((m) => (
              <label key={m}>
                <input type="radio" name="mode" checked={f.mode === m} onChange={() => set('mode', m)} />
                {m === 'deal' ? t('line.mode.deal', { pct: num(deal.defaultMarkupPct) }) : t(`line.mode.${m}`)}
              </label>
            ))}
          </div>
        </fieldset>
        {f.mode === 'markup' && (
          <div className="field">
            <label htmlFor="l-markup">{t('deal.form.markup')}</label>
            <input id="l-markup" className="input" inputMode="decimal" value={f.markupPct}
              aria-invalid={!!errors.markupPct} onChange={(e) => set('markupPct', e.target.value)} />
            {err('markupPct')}
          </div>
        )}
        {f.mode === 'fixed' && (
          <div className="field">
            <label htmlFor="l-fixed">{t('line.fixedPrice', { cur: deal.currency })}</label>
            <input id="l-fixed" className="input" inputMode="decimal" value={f.fixedUnitPrice}
              aria-invalid={!!errors.fixedUnitPrice} onChange={(e) => set('fixedUnitPrice', e.target.value)} />
            {err('fixedUnitPrice')}
            {fixedUnitPrice != null && fixedUnitPrice >= 0 && (
              <span className="sub">{t('line.maxCost', { pct: num(deal.defaultMarkupPct),
                cost: money(maxUnitCost(fixedUnitPrice, deal.defaultMarkupPct), deal.currency) })}</span>
            )}
          </div>
        )}
        <div className="field">
          <label htmlFor="l-sub">{t('line.subcontractor')}</label>
          <select id="l-sub" className="select" value={f.subcontractorId}
            onChange={(e) => set('subcontractorId', e.target.value)}>
            <option value="">{t('line.noSubcontractor')}</option>
            {suppliers.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.legalName}{c.checkStatus === 'RED' ? ` — ${t('status.RED')}` : ''}
              </option>
            ))}
          </select>
        </div>
        {preview && (
          <dl className={`preview${preview.margin < 0 ? ' bad' : ''}`} aria-live="polite">
            <div><dt>{t('line.unitPrice')}</dt><dd>{money(preview.unitPrice, deal.currency)}</dd></div>
            <div><dt>{t('line.priceTotal')}</dt><dd>{money(preview.priceTotal, deal.currency)}</dd></div>
            <div><dt>{t('totals.margin')}</dt><dd>{money(preview.margin, deal.currency)}</dd></div>
          </dl>
        )}
        {action.error && <p className="note bad" role="alert">{action.error}</p>}
        <div className="actions">
          <button className="btn primary" disabled={action.busy}>{line ? t('common.save') : t('line.add')}</button>
          <button type="button" className="btn" onClick={onClose}>{t('form.cancel')}</button>
        </div>
      </form>
    </Drawer>
  );
}
