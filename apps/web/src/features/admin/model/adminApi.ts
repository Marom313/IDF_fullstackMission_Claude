import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../../shared/api/http';
import { qk, staleTimes } from '../../../shared/api/queryClient';
import type { AuditPage, DeadLetter, QuarantinedFile } from '../../../shared/api/types';

export const useDeadLetters = () => useQuery({ queryKey: qk.dlt, staleTime: staleTimes.live, refetchInterval: 10_000,
  queryFn: () => http<DeadLetter[]>('/api/admin/dlt') });

export const useQuarantine = () => useQuery({ queryKey: qk.quarantine, staleTime: staleTimes.live,
  refetchInterval: 10_000, queryFn: () => http<QuarantinedFile[]>('/api/admin/quarantine') });

export const useAuditSearch = (entityId: string) => useQuery({
  queryKey: qk.audit(entityId), staleTime: staleTimes.live,
  queryFn: () => http<AuditPage>(`/api/audit?limit=50${entityId ? `&entityId=${encodeURIComponent(entityId)}` : ''}`),
});

export function useReplay() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http(`/api/admin/dlt/${encodeURIComponent(id)}/replay`, { method: 'POST' }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: qk.dlt });
    },
  });
}
