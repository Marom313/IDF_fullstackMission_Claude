import type { TFunction } from 'i18next';
import { ApiError } from './http';

/** Maps Problem Details to a user-facing, translated message (+ trace id for support). */
export function errorMessage(err: unknown, t: TFunction): string {
  if (err instanceof ApiError) {
    const p = err.problem;
    const base = p.status === 403 ? t('errors.forbidden')
      : p.status === 404 ? t('errors.notFound')
        : p.status === 409 ? t('errors.conflict')
          : p.status === 503 ? t('errors.unavailable')
            : p.detail ?? t('errors.generic');
    return p.traceId ? `${base} (${t('common.traceId')}: ${p.traceId.slice(0, 8)})` : base;
  }
  return t('errors.generic');
}
