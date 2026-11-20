import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from '../../../shared/api/http';
import { qk, staleTimes } from '../../../shared/api/queryClient';
import type { CasePage, CaseRecord } from '../../../shared/api/types';

export const useCases = (status: string) => useInfiniteQuery({
  queryKey: qk.cases(status),
  staleTime: staleTimes.list,
  initialPageParam: null as string | null,
  queryFn: ({ pageParam }) => http<CasePage>(`/api/enforcement/cases?limit=100${status ? `&status=${status}` : ''}${
    pageParam ? `&after=${encodeURIComponent(pageParam)}` : ''}`),
  getNextPageParam: (last) => last.nextCursor ?? null,
});

export type CaseAction = 'sanction' | 'dismiss' | 'appeal' | 'appeal-decision' | 'close';

export function useCaseAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: CaseAction; body: Record<string, unknown> }) =>
      http<CaseRecord>(`/api/enforcement/cases/${id}/${action}`, { method: 'POST', body }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['enforcement'] });
    },
  });
}
