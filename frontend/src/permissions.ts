import { authEnabled, currentUser } from './auth';

/**
 * Mirror of the backend's AccessRules: what the signed-in user may see and do, so the UI does not
 * offer actions the server would refuse. Without login (demo, local development) everything is allowed.
 */
const GROUPS = {
  office: ['ADMIN', 'SALES_MANAGER', 'ACCOUNT_MANAGER', 'ACCOUNTANT', 'PROJECT_MANAGER'],
  sales: ['ADMIN', 'SALES_MANAGER', 'ACCOUNT_MANAGER'],
  finance: ['ADMIN', 'ACCOUNTANT', 'ACCOUNT_MANAGER'],
  project: ['ADMIN', 'PROJECT_MANAGER'],
  salesOrProject: ['ADMIN', 'SALES_MANAGER', 'ACCOUNT_MANAGER', 'PROJECT_MANAGER'],
  siteCrew: ['ADMIN', 'PROJECT_MANAGER', 'FOREMAN'],
  siteRead: ['ADMIN', 'SALES_MANAGER', 'ACCOUNT_MANAGER', 'ACCOUNTANT', 'PROJECT_MANAGER', 'FOREMAN'],
  audit: ['ADMIN', 'ACCOUNTANT', 'ACCOUNT_MANAGER'],
  admin: ['ADMIN'],
} as const;

export type Permission =
  | 'deals.read' | 'deals.write' | 'quotes.issue' | 'tenders.write' | 'changes.write' | 'sites.open'
  | 'counterparties.write' | 'invoices.read' | 'invoices.write' | 'sites.read' | 'sites.write' | 'site.records'
  | 'audit.read' | 'reference.write';

const RULES: Record<Permission, readonly string[]> = {
  'deals.read': GROUPS.office,
  'deals.write': GROUPS.sales,
  'quotes.issue': GROUPS.sales,
  'tenders.write': GROUPS.salesOrProject,
  'changes.write': GROUPS.salesOrProject,
  'sites.open': GROUPS.salesOrProject,
  'counterparties.write': GROUPS.office,
  'invoices.read': GROUPS.office,
  'invoices.write': GROUPS.finance,
  'sites.read': GROUPS.siteRead,
  'sites.write': GROUPS.project,
  'site.records': GROUPS.siteCrew,
  'audit.read': GROUPS.audit,
  'reference.write': GROUPS.admin,
};

export function can(permission: Permission, roles: string[] | null = authEnabled ? currentUser()?.roles ?? [] : null): boolean {
  if (roles === null) return true;
  return RULES[permission].some((r) => roles.includes(r));
}
