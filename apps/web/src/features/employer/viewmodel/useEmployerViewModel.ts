import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../../../shared/api/errors';
import { formatNumber, translateReason } from '../../../shared/i18n/format';
import { toneOfStatus } from '../../../shared/ui';
import { useQuotas, useWorkerCheck, useWorkers } from '../model/employerApi';

export const REGIONS = ['', 'JERUSALEM', 'TEL_AVIV', 'CENTER', 'SHARON', 'HAIFA', 'NORTH', 'SHFELA', 'SOUTH'];

export interface WorkerRow {
  id: string;
  nationalId: string;
  name: string;
  region: string;
  security: string;
}

export function useEmployerViewModel() {
  const { t, i18n } = useTranslation();
  const [region, setRegion] = useState('');
  const [checkId, setCheckId] = useState('');
  const workers = useWorkers(region);
  const quotas = useQuotas();
  const check = useWorkerCheck();
  const lng = i18n.language;

  const rows: WorkerRow[] = useMemo(() => (workers.data?.pages ?? []).flatMap((p) => p.items ?? []).map((p) => ({
    id: p.id!, nationalId: p.nationalId ?? '', name: p.fullName ?? '', region: t(`region.${p.region}`),
    security: p.securityStatus ?? '',
  })), [workers.data, t]);

  return {
    region, setRegion, regions: REGIONS.map((r) => ({ value: r, label: r ? t(`region.${r}`) : t('common.all') })),
    rows, loading: workers.isLoading, error: workers.error ? errorMessage(workers.error, t) : null,
    hasMore: !!workers.hasNextPage, loadMore: () => void workers.fetchNextPage(), loadingMore: workers.isFetchingNextPage,
    quotas: (quotas.data ?? []).map((q) => ({
      sector: t(`sector.${q.sector}`), used: q.used ?? 0, total: q.total ?? 1,
      label: `${formatNumber(q.used, lng)} / ${formatNumber(q.total, lng)}`,
    })),
    checkId, setCheckId: (v: string) => setCheckId(v.replace(/\D/g, '').slice(0, 9)),
    runCheck: () => /^\d{9}$/.test(checkId) && check.mutate(checkId),
    checkResult: check.data ? {
      decision: t(`decision.${check.data.decision}`), tone: toneOfStatus(check.data.decision),
      reasons: (check.data.reasons ?? []).map((r) => translateReason(r, t, lng)),
    } : null,
    checkError: check.error ? errorMessage(check.error, t) : null,
  };
}
