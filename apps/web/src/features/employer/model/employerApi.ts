import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query';
import { http } from '../../../shared/api/http';
import { qk, staleTimes } from '../../../shared/api/queryClient';
import type { CheckResponse, PersonPage, SectorQuota } from '../../../shared/api/types';

/** ABAC is enforced by the server: the employer always gets ONLY its own workers. */
export const useWorkers = (region: string) => useInfiniteQuery({
  queryKey: qk.workers(region),
  staleTime: staleTimes.list,
  initialPageParam: null as string | null,
  queryFn: ({ pageParam }) => http<PersonPage>(`/api/registry/persons?limit=100${region ? `&region=${region}` : ''}${
    pageParam ? `&after=${pageParam}` : ''}`),
  getNextPageParam: (last) => last.nextCursor ?? null,
});

export const useQuotas = () => useQuery({
  queryKey: qk.quotas, staleTime: staleTimes.reference, queryFn: () => http<SectorQuota[]>('/api/permits/quotas'),
});

export const useWorkerCheck = () => useMutation({
  mutationFn: (nationalId: string) => http<CheckResponse>(`/api/eligibility/check?nationalId=${nationalId}&purpose=WORK`),
});
