import { useQuery } from '@tanstack/react-query';
import { http } from '../../../shared/api/http';
import { qk, staleTimes } from '../../../shared/api/queryClient';
import type { EnforcementStats, SectorQuota } from '../../../shared/api/types';

const opt = { staleTime: staleTimes.list, refetchInterval: 15_000 };

export const usePermitStats = () => useQuery({ queryKey: qk.permitStats, ...opt,
  queryFn: () => http<Record<string, number>>('/api/permits/stats') });
export const useQuotas = () => useQuery({ queryKey: qk.quotas, ...opt,
  queryFn: () => http<SectorQuota[]>('/api/permits/quotas') });
export const useEnforcementStats = () => useQuery({ queryKey: qk.enforcementStats, ...opt,
  queryFn: () => http<EnforcementStats>('/api/enforcement/stats') });
export const useEligibilityStats = () => useQuery({ queryKey: qk.eligStats, ...opt,
  queryFn: () => http<{ cacheHitRatio: number }>('/api/eligibility/stats') });
export const useAuditStats = () => useQuery({ queryKey: qk.auditStats, ...opt,
  queryFn: () => http<Record<string, number>>('/api/audit/stats') });
