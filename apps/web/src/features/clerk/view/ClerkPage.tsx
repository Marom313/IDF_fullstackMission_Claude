import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Badge, Button, Card, Field, Input, Ltr, PageTitle, QueryState, Row } from '../../../shared/ui';
import { VirtualTable } from '../../../shared/ui/VirtualTable';
import { useClerkViewModel, type QueueRow } from '../viewmodel/useClerkViewModel';

export function ClerkPage() {
  const vm = useClerkViewModel();
  const { t } = useTranslation();
  const columns = useMemo<ColumnDef<QueueRow, unknown>[]>(() => [
    { header: t('common.nationalId'), accessorKey: 'nationalId', cell: (c) => <Ltr>{String(c.getValue())}</Ltr> },
    { header: t('common.type'), accessorKey: 'type' },
    { header: t('clerk.submittedAt'), accessorKey: 'submittedAt' },
    { header: t('common.status'), cell: (c) => <Badge tone="refer">{c.row.original.statusLabel}</Badge> },
    { header: t('clerk.recommendation'),
      cell: (c) => <Badge tone={c.row.original.recommendationTone}>{c.row.original.recommendation}</Badge> },
    { header: t('common.reasons'), accessorKey: 'reasons' },
    { header: t('common.actions'), cell: (c) => (
      <Row>
        <Button variant="success" disabled={vm.busy} onClick={() => vm.approve(c.row.original)}
          aria-label={`${t('common.approve')} ${c.row.original.nationalId}`}>{t('common.approve')}</Button>
        <Button variant="danger" disabled={vm.busy} onClick={() => vm.startReject(c.row.original)}
          aria-label={`${t('common.reject')} ${c.row.original.nationalId}`}>{t('common.reject')}</Button>
      </Row>
    ) },
  ], [t, vm]);

  return (
    <>
      <PageTitle>{t('clerk.title')}</PageTitle>
      {vm.decideError && <Alert>{vm.decideError}</Alert>}
      {vm.done && <Alert info>{t('clerk.decided')}</Alert>}
      {vm.rejecting && (
        <Card title={`${t('common.reject')} – ${vm.rejecting.nationalId}`}>
          <Field label={t('clerk.rejectReason')} required>
            {({ id }) => <Input id={id} autoFocus value={vm.rejectReason} onChange={(e) => vm.setRejectReason(e.target.value)} />}
          </Field>
          <Row>
            <Button variant="danger" onClick={vm.confirmReject} disabled={!vm.rejectReason.trim() || vm.busy}>{t('common.reject')}</Button>
            <Button variant="secondary" onClick={vm.cancelReject}>{t('common.cancel')}</Button>
          </Row>
        </Card>
      )}
      <QueryState isLoading={vm.loading} error={vm.error} onRetry={vm.retry}>
        <VirtualTable data={vm.rows} columns={columns} caption={t('clerk.title')} hasMore={vm.hasMore}
          onLoadMore={vm.loadMore} loadingMore={vm.loadingMore} rowHeight={56} />
      </QueryState>
    </>
  );
}
