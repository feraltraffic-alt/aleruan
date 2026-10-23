export type CheckStatus = 'GREEN' | 'YELLOW' | 'RED';
export type CounterpartyRole = 'CUSTOMER' | 'SUBCONTRACTOR' | 'SUPPLIER' | 'CARRIER';
export type CheckSource =
  | 'VIES' | 'ARES' | 'REGISTR_DPH' | 'ISIR' | 'BIALA_LISTA' | 'KRS' | 'CEIDG' | 'SANCTIONS_EU' | 'MANUAL';
export type CheckFinding =
  | 'VAT_INVALID' | 'NOT_FOUND_IN_REGISTRY' | 'UNRELIABLE_VAT_PAYER' | 'VAT_NOT_ACTIVE' | 'INSOLVENCY'
  | 'LIQUIDATION_OR_CEASED' | 'SANCTIONS_HIT' | 'YOUNG_COMPANY' | 'NOT_VAT_PAYER' | 'SANCTIONS_POSSIBLE_MATCH'
  | 'ACCOUNT_NOT_PUBLISHED'
  | 'SOURCE_UNAVAILABLE' | 'MANUAL_REVIEW_REQUIRED';

export interface Counterparty {
  id: string;
  workTypes: string[];
  legalName: string;
  country: string;
  companyId: string | null;
  vatNumber: string | null;
  address: string | null;
  email: string | null;
  phone: string | null;
  roles: CounterpartyRole[];
  checkStatus: CheckStatus | null;
  lastCheckedAt: string | null;
  createdAt: string;
}

export interface NewCounterparty {
  legalName: string;
  country: string;
  companyId?: string;
  vatNumber?: string;
  address?: string;
  email?: string;
  phone?: string;
  roles: CounterpartyRole[];
  workTypes?: string[];
}

export interface CheckResult {
  id: string;
  source: CheckSource;
  status: CheckStatus;
  findings: CheckFinding[];
  requestId: string | null;
  unavailableReason: string | null;
  checkedAt: string;
  /** Set for bank-account checks. */
  checkedAccount: string | null;
}

export interface CheckRun {
  status: CheckStatus;
  findings: CheckFinding[];
  results: CheckResult[];
}

export interface LookupResult {
  source: CheckSource;
  legalName: string | null;
  address: string | null;
  vatNumber: string | null;
  registeredOn: string | null;
}

export interface ChainVerification {
  valid: boolean;
  verifiedRecords: number;
  brokenAtSeq: number | null;
}

export interface AuditRecord {
  seq: number;
  occurredAt: string;
  actor: string;
  entityType: string;
  entityId: string;
  action: string;
  payload: string | null;
  prevHash: string;
  hash: string;
}

export interface WorkType {
  id: string;
  code: string;
  nameCs: string;
  namePl: string;
  nameEn: string;
  nameRu: string;
  defaultMarkupPct: number | null;
  active: boolean;
}

export type DealStatus = 'DRAFT' | 'QUOTED' | 'WON' | 'LOST' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type Direction = 'CONSTRUCTION' | 'MARKETING' | 'IT' | 'CONSULTING' | 'LOGISTICS' | 'TRADE';
export type LineKind = 'WORK' | 'MATERIAL' | 'SERVICE' | 'GOODS' | 'DELIVERY';
export type DealWarning = 'CUSTOMER_RED' | 'SUBCONTRACTOR_RED' | 'LINE_BELOW_COST' | 'MARGIN_BELOW_THRESHOLD';

export interface PartyRef { id: string; legalName: string; checkStatus: CheckStatus | null }

export interface DealTotals { cost: number; price: number; margin: number; marginPct: number | null; markupPct: number | null }

export interface DealLine {
  id: string;
  position: number;
  kind: LineKind;
  description: string;
  workType: string | null;
  unit: string | null;
  quantity: number;
  unitCost: number;
  markupPct: number | null;
  markupFromDeal: boolean;
  fixedUnitPrice: number | null;
  unitPrice: number;
  costTotal: number;
  priceTotal: number;
  margin: number;
  subcontractor: PartyRef | null;
}

export type ChangeOrderStatus = 'DRAFT' | 'SENT' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface Contract {
  original: DealTotals; approvedChanges: DealTotals; pendingPrice: number; total: DealTotals; extraDays: number;
}

export interface ChangeOrder {
  id: string; dealId: string; number: string; seq: number; title: string; reason: string | null;
  status: ChangeOrderStatus; nextStatuses: ChangeOrderStatus[]; pricesEditable: boolean; extraDays: number;
  sentAt: string | null; decidedAt: string | null; decidedBy: string | null; decisionNote: string | null;
  createdAt: string; createdBy: string | null; lines: DealLine[]; totals: DealTotals;
}

export interface ChangeOrderDraft { title: string; reason?: string | null; extraDays?: number | null }

export interface Deal {
  id: string;
  number: string;
  title: string;
  status: DealStatus;
  nextStatuses: DealStatus[];
  direction: Direction;
  siteCountry: string | null;
  currency: string;
  defaultMarkupPct: number;
  pricesEditable: boolean;
  customer: PartyRef | null;
  manager: string | null;
  notes: string | null;
  createdAt: string;
  lines: DealLine[];
  totals: DealTotals;
  warnings: DealWarning[];
  contract: Contract;
}

export interface DealListItem {
  id: string; number: string; title: string; status: DealStatus; direction: Direction; currency: string;
  customer: PartyRef | null; totals: DealTotals; createdAt: string;
}

export interface NewDeal {
  title: string; customerId: string; direction: Direction; siteCountry?: string; currency: string; defaultMarkupPct: number;
}

export interface LineData {
  kind: LineKind; description: string; workType?: string | null; unit?: string | null; quantity: number; unitCost: number;
  markupPct?: number | null; fixedUnitPrice?: number | null; subcontractorId?: string | null;
}

export type RfqStatus = 'OPEN' | 'AWARDED' | 'CANCELLED';

export interface Candidate { id: string; legalName: string; country: string; checkStatus: CheckStatus | null; workTypes: string[] }

export interface RfqItem {
  id: string; dealLineId: string; description: string; unit: string | null; quantity: number; workType: string | null;
  cheapestInvitationId: string | null;
}

export interface RfqInvitation {
  id: string; subcontractor: Candidate; invitedAt: string; viewedAt: string | null; offerSubmittedAt: string | null;
  offerNote: string | null; prices: Record<string, number>; total: number | null; complete: boolean;
}

export interface Rfq {
  id: string; dealId: string; dealNumber: string | null; currency: string | null; title: string; deadline: string | null;
  status: RfqStatus; awardedInvitationId: string | null; cheapestInvitationId: string | null;
  items: RfqItem[]; invitations: RfqInvitation[];
}

export interface OfferPage {
  title: string; invitedCompany: string; currency: string | null; deadline: string | null; open: boolean;
  items: { id: string; description: string; unit: string | null; quantity: number }[];
  myPrices: Record<string, number>; myNote: string | null; submittedAt: string | null;
}

export type QuoteLanguage = 'CS' | 'EN' | 'PL' | 'DE';
export type VatMode = 'STANDARD' | 'REVERSE_CHARGE_EU' | 'REVERSE_CHARGE_CZ' | 'OUTSIDE_SCOPE';
export type VatReason = 'CUSTOMER_OUTSIDE_EU' | 'SITE_IN_OTHER_EU_STATE' | 'EU_BUSINESS_CUSTOMER'
  | 'CZ_CONSTRUCTION_FOR_VAT_PAYER' | 'DOMESTIC_STANDARD' | 'CHECK_WITH_ACCOUNTANT';

export interface QuoteDefaults { language: QuoteLanguage; vatMode: VatMode; vatRate: number; reason: VatReason; validDays: number }

export interface Quote {
  id: string; number: string; version: number; language: QuoteLanguage; vatMode: VatMode; vatRate: number;
  currency: string; subtotal: number; vatAmount: number; total: number; issuedOn: string; validUntil: string;
  issuedAt: string; issuedBy: string | null; pdfSha256: string;
}

export interface IssueQuote {
  language: QuoteLanguage; validDays: number; vatMode: VatMode; vatRate?: number | null; terms?: string | null;
}

export interface ClientConfig { googleMapsApiKey: string | null; googleMapsMapId: string | null }

export type SiteStatus = 'PLANNED' | 'ACTIVE' | 'ON_HOLD' | 'DONE';

export interface SiteParty { id: string; legalName: string; country: string; checkStatus: CheckStatus | null }

export interface SiteTask {
  id: string; position: number; title: string; workType: string | null; dealLineId: string | null;
  subcontractor: SiteParty | null; start: string; end: string; progressPct: number; late: boolean;
}

export interface Site {
  id: string; dealId: string; dealNumber: string | null; dealStatus: DealStatus | null; name: string;
  address: string | null; city: string | null; country: string | null; latitude: number | null; longitude: number | null;
  projectManager: string | null; foreman: string | null; plannedStart: string | null; plannedEnd: string | null;
  status: SiteStatus; notes: string | null; customer: SiteParty | null; progressPct: number; lateTasks: number;
  workTypes: string[]; createdAt: string; tasks: SiteTask[];
  handoverDate: string | null; warrantyMonths: number | null; warrantyUntil: string | null; openDefects: number;
}

export interface FileRef { id: string; fileName: string; contentType: string; sizeBytes: number; uploadedAt: string; uploadedBy: string | null }
export type PhotoOwner = 'diary' | 'defects';

export interface DiaryEntry {
  id: string; entryDate: string; weather: string | null; workers: number | null; text: string; correctsId: string | null;
  correctedBy: string[]; author: string | null; createdAt: string; editedAt: string | null; editable: boolean; photos: FileRef[];
}
export interface DiaryData { entryDate: string; weather: string | null; workers: number | null; text: string; correctsId: string | null }

export type DefectSeverity = 'MINOR' | 'MAJOR' | 'CRITICAL';
export type DefectStatus = 'OPEN' | 'FIXED' | 'CLOSED' | 'DISMISSED';
export interface Defect {
  id: string; siteId: string; siteName: string; seq: number; title: string; description: string | null; location: string | null;
  severity: DefectSeverity; subcontractor: { id: string; legalName: string; checkStatus: CheckStatus | null } | null;
  stageId: string | null; foundOn: string; dueDate: string | null; warranty: boolean; status: DefectStatus;
  nextStatuses: DefectStatus[]; overdue: boolean; fixedAt: string | null; fixNote: string | null; closedAt: string | null;
  closedBy: string | null; createdAt: string; createdBy: string | null; photos: FileRef[];
}
export interface DefectData {
  title: string; description: string | null; location: string | null; severity: DefectSeverity; subcontractorId: string | null;
  stageId: string | null; foundOn: string; dueDate: string | null;
}

export interface SiteDetails {
  name: string; address: string | null; city: string | null; country: string | null; latitude: number | null;
  longitude: number | null; projectManager: string | null; foreman: string | null; plannedStart: string | null;
  plannedEnd: string | null; status: SiteStatus; notes: string | null;
}

export interface SiteFilter {
  status?: SiteStatus; country?: string; city?: string; pm?: string; customerId?: string; workType?: string; dealId?: string;
}

export interface TaskData {
  title: string; workType: string | null; subcontractorId: string | null; start: string; end: string; progressPct: number;
}

export interface WorkloadRow {
  subcontractor: SiteParty;
  tasks: { taskId: string; title: string; siteId: string; siteName: string; start: string; end: string; progressPct: number }[];
  weeks: { monday: string; parallelTasks: number }[];
}

export type StageStatus = 'PLANNED' | 'SUBMITTED' | 'ACCEPTED' | 'ACCEPTED_WITH_REMARKS' | 'REJECTED';

export interface Stage {
  id: string; siteId: string; seq: number; title: string; description: string | null; amount: number;
  plannedDate: string | null; status: StageStatus; nextStatuses: StageStatus[]; submittedAt: string | null;
  decidedAt: string | null; decidedBy: string | null; remarks: string | null; fixUntil: string | null; taskIds: string[];
}

export interface StageData { title: string; description: string | null; amount: number; plannedDate: string | null; taskIds: string[] }

export interface Coverage { contractValue: number; planned: number; accepted: number; unassigned: number; currency: string | null }

export interface StageDecision {
  decision: 'ACCEPTED' | 'ACCEPTED_WITH_REMARKS' | 'REJECTED'; by: string; remarks: string | null; fixUntil: string | null;
}

export interface Api {
  listCounterparties(q?: string, role?: CounterpartyRole): Promise<Counterparty[]>;
  getCounterparty(id: string): Promise<Counterparty>;
  createCounterparty(input: NewCounterparty): Promise<Counterparty>;
  runChecks(id: string): Promise<CheckRun>;
  checkHistory(id: string): Promise<CheckResult[]>;
  checkAccount(id: string, account: string): Promise<CheckResult>;
  lookup(country: string, companyId?: string, vatNumber?: string): Promise<LookupResult | null>;
  verifyAudit(): Promise<ChainVerification>;
  auditHistory(entityType: string, entityId: string): Promise<AuditRecord[]>;
  workTypes(): Promise<WorkType[]>;
  setWorkTypes(id: string, workTypes: string[]): Promise<Counterparty>;

  listDeals(q?: string, status?: DealStatus): Promise<DealListItem[]>;
  getDeal(id: string): Promise<Deal>;
  createDeal(input: NewDeal): Promise<Deal>;
  editDeal(id: string, patch: { title?: string; defaultMarkupPct?: number; siteCountry?: string | null; notes?: string | null }): Promise<Deal>;
  addLine(dealId: string, line: LineData): Promise<Deal>;
  updateLine(dealId: string, lineId: string, line: LineData): Promise<Deal>;
  removeLine(dealId: string, lineId: string): Promise<Deal>;
  moveDeal(id: string, status: DealStatus): Promise<Deal>;

  dealRfqs(dealId: string): Promise<Rfq[]>;
  createRfq(dealId: string, input: { title?: string; deadline?: string | null; lineIds: string[] }): Promise<Rfq>;
  getRfq(id: string): Promise<Rfq>;
  rfqCandidates(workType?: string): Promise<Candidate[]>;
  invite(rfqId: string, subcontractorId: string, allowRisky: boolean): Promise<{ invitation: RfqInvitation; offerPath: string }>;
  award(rfqId: string, invitationId: string): Promise<Rfq>;
  cancelRfq(rfqId: string): Promise<Rfq>;

  quoteDefaults(dealId: string): Promise<QuoteDefaults>;
  listQuotes(dealId: string): Promise<Quote[]>;
  issueQuote(dealId: string, input: IssueQuote): Promise<Quote>;
  /** The stored document (PDF in production, an HTML stand-in in demo mode). */
  quoteFile(id: string): Promise<Blob>;

  config(): Promise<ClientConfig>;
  listSites(filter?: SiteFilter): Promise<Site[]>;
  getSite(id: string): Promise<Site>;
  createSite(dealId: string): Promise<Site>;
  editSite(id: string, details: SiteDetails): Promise<Site>;
  addTask(siteId: string, task: TaskData): Promise<Site>;
  updateTask(siteId: string, taskId: string, task: TaskData): Promise<Site>;
  removeTask(siteId: string, taskId: string): Promise<Site>;
  tasksFromDeal(siteId: string): Promise<Site>;
  workload(from: string, to: string): Promise<WorkloadRow[]>;

  changeOrders(dealId: string): Promise<ChangeOrder[]>;
  createChangeOrder(dealId: string, draft: ChangeOrderDraft): Promise<ChangeOrder>;
  getChangeOrder(id: string): Promise<ChangeOrder>;
  editChangeOrder(id: string, draft: ChangeOrderDraft): Promise<ChangeOrder>;
  addChangeLine(id: string, line: LineData): Promise<ChangeOrder>;
  updateChangeLine(id: string, lineId: string, line: LineData): Promise<ChangeOrder>;
  removeChangeLine(id: string, lineId: string): Promise<ChangeOrder>;
  changeOrderAction(id: string, action: 'send' | 'revise' | 'cancel'): Promise<ChangeOrder>;
  decideChangeOrder(id: string, decision: 'approve' | 'reject', by: string, note: string | null): Promise<ChangeOrder>;
  /** Sheet for the customer to sign (PDF; HTML stand-in in demo mode). */
  changeOrderFile(id: string): Promise<Blob>;

  stages(siteId: string): Promise<{ stages: Stage[]; coverage: Coverage }>;
  createStage(siteId: string, data: StageData): Promise<Stage>;
  editStage(id: string, data: StageData): Promise<Stage>;
  removeStage(id: string): Promise<void>;
  stageAction(id: string, action: 'submit' | 'withdraw'): Promise<Stage>;
  decideStage(id: string, decision: StageDecision): Promise<Stage>;
  /** Handover protocol (PDF; HTML stand-in in demo mode). */
  protocolFile(id: string): Promise<Blob>;

  setHandover(siteId: string, handoverDate: string | null, warrantyMonths: number | null): Promise<Site>;
  diary(siteId: string): Promise<DiaryEntry[]>;
  addDiaryEntry(siteId: string, data: DiaryData): Promise<DiaryEntry>;
  editDiaryEntry(id: string, data: DiaryData): Promise<DiaryEntry>;
  siteDefects(siteId: string): Promise<Defect[]>;
  unresolvedDefects(): Promise<Defect[]>;
  reportDefect(siteId: string, data: DefectData): Promise<Defect>;
  editDefect(id: string, data: DefectData): Promise<Defect>;
  moveDefect(id: string, status: DefectStatus, note: string | null): Promise<Defect>;
  uploadPhoto(owner: PhotoOwner, ownerId: string, file: Blob, fileName: string): Promise<FileRef>;
  removePhoto(owner: PhotoOwner, ownerId: string, fileId: string): Promise<void>;
  fileBlob(id: string): Promise<Blob>;

  openOffer(token: string): Promise<OfferPage>;
  submitOffer(token: string, prices: Record<string, number>, note: string): Promise<OfferPage>;
}
