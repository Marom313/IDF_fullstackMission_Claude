import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Badge, Button, Card, DefList, Field, Ltr, PageTitle, QueryState, Row, Select, Textarea } from '../../../shared/ui';
import { VirtualTable } from '../../../shared/ui/VirtualTable';
import { useEnforcementViewModel } from '../viewmodel/useEnforcementViewModel';

type CaseRow = ReturnType<typeof useEnforcementViewModel>['rows'][number];

export function EnforcementPage() {
  const vm = useEnforcementViewModel();
  const { t } = useTranslation();
  const columns = useMemo<ColumnDef<CaseRow, unknown>[]>(() => [
    { header: t('common.nationalId'), accessorKey: 'nationalId', cell: (c) => <Ltr>{String(c.getValue())}</Ltr> },
    { header: t('enforcement.violation'), accessorKey: 'violation' },
    { header: t('common.status'), cell: (c) => <Badge tone={c.row.original.tone}>{c.row.original.statusLabel}</Badge> },
    { header: t('enforcement.proposed'), accessorKey: 'proposed' },
    { header: t('enforcement.until'), accessorKey: 'until' },
    { header: t('enforcement.opened'), accessorKey: 'opened' },
    { header: t('common.actions'), cell: (c) => (
      <Button variant="secondary" onClick={() => vm.select(c.row.original.raw)}>{t('common.actions')}</Button>) },
  ], [t, vm]);

  return (
    <>
      <PageTitle>{t('enforcement.title')}</PageTitle>
      {vm.selected && (
        <Card title={`${vm.selected.violation} – ${vm.selected.nationalId}`}>
          <DefList items={[
            [t('common.status'), <Badge key="s" tone={vm.selected.tone}>{vm.selected.statusLabel}</Badge>],
            [t('enforcement.violation'), vm.selected.details],
            [t('enforcement.until'), vm.selected.until],
            ...(vm.selected.appeal ? [[t('enforcement.appealText'), vm.selected.appeal] as [string, string]] : []),
          ]} />
          <Row>
            {vm.selected.actions.includes('sanction') && <Button variant="danger" disabled={vm.busy} onClick={vm.impose}>{t('enforcement.impose')}</Button>}
            {vm.selected.actions.includes('dismiss') && <Button variant="secondary" disabled={vm.busy} onClick={vm.dismiss}>{t('enforcement.dismiss')}</Button>}
            {vm.selected.actions.includes('appeal-decision') && (
              <>
                <Button variant="danger" disabled={vm.busy} onClick={() => vm.decideAppeal(true)}>{t('enforcement.uphold')}</Button>
                <Button variant="success" disabled={vm.busy} onClick={() => vm.decideAppeal(false)}>{t('enforcement.overturn')}</Button>
              </>
            )}
            {vm.selected.actions.includes('close') && <Button variant="secondary" disabled={vm.busy} onClick={vm.close}>{t('enforcement.closeCase')}</Button>}
          </Row>
          {vm.selected.actions.includes('appeal') && (
            <>
              <Field label={t('enforcement.appealText')} required>
                {({ id }) => <Textarea id={id} value={vm.appealText} onChange={(e) => vm.setAppealText(e.target.value)} />}
              </Field>
              <Button disabled={vm.busy || !vm.appealText.trim()} onClick={vm.fileAppeal}>{t('enforcement.appeal')}</Button>
            </>
          )}
          {vm.actionError && <Alert>{vm.actionError}</Alert>}
        </Card>
      )}
      <Card>
        <Field label={t('common.status')}>
          {({ id }) => (
            <Select id={id} value={vm.status} onChange={(e) => vm.setStatus(e.target.value)}>
              {vm.statuses.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </Select>
          )}
        </Field>
        <QueryState isLoading={vm.loading} error={vm.error}>
          <VirtualTable data={vm.rows} columns={columns} caption={t('enforcement.title')} hasMore={vm.hasMore}
            onLoadMore={vm.loadMore} loadingMore={vm.loadingMore} rowHeight={52} />
        </QueryState>
      </Card>
    </>
  );
}
