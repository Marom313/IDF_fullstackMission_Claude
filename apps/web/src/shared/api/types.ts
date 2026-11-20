// Type aliases over the GENERATED OpenAPI types (npm run gen:api). No hand-written DTOs.
import type { components as Audit } from './generated/audit';
import type { components as Elig } from './generated/eligibility';
import type { components as Enf } from './generated/enforcement';
import type { components as Permits } from './generated/permits';
import type { components as Policies } from './generated/policies';
import type { components as Registry } from './generated/registry';

export type PersonDto = Registry['schemas']['PersonDto'];
export type PersonPage = Registry['schemas']['PersonPage'];
export type Employer = Registry['schemas']['Employer'];

export type PermitDto = Permits['schemas']['PermitDto'];
export type PermitPage = Permits['schemas']['PermitPage'];
export type SubmitRequest = Permits['schemas']['SubmitRequest'];
export type DecisionRequest = Permits['schemas']['DecisionRequest'];
export type QrBundle = Permits['schemas']['QrBundle'];
export type SectorQuota = Permits['schemas']['SectorQuota'];

export type CheckResponse = Elig['schemas']['CheckResponse'];

export type CaseRecord = Enf['schemas']['CaseRecord'];
export type CasePage = Enf['schemas']['CasePage'];
export type EnforcementStats = Enf['schemas']['Stats'];
export type CrossingRequest = Enf['schemas']['CrossingRequest'];

export type AuditPage = Audit['schemas']['AuditPage'];
export type AuditRecord = Audit['schemas']['AuditRecord'];
export type DeadLetter = Audit['schemas']['DeadLetter'];
export type QuarantinedFile = Audit['schemas']['QuarantinedFile'];

export type PolicySummary = Policies['schemas']['PolicySummary'];
export type PolicyDetail = Policies['schemas']['PolicyDetail'];
export type ValidationReport = Policies['schemas']['ValidationReport'];
export type SimulationResult = Policies['schemas']['Result'];
export type SyntaxError = Policies['schemas']['SyntaxError'];
