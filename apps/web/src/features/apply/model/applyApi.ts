import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '../../../shared/api/http';
import { qk, staleTimes } from '../../../shared/api/queryClient';
import type { PermitDto, SubmitRequest } from '../../../shared/api/types';

export type PermitType = 'WORK_ENTRY' | 'DRIVER' | 'MEDICAL';

/** Required fields come from the ACTIVE policy (policy-service), not from the frontend. */
export const useRequiredFields = (type: PermitType) => useQuery({
  queryKey: qk.requiredFields(type),
  staleTime: staleTimes.reference,
  queryFn: () => http<{ requiredFields: string[] }>(`/api/permits/required-fields?type=${type}`)
    .then((r) => r.requiredFields),
});

export function useSubmitApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ req, idempotencyKey }: { req: SubmitRequest; idempotencyKey: string }) =>
      http<PermitDto>('/api/permits/applications', { method: 'POST', body: req, idempotencyKey }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['permits'] });
    },
  });
}
