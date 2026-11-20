import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../../../shared/api/errors';
import type { PermitDto } from '../../../shared/api/types';
import { formatDateTime, translateReason } from '../../../shared/i18n/format';
import { toneOfStatus } from '../../../shared/ui';
import { useDecide, useQueue } from '../model/clerkApi';

export interface QueueRow {
  id: string;
  version: number;
  nationalId: string;
  type: string;
  status: string;
  statusLabel: string;
  submittedAt: string;
  recommendation: string;
  recommendationTone: ReturnType<typeof toneOfStatus>;
  reasons: string;
}

export function useClerkViewModel() {
  const { t, i18n } = useTranslation();
  const queue = useQueue();
  const decide = useDecide();
  const [rejecting, setRejecting] = useState<QueueRow | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [done, setDone] = useState<string | null>(null);

  const rows: QueueRow[] = useMemo(() => (queue.data?.pages ?? []).flatMap((p) => p.items ?? [])
    .map((x: PermitDto) => ({
      id: x.id!,
      version: x.version ?? 0,
      nationalId: x.nationalId ?? '',
      type: t(`permitType.${x.type}`),
      status: x.status!,
      statusLabel: t(`status.${x.status}`),
      submittedAt: formatDateTime(x.submittedAt, i18n.language),
      recommendation: t(`decision.${x.recommendation}`, { defaultValue: x.recommendation ?? '' }),
      recommendationTone: toneOfStatus(x.recommendation),
      reasons: (x.reasons ?? []).map((r) => translateReason(r, t, i18n.language)).join(' · '),
    })), [queue.data, t, i18n.language]);

  const approve = (row: QueueRow) => decide.mutate({ id: row.id, body: { approve: true, version: row.version } },
    { onSuccess: () => setDone(row.id) });
  const confirmReject = () => {
    if (!rejecting || !rejectReason.trim()) return;
    decide.mutate({ id: rejecting.id, body: { approve: false, version: rejecting.version, reasons: [rejectReason.trim()] } },
      { onSuccess: () => { setDone(rejecting.id); setRejecting(null); setRejectReason(''); } });
  };

  return {
    rows,
    loading: queue.isLoading,
    error: queue.error ? errorMessage(queue.error, t) : null,
    retry: () => void queue.refetch(),
    hasMore: !!queue.hasNextPage,
    loadMore: () => void queue.fetchNextPage(),
    loadingMore: queue.isFetchingNextPage,
    approve, startReject: (r: QueueRow) => { setRejecting(r); setRejectReason(''); },
    rejecting, rejectReason, setRejectReason, confirmReject, cancelReject: () => setRejecting(null),
    busy: decide.isPending,
    decideError: decide.error ? errorMessage(decide.error, t) : null,
    done,
  };
}
