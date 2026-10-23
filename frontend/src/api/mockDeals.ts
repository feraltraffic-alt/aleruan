import type {
  Api, Candidate, Counterparty, Deal, DealLine, DealListItem, DealStatus, DealWarning, LineData, OfferPage, PartyRef,
  ChangeOrder, ChangeOrderStatus, Contract, Quote, Rfq, RfqInvitation,
} from './types';
import { ApiError } from './http';
import { priceLine, totals } from '../pricing';
import { languageFor, suggestVat, vatAmount } from '../vat';
import { addDays, localToday, mondayOf } from '../dates';
import { createSitesMock, type SitesApi } from './mockSites';
import { createInvoicesMock, type InvoicesApi } from './mockInvoices';

/** Deals and tenders for the demo API; mirrors the backend rules that the UI relies on. */

type DealsApi = Pick<Api, 'listDeals' | 'getDeal' | 'createDeal' | 'editDeal' | 'addLine' | 'updateLine' | 'removeLine'
  | 'moveDeal' | 'dealRfqs' | 'createRfq' | 'getRfq' | 'rfqCandidates' | 'invite' | 'award' | 'cancelRfq'
  | 'openOffer' | 'submitOffer' | 'quoteDefaults' | 'listQuotes' | 'issueQuote' | 'quoteFile' | 'changeOrders'
  | 'createChangeOrder' | 'getChangeOrder' | 'editChangeOrder' | 'addChangeLine' | 'updateChangeLine'
  | 'removeChangeLine' | 'changeOrderAction' | 'decideChangeOrder' | 'changeOrderFile'>;

interface StoredChangeOrder {
  id: string; dealId: string; seq: number; title: string; reason: string | null; status: ChangeOrderStatus;
  extraDays: number; sentAt: string | null; decidedAt: string | null; decidedBy: string | null;
  decisionNote: string | null; createdAt: string; lines: StoredLine[];
}

const CO_NEXT: Record<ChangeOrderStatus, ChangeOrderStatus[]> = {
  DRAFT: ['SENT', 'CANCELLED'], SENT: ['APPROVED', 'REJECTED', 'DRAFT', 'CANCELLED'], APPROVED: [], REJECTED: [], CANCELLED: [],
};

interface StoredLine extends LineData { id: string; position: number }
interface StoredDeal {
  id: string; number: string; title: string; status: DealStatus; direction: Deal['direction'];
  siteCountry: string | null; currency: string; defaultMarkupPct: number; customerId: string; notes: string | null;
  createdAt: string; lines: StoredLine[];
}
interface StoredInvitation {
  id: string; subcontractorId: string; token: string; invitedAt: string; viewedAt: string | null;
  offerSubmittedAt: string | null; offerNote: string | null; prices: Record<string, number>;
}
interface StoredRfq {
  id: string; dealId: string; title: string; deadline: string | null; status: Rfq['status'];
  awardedInvitationId: string | null; items: { id: string; dealLineId: string }[]; invitations: StoredInvitation[];
}

const NEXT: Record<DealStatus, DealStatus[]> = {
  DRAFT: ['QUOTED', 'CANCELLED'], QUOTED: ['DRAFT', 'WON', 'LOST', 'CANCELLED'], WON: ['IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['COMPLETED', 'CANCELLED'], LOST: [], COMPLETED: [], CANCELLED: [],
};
const pricesEditable = (s: DealStatus) => s === 'DRAFT' || s === 'QUOTED';
const costsEditable = (s: DealStatus) => !['LOST', 'COMPLETED', 'CANCELLED'].includes(s);
const MARGIN_WARNING_PCT = 10;
const uid = () => crypto.randomUUID();
const now = () => new Date().toISOString();

export function createDealsMock(
  counterparties: Counterparty[],
  record: (type: string, id: string, action: string, payload: unknown) => void,
  delay: <T>(v: T) => Promise<T>,
): DealsApi & SitesApi & InvoicesApi {
  const deals: StoredDeal[] = [];
  const rfqs: StoredRfq[] = [];
  const quotes: (Quote & { dealId: string; html: string })[] = [];
  const cos: StoredChangeOrder[] = [];
  let seq = 0;

  const party = (id: string | null | undefined): Counterparty | undefined => counterparties.find((c) => c.id === id);
  const ref = (c: Counterparty | undefined): PartyRef | null =>
    c ? { id: c.id, legalName: c.legalName, checkStatus: c.checkStatus } : null;
  const candidate = (c: Counterparty): Candidate => ({
    id: c.id, legalName: c.legalName, country: c.country, checkStatus: c.checkStatus, workTypes: c.workTypes ?? [],
  });
  const findDeal = (id: string) => {
    const d = deals.find((x) => x.id === id);
    if (!d) throw new ApiError(404, `Deal ${id} not found`);
    return d;
  };
  const findRfq = (id: string) => {
    const r = rfqs.find((x) => x.id === id);
    if (!r) throw new ApiError(404, `RFQ ${id} not found`);
    return r;
  };
  const requirePrices = (d: StoredDeal) => {
    if (!pricesEditable(d.status)) {
      throw new ApiError(409, `Prices of deal ${d.number} are frozen in status ${d.status}; use a change order`);
    }
  };
  const checkSupplier = (id: string | null | undefined) => {
    if (!id) return;
    const c = party(id);
    if (!c || !c.roles.some((r) => ['SUBCONTRACTOR', 'SUPPLIER', 'CARRIER'].includes(r))) {
      throw new ApiError(400, 'The line supplier must be a subcontractor, supplier or carrier');
    }
  };
  const priced = (d: StoredDeal, l: StoredLine) =>
    priceLine({ quantity: l.quantity, unitCost: l.unitCost, markupPct: l.markupPct ?? null,
      fixedUnitPrice: l.fixedUnitPrice ?? null }, d.defaultMarkupPct);

  const lineView = (d: StoredDeal, l: StoredLine): DealLine => {
    const p = priced(d, l);
    return {
      id: l.id, position: l.position, kind: l.kind, description: l.description, workType: l.workType ?? null,
      unit: l.unit ?? null, quantity: l.quantity, unitCost: l.unitCost, markupPct: p.markupPct,
      markupFromDeal: l.markupPct == null && l.fixedUnitPrice == null, fixedUnitPrice: l.fixedUnitPrice ?? null,
      unitPrice: p.unitPrice, costTotal: p.costTotal, priceTotal: p.priceTotal, margin: p.margin,
      subcontractor: ref(party(l.subcontractorId)),
    };
  };

  const contract = (d: StoredDeal): Contract => {
    const own = d.lines.map((l) => priced(d, l));
    const mine = cos.filter((c) => c.dealId === d.id);
    const approved = mine.filter((c) => c.status === 'APPROVED').flatMap((c) => c.lines.map((l) => priced(d, l)));
    const pending = totals(mine.filter((c) => c.status === 'SENT').flatMap((c) => c.lines.map((l) => priced(d, l)))).price;
    return { original: totals(own), approvedChanges: totals(approved), pendingPrice: pending,
      total: totals([...own, ...approved]),
      extraDays: mine.filter((c) => c.status === 'APPROVED').reduce((n, c) => n + c.extraDays, 0) };
  };

  const view = (d: StoredDeal): Deal => {
    const warnings: DealWarning[] = [];
    const customer = party(d.customerId);
    if (customer?.checkStatus === 'RED') warnings.push('CUSTOMER_RED');
    const lines: DealLine[] = d.lines.map((l) => {
      const p = priced(d, l);
      const sub = party(l.subcontractorId);
      if (sub?.checkStatus === 'RED' && !warnings.includes('SUBCONTRACTOR_RED')) warnings.push('SUBCONTRACTOR_RED');
      if (p.margin < 0 && !warnings.includes('LINE_BELOW_COST')) warnings.push('LINE_BELOW_COST');
      return {
        id: l.id, position: l.position, kind: l.kind, description: l.description, workType: l.workType ?? null,
        unit: l.unit ?? null, quantity: l.quantity, unitCost: l.unitCost, markupPct: p.markupPct,
        markupFromDeal: l.markupPct == null && l.fixedUnitPrice == null, fixedUnitPrice: l.fixedUnitPrice ?? null,
        unitPrice: p.unitPrice, costTotal: p.costTotal, priceTotal: p.priceTotal, margin: p.margin,
        subcontractor: ref(sub),
      };
    });
    const t = totals(d.lines.map((l) => priced(d, l)));
    const agreed = contract(d);
    if (agreed.total.marginPct != null && agreed.total.marginPct < MARGIN_WARNING_PCT) warnings.push('MARGIN_BELOW_THRESHOLD');
    return {
      id: d.id, number: d.number, title: d.title, status: d.status, nextStatuses: NEXT[d.status],
      direction: d.direction, siteCountry: d.siteCountry, currency: d.currency, defaultMarkupPct: d.defaultMarkupPct,
      pricesEditable: pricesEditable(d.status), customer: ref(customer), manager: 'demo', notes: d.notes,
      createdAt: d.createdAt, lines, totals: t, warnings, contract: agreed,
    };
  };

  const findCo = (id: string) => {
    const c = cos.find((x) => x.id === id);
    if (!c) throw new ApiError(404, `Change order ${id} not found`);
    return c;
  };
  const coView = (c: StoredChangeOrder): ChangeOrder => {
    const d = findDeal(c.dealId);
    const { lines, ...rest } = c;
    return { ...rest, number: `${d.number}-C${c.seq}`, nextStatuses: CO_NEXT[c.status],
      pricesEditable: c.status === 'DRAFT', createdBy: 'demo', lines: lines.map((l) => lineView(d, l)),
      totals: totals(lines.map((l) => priced(d, l))) };
  };
  const requireAlive = (d: StoredDeal) => {
    if (d.status !== 'WON' && d.status !== 'IN_PROGRESS') {
      throw new ApiError(409, `Change orders are for deals the customer accepted and that are not closed; ${d.number} is ${d.status}`);
    }
  };
  const coLine = (line: LineData) => {
    if (!line.description.trim()) throw new ApiError(400, 'A line needs a kind and a description');
    if (!line.quantity) throw new ApiError(400, 'Quantity must not be zero');
    checkSupplier(line.subcontractorId);
  };
  const moveCo = (c: StoredChangeOrder, to: ChangeOrderStatus) => {
    if (!CO_NEXT[c.status].includes(to)) throw new ApiError(409, `Change order ${c.seq} cannot move from ${c.status} to ${to}`);
    c.status = to;
    record('ChangeOrder', c.id, to, {});
  };

  const listItem = (d: StoredDeal): DealListItem => {
    const v = view(d);
    return { id: v.id, number: v.number, title: v.title, status: v.status, direction: v.direction,
      currency: v.currency, customer: v.customer, totals: v.totals, createdAt: v.createdAt };
  };

  const create = (input: Parameters<Api['createDeal']>[0]): StoredDeal => {
    const customer = party(input.customerId);
    if (!customer || !customer.roles.includes('CUSTOMER')) {
      throw new ApiError(400, 'The deal customer must have the CUSTOMER role');
    }
    seq += 1;
    const d: StoredDeal = {
      id: uid(), number: `D-${new Date().getFullYear()}-${String(seq).padStart(4, '0')}`, title: input.title.trim(),
      status: 'DRAFT', direction: input.direction, siteCountry: input.siteCountry?.toUpperCase() || null,
      currency: input.currency.toUpperCase(), defaultMarkupPct: input.defaultMarkupPct, customerId: input.customerId,
      notes: null, createdAt: now(), lines: [],
    };
    deals.push(d);
    record('Deal', d.id, 'CREATED', input);
    return d;
  };

  const assignCost = (d: StoredDeal, lineId: string, unitCost: number, subcontractorId: string) => {
    if (!costsEditable(d.status)) throw new ApiError(409, `Costs of deal ${d.number} are closed`);
    const l = d.lines.find((x) => x.id === lineId);
    if (!l) return;
    if (!pricesEditable(d.status) && l.fixedUnitPrice == null) l.fixedUnitPrice = priced(d, l).unitPrice;
    l.unitCost = unitCost;
    l.subcontractorId = subcontractorId;
    record('Deal', d.id, 'COST_ASSIGNED', { lineId, unitCost, subcontractorId });
  };

  const rfqView = (r: StoredRfq): Rfq => {
    const d = findDeal(r.dealId);
    const submitted = r.invitations.filter((i) => i.offerSubmittedAt);
    const complete = (i: StoredInvitation) => r.items.every((it) => i.prices[it.id] != null);
    const total = (i: StoredInvitation) =>
      r.items.reduce((s, it) => {
        const line = d.lines.find((l) => l.id === it.dealLineId);
        return s + (i.prices[it.id] ?? 0) * (line?.quantity ?? 0);
      }, 0);
    const cheapest = submitted.filter(complete).sort((a, b) => total(a) - total(b))[0];
    return {
      id: r.id, dealId: d.id, dealNumber: d.number, currency: d.currency, title: r.title, deadline: r.deadline,
      status: r.status, awardedInvitationId: r.awardedInvitationId, cheapestInvitationId: cheapest?.id ?? null,
      items: r.items.map((it) => {
        const line = d.lines.find((l) => l.id === it.dealLineId);
        const best = submitted.filter((i) => i.prices[it.id] != null).sort((a, b) => a.prices[it.id] - b.prices[it.id])[0];
        return { id: it.id, dealLineId: it.dealLineId, description: line?.description ?? '', unit: line?.unit ?? null,
          quantity: line?.quantity ?? 0, workType: line?.workType ?? null, cheapestInvitationId: best?.id ?? null };
      }),
      invitations: r.invitations.map((i): RfqInvitation => ({
        id: i.id, subcontractor: candidate(party(i.subcontractorId)!), invitedAt: i.invitedAt, viewedAt: i.viewedAt,
        offerSubmittedAt: i.offerSubmittedAt, offerNote: i.offerNote, prices: i.prices,
        total: i.offerSubmittedAt ? Math.round(total(i) * 100) / 100 : null, complete: complete(i),
      })),
    };
  };

  const byToken = (token: string) => {
    for (const r of rfqs) {
      const inv = r.invitations.find((i) => i.token === token);
      if (inv) return { r, inv };
    }
    throw new ApiError(404, 'This offer link is not valid');
  };
  const offerPage = (r: StoredRfq, inv: StoredInvitation): OfferPage => {
    const d = findDeal(r.dealId);
    return {
      title: r.title, invitedCompany: party(inv.subcontractorId)?.legalName ?? '', currency: d.currency,
      deadline: r.deadline, open: r.status === 'OPEN' && (!r.deadline || r.deadline >= now().slice(0, 10)),
      items: r.items.map((it) => {
        const l = d.lines.find((x) => x.id === it.dealLineId);
        return { id: it.id, description: l?.description ?? '', unit: l?.unit ?? null, quantity: l?.quantity ?? 0 };
      }),
      myPrices: inv.prices, myNote: inv.offerNote, submittedAt: inv.offerSubmittedAt,
    };
  };

  // A demo deal so the screens are not empty.
  const customer = counterparties.find((c) => c.roles.includes('CUSTOMER') && c.checkStatus === 'GREEN');
  const subs = counterparties.filter((c) => c.roles.includes('SUBCONTRACTOR'));
  if (customer && subs.length) {
    const d = create({ title: 'Rekonstrukce kanceláří, Kraków', customerId: customer.id, direction: 'CONSTRUCTION',
      siteCountry: 'PL', currency: 'EUR', defaultMarkupPct: 25 });
    d.createdAt = '2026-10-12T09:00:00Z';
    const add = (l: LineData) => d.lines.push({ ...l, id: uid(), position: d.lines.length + 1 });
    add({ kind: 'WORK', description: 'Demontáž příček a podhledů', workType: 'DISMANTLING', unit: 'm2', quantity: 240,
      unitCost: 9.5, subcontractorId: subs[0].id });
    add({ kind: 'WORK', description: 'Sádrokartonové příčky', workType: 'INTERIOR', unit: 'm2', quantity: 180,
      unitCost: 24, markupPct: 30, subcontractorId: subs[0].id });
    add({ kind: 'WORK', description: 'Elektroinstalace, rozvody', workType: 'ELECTRICAL', unit: 'kpl', quantity: 1,
      unitCost: 6800, fixedUnitPrice: 8200, subcontractorId: subs[1]?.id });
    add({ kind: 'MATERIAL', description: 'Sádrokarton a profily', unit: 'kpl', quantity: 1, unitCost: 3150 });
    d.status = 'QUOTED';
  }

  const sitesMock = createSitesMock(counterparties, {
    find: (id) => deals.find((x) => x.id === id),
    contractValue: (id) => contract(findDeal(id)).total.price,
    currency: (id) => findDeal(id).currency,
  }, record, delay);

  // Two won deals with sites, scheduled around today, so the map, schedule and workload have something to show.
  const rhein = counterparties.find((c) => c.legalName.startsWith('Montage Rhein'));
  if (customer && rhein && subs.length >= 2) {
    const mon = mondayOf(localToday());
    const won = (title: string, cust: string, country: string, created: string, lines: LineData[]) => {
      const d = create({ title, customerId: cust, direction: 'CONSTRUCTION', siteCountry: country, currency: 'EUR',
        defaultMarkupPct: 22 });
      d.createdAt = created;
      lines.forEach((l) => d.lines.push({ ...l, id: uid(), position: d.lines.length + 1 }));
      d.status = 'IN_PROGRESS';
      return d;
    };
    const hala = won('Hala magazynowa, Gliwice', customer.id, 'PL', '2026-09-02T09:00:00Z', [
      { kind: 'WORK', description: 'Montáž ocelové konstrukce', workType: 'INSTALLATION', unit: 't', quantity: 42,
        unitCost: 610, subcontractorId: subs[1].id },
      { kind: 'WORK', description: 'Elektroinstalace haly', workType: 'ELECTRICAL', unit: 'kpl', quantity: 1,
        unitCost: 18400, subcontractorId: subs[1].id },
    ]);
    sitesMock.seed(hala.id, { address: 'ul. Portowa 12', city: 'Gliwice', latitude: 50.2945, longitude: 18.6714,
      projectManager: 'lena', foreman: 'pavel', plannedStart: addDays(mon, -21), plannedEnd: addDays(mon, 34),
      status: 'ACTIVE' }, [
      { title: 'Montáž ocelové konstrukce', workType: 'INSTALLATION', dealLineId: null, subcontractorId: subs[1].id,
        start: addDays(mon, -21), end: addDays(mon, -3), progressPct: 80 },
      { title: 'Elektroinstalace haly', workType: 'ELECTRICAL', dealLineId: null, subcontractorId: subs[1].id,
        start: addDays(mon, 2), end: addDays(mon, 25), progressPct: 0 },
    ]);
    const fasada = won('Fasáda, Wesel', rhein.id, 'DE', '2026-09-10T09:00:00Z', [
      { kind: 'WORK', description: 'Zateplení fasády ETICS', workType: 'FINISHING', unit: 'm2', quantity: 400,
        unitCost: 35, subcontractorId: subs[0].id },
    ]);
    sitesMock.seed(fasada.id, { address: 'Industriestraße 4', city: 'Wesel', latitude: 51.6586, longitude: 6.6178,
      projectManager: 'lena', foreman: 'marek', plannedStart: addDays(mon, -7), plannedEnd: addDays(mon, 27),
      status: 'ACTIVE' }, [
      { title: 'Lešení', workType: 'INSTALLATION', dealLineId: null, subcontractorId: subs[0].id,
        start: addDays(mon, -7), end: addDays(mon, -4), progressPct: 100 },
      { title: 'Zateplení fasády ETICS', workType: 'FINISHING', dealLineId: null, subcontractorId: subs[0].id,
        start: addDays(mon, -3), end: addDays(mon, 20), progressPct: 15 },
      { title: 'Elektro: venkovní osvětlení', workType: 'ELECTRICAL', dealLineId: null, subcontractorId: subs[1].id,
        start: addDays(mon, 7), end: addDays(mon, 11), progressPct: 0 },
    ]);
  }
  const invoicesMock = createInvoicesMock(counterparties, {
    deal: (id) => {
      const d = deals.find((x) => x.id === id);
      return d && { id: d.id, number: d.number, title: d.title, currency: d.currency, status: d.status,
        customerId: d.customerId, siteCountry: d.siteCountry, direction: d.direction };
    },
    contractValue: (id) => contract(findDeal(id)).total.price,
    customerLines: (id) => {
      const d = findDeal(id);
      const approved = cos.filter((c) => c.dealId === id && c.status === 'APPROVED').flatMap((c) => c.lines);
      return [...d.lines, ...approved].map((l) => ({ description: l.description, unit: l.unit ?? null,
        quantity: l.quantity, unitPrice: priced(d, l).unitPrice }));
    },
    acceptedStages: (dealId) => sitesMock.acceptedStages(dealId),
  }, record, delay);
  // An older paid invoice and an overdue one, so the invoice list has history.
  const wesel = deals.find((d) => d.title === 'Fasáda, Wesel');
  const gliwice = deals.find((d) => d.title === 'Hala magazynowa, Gliwice');
  if (wesel && gliwice) {
    invoicesMock.seedIssued(gliwice.id, [{ description: 'Záloha: montáž ocelové konstrukce', unit: null, quantity: 1,
      unitPrice: 12000, stageId: null }], 40, true);
    invoicesMock.seedIssued(wesel.id, [{ description: 'Lešení a příprava podkladu', unit: 'kpl', quantity: 1,
      unitPrice: 3400, stageId: null }], 21, false);
  }
  const { seed: _seed, acceptedStages: _stages, ...sitesApi } = sitesMock;
  const { seedIssued: _seedIssued, ...invoicesApi } = invoicesMock;

  return {
    ...sitesApi,
    ...invoicesApi,
    listDeals: (q, status) => {
      const needle = q?.toLowerCase().trim();
      return delay(deals
        .filter((d) => !status || d.status === status)
        .filter((d) => !needle || d.title.toLowerCase().includes(needle) || d.number.toLowerCase().includes(needle)
          || party(d.customerId)?.legalName.toLowerCase().includes(needle))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(listItem));
    },
    getDeal: (id) => delay(view(findDeal(id))),
    createDeal: async (input) => delay(view(create(input))),
    editDeal: async (id, patch) => {
      const d = findDeal(id);
      if (patch.defaultMarkupPct != null && patch.defaultMarkupPct !== d.defaultMarkupPct) requirePrices(d);
      if (patch.title?.trim()) d.title = patch.title.trim();
      if (patch.defaultMarkupPct != null) d.defaultMarkupPct = patch.defaultMarkupPct;
      if (patch.siteCountry !== undefined) d.siteCountry = patch.siteCountry?.toUpperCase() || null;
      if (patch.notes !== undefined) d.notes = patch.notes;
      record('Deal', id, 'EDITED', patch);
      return delay(view(d));
    },
    addLine: async (dealId, line) => {
      const d = findDeal(dealId);
      requirePrices(d);
      checkSupplier(line.subcontractorId);
      d.lines.push({ ...line, id: uid(), position: (d.lines.at(-1)?.position ?? 0) + 1 });
      record('Deal', dealId, 'LINE_ADDED', line);
      return delay(view(d));
    },
    updateLine: async (dealId, lineId, line) => {
      const d = findDeal(dealId);
      requirePrices(d);
      checkSupplier(line.subcontractorId);
      const i = d.lines.findIndex((l) => l.id === lineId);
      if (i < 0) throw new ApiError(404, `Line ${lineId} not found`);
      d.lines[i] = { ...line, id: lineId, position: d.lines[i].position };
      record('Deal', dealId, 'LINE_UPDATED', line);
      return delay(view(d));
    },
    removeLine: async (dealId, lineId) => {
      const d = findDeal(dealId);
      requirePrices(d);
      d.lines = d.lines.filter((l) => l.id !== lineId);
      record('Deal', dealId, 'LINE_REMOVED', { lineId });
      return delay(view(d));
    },
    moveDeal: async (id, status) => {
      const d = findDeal(id);
      if (!NEXT[d.status].includes(status)) throw new ApiError(409, `Deal ${d.number} cannot move from ${d.status} to ${status}`);
      if (status === 'QUOTED' && !d.lines.length) throw new ApiError(409, 'A quote needs at least one line');
      d.status = status;
      record('Deal', id, 'STATUS_CHANGED', { status });
      return delay(view(d));
    },

    dealRfqs: (dealId) => delay(rfqs.filter((r) => r.dealId === dealId).map(rfqView)),
    createRfq: async (dealId, input) => {
      const d = findDeal(dealId);
      if (!costsEditable(d.status)) throw new ApiError(409, `Deal ${d.number} is closed`);
      if (!input.lineIds.length) throw new ApiError(400, 'Pick at least one line');
      if (input.deadline && input.deadline < now().slice(0, 10)) throw new ApiError(400, 'The deadline is in the past');
      const r: StoredRfq = {
        id: uid(), dealId, title: input.title?.trim() || `${d.number} ${d.title}`, deadline: input.deadline ?? null,
        status: 'OPEN', awardedInvitationId: null, invitations: [],
        items: d.lines.filter((l) => input.lineIds.includes(l.id)).map((l) => ({ id: uid(), dealLineId: l.id })),
      };
      rfqs.push(r);
      record('Rfq', r.id, 'CREATED', input);
      return delay(rfqView(r));
    },
    getRfq: (id) => delay(rfqView(findRfq(id))),
    rfqCandidates: (workType) => delay(counterparties
      .filter((c) => c.roles.includes('SUBCONTRACTOR') || c.roles.includes('SUPPLIER'))
      .filter((c) => !workType || (c.workTypes ?? []).includes(workType))
      .map(candidate)),
    invite: async (rfqId, subcontractorId, allowRisky) => {
      const r = findRfq(rfqId);
      if (r.status !== 'OPEN') throw new ApiError(409, 'The tender is closed');
      const c = party(subcontractorId);
      if (!c) throw new ApiError(404, 'Counterparty not found');
      if (c.checkStatus === 'RED' && !allowRisky) throw new ApiError(409, `${c.legalName} has a red check result`);
      if (r.invitations.some((i) => i.subcontractorId === subcontractorId)) {
        throw new ApiError(409, `${c.legalName} is already invited`);
      }
      const bytes = crypto.getRandomValues(new Uint8Array(32));
      const token = btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
      const inv: StoredInvitation = { id: uid(), subcontractorId, token, invitedAt: now(), viewedAt: null,
        offerSubmittedAt: null, offerNote: null, prices: {} };
      r.invitations.push(inv);
      record('Rfq', rfqId, 'INVITED', { subcontractorId });
      const v = rfqView(r).invitations.find((i) => i.id === inv.id)!;
      return delay({ invitation: v, offerPath: `/offer/${token}` });
    },
    award: async (rfqId, invitationId) => {
      const r = findRfq(rfqId);
      if (r.status !== 'OPEN') throw new ApiError(409, 'The tender is closed');
      const inv = r.invitations.find((i) => i.id === invitationId);
      if (!inv?.offerSubmittedAt) throw new ApiError(409, 'Only a submitted offer can win');
      const d = findDeal(r.dealId);
      r.items.forEach((it) => {
        if (inv.prices[it.id] != null) assignCost(d, it.dealLineId, inv.prices[it.id], inv.subcontractorId);
      });
      r.status = 'AWARDED';
      r.awardedInvitationId = invitationId;
      record('Rfq', rfqId, 'AWARDED', { invitationId });
      return delay(rfqView(r));
    },
    cancelRfq: async (rfqId) => {
      const r = findRfq(rfqId);
      if (r.status !== 'OPEN') throw new ApiError(409, 'The tender is closed');
      r.status = 'CANCELLED';
      record('Rfq', rfqId, 'CANCELLED', {});
      return delay(rfqView(r));
    },

    changeOrders: async (dealId) => { findDeal(dealId); return delay(cos.filter((c) => c.dealId === dealId).map(coView)); },
    createChangeOrder: async (dealId, draft) => {
      const d = findDeal(dealId);
      requireAlive(d);
      if (!draft.title.trim()) throw new ApiError(400, 'A change order needs a title');
      const c: StoredChangeOrder = { id: uid(), dealId, seq: cos.filter((x) => x.dealId === dealId).length + 1,
        title: draft.title.trim(), reason: draft.reason?.trim() || null, status: 'DRAFT', extraDays: draft.extraDays ?? 0,
        sentAt: null, decidedAt: null, decidedBy: null, decisionNote: null, createdAt: now(), lines: [] };
      cos.push(c);
      record('ChangeOrder', c.id, 'CREATED', draft);
      return delay(coView(c));
    },
    getChangeOrder: async (id) => delay(coView(findCo(id))),
    editChangeOrder: async (id, draft) => {
      const c = findCo(id);
      if (c.status !== 'DRAFT') throw new ApiError(409, `Change order ${c.seq} is ${c.status}; revise it to a draft first`);
      Object.assign(c, { title: draft.title.trim(), reason: draft.reason?.trim() || null, extraDays: draft.extraDays ?? 0 });
      return delay(coView(c));
    },
    addChangeLine: async (id, line) => {
      const c = findCo(id);
      if (c.status !== 'DRAFT') throw new ApiError(409, `Change order ${c.seq} is ${c.status}; revise it to a draft first`);
      coLine(line);
      c.lines.push({ ...line, id: uid(), position: (c.lines.at(-1)?.position ?? 0) + 1 });
      return delay(coView(c));
    },
    updateChangeLine: async (id, lineId, line) => {
      const c = findCo(id);
      const i = c.lines.findIndex((l) => l.id === lineId);
      if (i < 0) throw new ApiError(404, `Line ${lineId} not found`);
      coLine(line);
      const old = c.lines[i];
      if (c.status !== 'DRAFT') {
        if (!['SENT', 'APPROVED'].includes(c.status)) throw new ApiError(409, `Change order ${c.seq} cannot change`);
        const touched = old.quantity !== line.quantity || old.description !== line.description.trim()
          || (old.markupPct ?? null) !== (line.markupPct ?? null) || (old.fixedUnitPrice ?? null) !== (line.fixedUnitPrice ?? null);
        if (touched) throw new ApiError(409, `The customer was already sent change order ${c.seq}; revise it to change prices`);
        const d = findDeal(c.dealId);
        c.lines[i] = { ...old, fixedUnitPrice: old.fixedUnitPrice ?? priced(d, old).unitPrice, unitCost: line.unitCost,
          subcontractorId: line.subcontractorId };
      } else {
        c.lines[i] = { ...line, id: lineId, position: old.position };
      }
      return delay(coView(c));
    },
    removeChangeLine: async (id, lineId) => {
      const c = findCo(id);
      if (c.status !== 'DRAFT') throw new ApiError(409, `Change order ${c.seq} is ${c.status}; revise it to a draft first`);
      c.lines = c.lines.filter((l) => l.id !== lineId);
      return delay(coView(c));
    },
    changeOrderAction: async (id, action) => {
      const c = findCo(id);
      if (action === 'send') {
        requireAlive(findDeal(c.dealId));
        if (!c.lines.length) throw new ApiError(409, 'A change order needs at least one line');
        moveCo(c, 'SENT');
        c.sentAt = now();
      } else if (action === 'revise') {
        moveCo(c, 'DRAFT');
        c.sentAt = null;
      } else {
        moveCo(c, 'CANCELLED');
      }
      return delay(coView(c));
    },
    decideChangeOrder: async (id, decision, by, note) => {
      const c = findCo(id);
      if (!by.trim()) throw new ApiError(400, "Record who decided on the customer's side");
      if (decision === 'approve') requireAlive(findDeal(c.dealId));
      moveCo(c, decision === 'approve' ? 'APPROVED' : 'REJECTED');
      Object.assign(c, { decidedBy: by.trim(), decisionNote: note?.trim() || null, decidedAt: now() });
      return delay(coView(c));
    },
    changeOrderFile: async (id) => {
      const v = coView(findCo(id));
      return new Blob([`<!doctype html><meta charset="utf-8"><title>${v.number}</title><body style="font-family:sans-serif;padding:32px">`
        + `<p>Demo: the real system renders a PDF here.</p><h1>${v.number}: ${v.title}</h1>`
        + `<p><b>${v.totals.price.toFixed(2)}</b></p>`], { type: 'text/html' });
    },

    quoteDefaults: async (dealId) => {
      const d = findDeal(dealId);
      const c = party(d.customerId)!;
      const s = suggestVat(c.country, c.vatNumber, d.siteCountry, d.direction === 'CONSTRUCTION');
      return delay({ language: languageFor(c.country), vatMode: s.mode, vatRate: s.rate, reason: s.reason, validDays: 30 });
    },
    listQuotes: async (dealId) => {
      findDeal(dealId);
      return delay(quotes.filter((q) => q.dealId === dealId).sort((a, b) => b.version - a.version)
        .map(({ dealId: _d, html: _h, ...q }) => q));
    },
    issueQuote: async (dealId, input) => {
      const d = findDeal(dealId);
      if (!d.lines.length) throw new ApiError(409, `Deal ${d.number} has no lines to quote`);
      if (!pricesEditable(d.status)) {
        throw new ApiError(409, `Deal ${d.number} is ${d.status}; a new quote is possible only before the customer accepts`);
      }
      if (d.status === 'DRAFT') { d.status = 'QUOTED'; record('Deal', d.id, 'STATUS_CHANGED', { status: 'QUOTED' }); }
      const v = view(d);
      const rate = input.vatMode === 'STANDARD' ? (input.vatRate ?? 21) : 0;
      const vat = vatAmount(v.totals.price, input.vatMode, rate);
      const version = quotes.filter((q) => q.dealId === dealId).length + 1;
      const today = new Date();
      const until = new Date(today.getTime() + input.validDays * 86_400_000);
      const number = d.number.replace(/^D-/, 'Q-') + (version > 1 ? `/${version}` : '');
      const q = {
        id: uid(), dealId, number, version, language: input.language, vatMode: input.vatMode, vatRate: rate,
        currency: d.currency, subtotal: v.totals.price, vatAmount: vat, total: Math.round((v.totals.price + vat) * 100) / 100,
        issuedOn: today.toISOString().slice(0, 10), validUntil: until.toISOString().slice(0, 10), issuedAt: now(),
        issuedBy: 'demo', pdfSha256: Array.from(crypto.getRandomValues(new Uint8Array(32)), (b) => b.toString(16).padStart(2, '0')).join(''),
        html: `<!doctype html><meta charset="utf-8"><title>${number}</title><body style="font-family:sans-serif;padding:32px">`
          + `<p>Demo: the real system stores a PDF here.</p><h1>${number}</h1><p>${d.title}</p><ul>`
          + v.lines.map((l) => `<li>${l.description}: ${l.quantity} ${l.unit ?? ''} × ${l.unitPrice.toFixed(2)}</li>`).join('')
          + `</ul><p><b>${(v.totals.price + vat).toFixed(2)} ${d.currency}</b></p>`,
      };
      quotes.push(q);
      record('Quote', q.id, 'ISSUED', { number, total: q.total });
      const { dealId: _d, html: _h, ...out } = q;
      return delay(out);
    },
    quoteFile: async (id) => {
      const q = quotes.find((x) => x.id === id);
      if (!q) throw new ApiError(404, `Quote ${id} not found`);
      return new Blob([q.html], { type: 'text/html' });
    },

    openOffer: async (token) => {
      const { r, inv } = byToken(token);
      inv.viewedAt ??= now();
      return delay(offerPage(r, inv));
    },
    submitOffer: async (token, prices, note) => {
      const { r, inv } = byToken(token);
      if (!offerPage(r, inv).open) throw new ApiError(409, 'This tender no longer accepts offers');
      const known = new Set(r.items.map((i) => i.id));
      if (Object.keys(prices).some((k) => !known.has(k))) throw new ApiError(400, 'Unknown item');
      if (Object.values(prices).some((p) => !(p >= 0))) throw new ApiError(400, 'Prices must be zero or positive');
      inv.prices = { ...prices };
      inv.offerNote = note || null;
      inv.offerSubmittedAt = now();
      record('Rfq', r.id, 'OFFER_SUBMITTED', { invitationId: inv.id });
      return delay(offerPage(r, inv));
    },
  };
}
