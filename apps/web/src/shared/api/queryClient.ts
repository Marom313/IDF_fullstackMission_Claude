import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './http';

/** staleTime per data type – see `staleTimes`. Retries only for transient errors. */
export const staleTimes = {
  reference: 10 * 60_000, // quotas, policies list
  list: 30_000, // queues, cases
  live: 0, // eligibility checks: always fresh
  profile: 60_000,
};

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: staleTimes.list,
      refetchOnWindowFocus: true,
      retry: (count, err) => !(err instanceof ApiError && err.problem.status < 500) && count < 2,
    },
    mutations: { retry: false },
  },
});

export const qk = {
  me: ['registry', 'me'] as const,
  person: (id: string) => ['registry', 'person', id] as const,
  workers: (region: string) => ['registry', 'workers', region] as const,
  myPermits: ['permits', 'mine'] as const,
  queue: ['permits', 'queue'] as const,
  requiredFields: (type: string) => ['permits', 'required', type] as const,
  quotas: ['permits', 'quotas'] as const,
  permitStats: ['permits', 'stats'] as const,
  cases: (status: string) => ['enforcement', 'cases', status] as const,
  enforcementStats: ['enforcement', 'stats'] as const,
  policies: ['policies'] as const,
  audit: (q: string) => ['audit', q] as const,
  auditStats: ['audit', 'stats'] as const,
  dlt: ['admin', 'dlt'] as const,
  quarantine: ['admin', 'quarantine'] as const,
  eligStats: ['eligibility', 'stats'] as const,
};
