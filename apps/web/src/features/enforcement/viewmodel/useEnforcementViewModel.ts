import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../../../shared/api/errors';
import type { CaseRecord } from '../../../shared/api/types';
import { useAuth } from '../../../shared/auth/AuthContext';
import { formatDate, formatDateTime } from '../../../shared/i18n/format';
import { toneOfStatus } from '../../../shared/ui';
import { useCaseAction, useCases, type CaseAction } from '../model/enforcementApi';

export const CASE_STATUSES = ['', 'OPEN', 'SANCTIONED', 'APPEALED', 'CLOSED', 'DISMISSED'];

/** Which actions the state machine allows next (mirrors the backend; the backend still enforces it). */
export function nextActions(status: string): CaseAction[] {
  switch (status) {
    case 'OPEN': return ['sanction', 'dismiss'];
    case 'SANCTIONED': return ['appeal', 'close'];
    case 'APPEALED': return ['appeal-decision'];
    case 'UPHELD': case 'OVERTURNED': return ['close'];
    default: return [];
  }
}

export function useEnforcementViewModel() {
  const { t, i18n } = useTranslation();
  const { hasAny } = useAuth();
  const [status, setStatus] = useState('');
  const cases = useCases(status);
  const action = useCaseAction();
  const [selected, setSelected] = useState<CaseRecord | null>(null);
  const [appealText, setAppealText] = useState('');
  const lng = i18n.language;
  const canAct = hasAny('ENFORCEMENT_OFFICER', 'SYS_ADMIN');

  const rows = useMemo(() => (cases.data?.pages ?? []).flatMap((p) => p.items ?? []).map((c) => ({
    raw: c,
    id: c.id!,
    nationalId: c.nationalId ?? '–',
    violation: t(`enforcement.${c.violationType}`, { defaultValue: c.violationType ?? '' }),
    status: c.status!,
    statusLabel: t(`status.${c.status}`),
    tone: toneOfStatus(c.status),
    proposed: `${t(`enforcement.${c.proposedSanction}`, { defaultValue: c.proposedSanction ?? '' })} · ${t('enforcement.days', { n: c.proposedDays })}`,
    until: formatDate(c.sanctionUntil, lng),
    opened: formatDateTime(c.openedAt, lng),
  })), [cases.data, t, lng]);

  const run = (a: CaseAction, extra: Record<string, unknown> = {}) => {
    if (!selected) return;
    action.mutate({ id: selected.id!, action: a, body: { version: selected.version ?? 0, ...extra } },
      { onSuccess: (c) => { setSelected(c); setAppealText(''); } });
  };

  return {
    status, setStatus, statuses: CASE_STATUSES.map((s) => ({ value: s, label: s ? t(`status.${s}`) : t('common.all') })),
    rows, loading: cases.isLoading, error: cases.error ? errorMessage(cases.error, t) : null,
    hasMore: !!cases.hasNextPage, loadMore: () => void cases.fetchNextPage(), loadingMore: cases.isFetchingNextPage,
    select: (c: CaseRecord) => { setSelected(c); action.reset(); },
    selected: selected ? {
      id: selected.id!, nationalId: selected.nationalId ?? '–', details: selected.details ?? '',
      statusLabel: t(`status.${selected.status}`), tone: toneOfStatus(selected.status),
      violation: t(`enforcement.${selected.violationType}`, { defaultValue: selected.violationType ?? '' }),
      appeal: selected.appealText ?? null, until: formatDate(selected.sanctionUntil, lng),
      actions: canAct ? nextActions(selected.status ?? '') : [],
    } : null,
    appealText, setAppealText,
    impose: () => run('sanction'), dismiss: () => run('dismiss'),
    fileAppeal: () => appealText.trim() && run('appeal', { text: appealText.trim() }),
    decideAppeal: (uphold: boolean) => run('appeal-decision', { uphold }), close: () => run('close'),
    busy: action.isPending, actionError: action.error ? errorMessage(action.error, t) : null,
  };
}
