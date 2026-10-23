import { useState } from 'react';
import { can } from '../permissions';
import { Link, useParams } from 'react-router-dom';
import { api, type Rfq, type RfqInvitation } from '../api';
import { Drawer } from '../components/Drawer';
import { LoadError } from '../components/LoadError';
import { Status } from '../components/Status';
import { RfqStatusTag } from '../components/dealBits';
import { useAction } from '../components/useAction';
import { useLoad } from '../components/useLoad';
import { useI18n } from '../i18n';

export function RfqDetailPage() {
  const { id = '' } = useParams();
  const { t, date, day, money, num } = useI18n();
  const rfq = useLoad(() => api.getRfq(id), [id]);
  const action = useAction();
  const [inviting, setInviting] = useState(false);
  const [confirmAward, setConfirmAward] = useState<string | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);

  if (rfq.error) return <LoadError message={rfq.error} onRetry={rfq.reload} />;
  const r = rfq.data;
  if (!r) return <p className="empty" aria-busy="true">{t('loading')}</p>;

  const open = r.status === 'OPEN';
  const offers = r.invitations.filter((i) => i.offerSubmittedAt);
  const award = async (inv: RfqInvitation) => {
    if (confirmAward !== inv.id) { setConfirmAward(inv.id); return; }
    setConfirmAward(null);
    const next = await action.run(() => api.award(r.id, inv.id));
    if (next) rfq.setData(next);
  };
  const cancel = async () => {
    if (!confirmCancel) { setConfirmCancel(true); return; }
    setConfirmCancel(false);
    const next = await action.run(() => api.cancelRfq(r.id));
    if (next) rfq.setData(next);
  };

  return (
    <>
      <Link to={`/deals/${r.dealId}`} className="crumb">{t('rfq.back', { deal: r.dealNumber ?? '' })}</Link>
      <div className="page-head">
        <div>
          <h1>{r.title}</h1>
          <p className="lead">
            <RfqStatusTag status={r.status} />{' '}
            {r.deadline ? t('rfq.until', { date: day(r.deadline) }) : t('rfq.noDeadline')}
          </p>
        </div>
        {open && can('tenders.write') && (
          <div className="moves">
            <button className="btn primary" onClick={() => setInviting(true)}>{t('rfq.invite')}</button>
            <button className="btn" onClick={cancel} disabled={action.busy}>
              {confirmCancel ? t('deal.confirm', { what: t('rfq.cancel') }) : t('rfq.cancel')}
            </button>
          </div>
        )}
      </div>
      {action.error && <p className="note bad" role="alert">{action.error}</p>}
      {r.status === 'AWARDED' && <p className="note">{t('rfq.awardedNote')}</p>}

      <section className="section">
        <h2>{t('rfq.compare')}</h2>
        <div className="panel table-wrap">
          {offers.length === 0 ? (
            <p className="empty">{r.invitations.length ? t('rfq.waiting') : t('rfq.noInvites')}</p>
          ) : (
            <table className="compare">
              <thead>
                <tr>
                  <th>{t('line.description')}</th>
                  <th className="num">{t('line.quantity')}</th>
                  {offers.map((o) => (
                    <th key={o.id} className={`num${o.id === r.awardedInvitationId ? ' won' : ''}`}>
                      {o.subcontractor.legalName}
                      <span className="sub"><Status status={o.subcontractor.checkStatus} /></span>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {r.items.map((it) => (
                  <tr key={it.id}>
                    <td className="name-cell">{it.description}</td>
                    <td className="num">{num(it.quantity, 4)} {it.unit}</td>
                    {offers.map((o) => (
                      <td key={o.id} className={`num${it.cheapestInvitationId === o.id ? ' best' : ''}`}>
                        {o.prices[it.id] != null ? money(o.prices[it.id]) : <span className="sub">{t('rfq.noPrice')}</span>}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th colSpan={2}>{t('rfq.total', { cur: r.currency ?? '' })}</th>
                  {offers.map((o) => (
                    <td key={o.id} className={`num total${r.cheapestInvitationId === o.id ? ' best' : ''}`}>
                      <b>{money(o.total)}</b>
                      {!o.complete && <span className="sub">{t('rfq.incomplete')}</span>}
                      {r.cheapestInvitationId === o.id && <span className="sub">{t('rfq.cheapest')}</span>}
                      {o.offerNote && <span className="sub offer-note">«{o.offerNote}»</span>}
                      {open && can('tenders.write') && (
                        <button className="btn award" disabled={action.busy} onClick={() => award(o)}>
                          {confirmAward === o.id ? t('rfq.awardConfirm') : t('rfq.award')}
                        </button>
                      )}
                      {o.id === r.awardedInvitationId && <span className="pill r-AWARDED">{t('rfq.winner')}</span>}
                    </td>
                  ))}
                </tr>
              </tfoot>
            </table>
          )}
        </div>
      </section>

      <section className="section">
        <h2>{t('rfq.invitations')}</h2>
        <div className="panel table-wrap">
          {r.invitations.length === 0 ? <p className="empty">{t('rfq.noInvites')}</p> : (
            <table>
              <thead><tr><th>{t('cp.col.name')}</th><th>{t('rfq.col.invitedAt')}</th><th>{t('rfq.col.progress')}</th></tr></thead>
              <tbody>
                {r.invitations.map((i) => (
                  <tr key={i.id}>
                    <td className="name-cell">
                      <Link to={`/counterparties/${i.subcontractor.id}`}>{i.subcontractor.legalName}</Link>
                      <span className="sub">{i.subcontractor.country} <Status status={i.subcontractor.checkStatus} /></span>
                    </td>
                    <td>{date(i.invitedAt)}</td>
                    <td>
                      {i.offerSubmittedAt ? t('rfq.state.submitted', { date: date(i.offerSubmittedAt) })
                        : i.viewedAt ? t('rfq.state.viewed', { date: date(i.viewedAt) }) : t('rfq.state.sent')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      {inviting && <InviteForm rfq={r} onClose={() => setInviting(false)} onInvited={() => rfq.reload()} />}
    </>
  );
}

function InviteForm({ rfq, onClose, onInvited }: { rfq: Rfq; onClose(): void; onInvited(): void }) {
  const { t } = useI18n();
  const workTypes = [...new Set(rfq.items.map((i) => i.workType).filter((w): w is string => !!w))];
  const [byWorkType, setByWorkType] = useState(workTypes.length === 1);
  const candidates = useLoad(() => api.rfqCandidates(byWorkType ? workTypes[0] : undefined), [byWorkType]);
  const [allowRisky, setAllowRisky] = useState(false);
  const [link, setLink] = useState<{ company: string; url: string } | null>(null);
  const [copied, setCopied] = useState(false);
  const action = useAction();
  const invited = new Set(rfq.invitations.map((i) => i.subcontractor.id));

  const invite = async (id: string, company: string) => {
    const res = await action.run(() => api.invite(rfq.id, id, allowRisky));
    if (!res) return;
    setLink({ company, url: `${window.location.origin}${res.offerPath}` });
    setCopied(false);
    onInvited();
  };
  const copy = async () => {
    if (!link) return;
    try { await navigator.clipboard.writeText(link.url); setCopied(true); } catch { setCopied(false); }
  };

  return (
    <Drawer title={t('rfq.invite')} onClose={onClose} wide>
      {link ? (
        <div className="linkbox">
          <p>{t('rfq.linkFor', { company: link.company })}</p>
          <div className="fill-row">
            <input className="input" readOnly value={link.url} aria-label={t('rfq.link')}
              onFocus={(e) => e.currentTarget.select()} />
            <button className="btn primary" onClick={copy}>{copied ? t('rfq.copied') : t('rfq.copy')}</button>
          </div>
          <p className="note">{t('rfq.linkOnce')}</p>
          <div className="actions">
            <button className="btn" onClick={() => setLink(null)}>{t('rfq.inviteMore')}</button>
            <button className="btn" onClick={onClose}>{t('rfq.done')}</button>
          </div>
        </div>
      ) : (
        <>
          {workTypes.length === 1 && (
            <label className="toggle">
              <input type="checkbox" checked={byWorkType} onChange={(e) => setByWorkType(e.target.checked)} />
              {t('rfq.onlyWorkType')}
            </label>
          )}
          <label className="toggle">
            <input type="checkbox" checked={allowRisky} onChange={(e) => setAllowRisky(e.target.checked)} />
            {t('rfq.allowRisky')}
          </label>
          {action.error && <p className="note bad" role="alert">{action.error}</p>}
          {candidates.error ? <LoadError message={candidates.error} onRetry={candidates.reload} /> : (
            <ul className="candidates" aria-busy={candidates.loading}>
              {candidates.data?.length === 0 && <li className="sub">{t('rfq.noCandidates')}</li>}
              {candidates.data?.map((c) => {
                const blocked = c.checkStatus === 'RED' && !allowRisky;
                return (
                  <li key={c.id}>
                    <span>
                      <b>{c.legalName}</b>
                      <span className="sub">{c.country} <Status status={c.checkStatus} /></span>
                    </span>
                    {invited.has(c.id) ? <span className="sub">{t('rfq.alreadyInvited')}</span> : (
                      <button className="btn" disabled={action.busy || blocked} onClick={() => invite(c.id, c.legalName)}
                        title={blocked ? t('rfq.redBlocked') : undefined}>
                        {t('rfq.inviteOne')}
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}
    </Drawer>
  );
}
