import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from '../../../shared/api/http';
import { qk, staleTimes } from '../../../shared/api/queryClient';
import type { DecisionRequest, PermitDto, PermitPage } from '../../../shared/api/types';

/** Keyset pagination: the server returns nextCursor; we never use OFFSET. */
export const useQueue = () => useInfiniteQuery({
  queryKey: qk.queue,
  staleTime: staleTimes.list,
  initialPageParam: null as string | null,
  queryFn: ({ pageParam }) => http<PermitPage>(`/api/permits/applications/queue?limit=100${pageParam
    ? `&after=${encodeURIComponent(pageParam)}` : ''}`),
  getNextPageParam: (last) => last.nextCursor ?? null,
});

export function useDecide() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: DecisionRequest }) =>
      http<PermitDto>(`/api/permits/applications/${id}/decision`, { method: 'POST', body }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['permits'] });
    },
  });
}
