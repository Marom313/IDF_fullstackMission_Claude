import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../../shared/api/http';
import { qk, staleTimes } from '../../../shared/api/queryClient';
import type { PolicyDetail, PolicySummary, SimulationResult, ValidationReport } from '../../../shared/api/types';

export const usePolicies = () => useQuery({
  queryKey: qk.policies, staleTime: staleTimes.reference, queryFn: () => http<PolicySummary[]>('/api/policies'),
});

export const usePolicyDetail = (name?: string, version?: number) => useQuery({
  queryKey: ['policies', name, version], enabled: !!name && !!version,
  queryFn: () => http<PolicyDetail>(`/api/policies/${encodeURIComponent(name!)}/versions/${version}`),
});

export const validatePolicy = (source: string, signal?: AbortSignal) =>
  http<ValidationReport>('/api/policies/validate', { method: 'POST', body: { source }, signal });

export const useSimulate = () => useMutation({
  mutationFn: (source: string) => http<SimulationResult>('/api/policies/simulate',
    { method: 'POST', body: { source, caseCount: 1000, seed: 20261120 } }),
});

export function useSaveAndPublish() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ source, publish }: { source: string; publish: boolean }) => {
      const draft = await http<PolicyDetail>('/api/policies', { method: 'POST', body: { source } });
      const s = draft.summary!;
      if (publish) await http(`/api/policies/${encodeURIComponent(s.name!)}/versions/${s.version}/publish`, { method: 'POST' });
      return s;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: qk.policies });
    },
  });
}
