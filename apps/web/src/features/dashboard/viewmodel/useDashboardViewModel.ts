import { useTranslation } from 'react-i18next';
import { formatNumber } from '../../../shared/i18n/format';
import { toneOfStatus } from '../../../shared/ui';
import { useAuditStats, useEligibilityStats, useEnforcementStats, usePermitStats, useQuotas } from '../model/dashboardApi';

export interface Bar { key: string; label: string; value: number; display: string; max: number; tone: ReturnType<typeof toneOfStatus> }

export function useDashboardViewModel() {
  const { t, i18n } = useTranslation();
  const lng = i18n.language;
  const permits = usePermitStats();
  const quotas = useQuotas();
  const enf = useEnforcementStats();
  const elig = useEligibilityStats();
  const audit = useAuditStats();

  const bars = (m: Record<string, number> | undefined, prefix: string): Bar[] => {
    const entries = Object.entries(m ?? {});
    const max = Math.max(1, ...entries.map(([, v]) => v));
    return entries.map(([k, v]) => ({ key: k, label: t(`${prefix}.${k}`, { defaultValue: k }), value: v,
      display: formatNumber(v, lng), max, tone: toneOfStatus(k) }));
  };

  return {
    loading: permits.isLoading,
    permitBars: bars(permits.data, 'status'),
    caseBars: bars(enf.data?.casesByStatus as Record<string, number> | undefined, 'status'),
    auditBars: bars(audit.data, 'x').slice(0, 8),
    quotas: (quotas.data ?? []).map((q) => ({ key: q.sector!, label: t(`sector.${q.sector}`), value: q.used ?? 0,
      max: q.total ?? 1, display: `${formatNumber(q.used, lng)} / ${formatNumber(q.total, lng)}`, tone: 'neutral' as const })),
    kpis: [
      { label: t('dashboard.hitRatio'), value: formatNumber(elig.data?.cacheHitRatio ?? 0, lng, { style: 'percent', maximumFractionDigits: 1 }) },
      { label: t('dashboard.crossings'), value: formatNumber(enf.data?.crossings ?? 0, lng) },
      { label: t('dashboard.permitsByStatus'), value: formatNumber(Object.values(permits.data ?? {}).reduce((a, b) => a + b, 0), lng) },
    ],
  };
}
