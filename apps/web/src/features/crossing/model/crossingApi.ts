import { useMutation } from '@tanstack/react-query';
import { http, httpBlobUrl } from '../../../shared/api/http';
import type { CheckResponse, CrossingRequest } from '../../../shared/api/types';

export type Purpose = 'ENTRY' | 'EXIT' | 'WORK';

export interface TimedCheck {
  result: CheckResponse;
  latencyMs: number;
  photoUrl: string | null;
}

/** Eligibility check is "live" data → a mutation (never cached in the browser). */
export function useEligibilityCheck() {
  return useMutation({
    mutationFn: async ({ nationalId, purpose }: { nationalId: string; purpose: Purpose }): Promise<TimedCheck> => {
      const t0 = performance.now();
      const result = await http<CheckResponse>(
        `/api/eligibility/check?nationalId=${encodeURIComponent(nationalId)}&purpose=${purpose}`);
      const latencyMs = Math.round(performance.now() - t0);
      const photoUrl = result.photoUrl ? await httpBlobUrl(result.photoUrl).catch(() => null) : null;
      return { result, latencyMs, photoUrl };
    },
  });
}

export function useRecordCrossing() {
  return useMutation({
    mutationFn: (req: CrossingRequest) => http<void>('/api/enforcement/crossings', { method: 'POST', body: req }),
  });
}

const KEY_CACHE = 'ipp.qr.publicKey';

/** Public key is cached locally so the terminal can verify QR signatures while offline. */
export async function qrPublicKey(): Promise<string> {
  try {
    const cached = localStorage.getItem(KEY_CACHE);
    if (cached) return cached;
  } catch {
    /* ignore */
  }
  const r = await http<{ key: string }>('/api/permits/keys/qr');
  try {
    localStorage.setItem(KEY_CACHE, r.key);
  } catch {
    /* ignore */
  }
  return r.key;
}

export async function verifyQrOnServer(qr: string): Promise<{ valid: boolean; payload?: string }> {
  return http('/api/permits/qr/verify', { method: 'POST', body: { qr } });
}
