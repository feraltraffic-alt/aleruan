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
}
