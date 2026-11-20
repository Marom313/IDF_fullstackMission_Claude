import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Badge, Button, Card, Field, Grid, Input, Ltr, PageTitle, QueryState, Row, Select } from '../../../shared/ui';
import { VirtualTable } from '../../../shared/ui/VirtualTable';
import { useEmployerViewModel, type WorkerRow } from '../viewmodel/useEmployerViewModel';

export function EmployerPage() {
  const vm = useEmployerViewModel();
  const { t } = useTranslation();
  const columns = useMemo<ColumnDef<WorkerRow, unknown>[]>(() => [
    { header: t('common.nationalId'), accessorKey: 'nationalId', cell: (c) => <Ltr>{String(c.getValue())}</Ltr> },
    { header: t('common.name'), accessorKey: 'name' },
    { header: t('common.region'), accessorKey: 'region' },
    { header: t('profile.security'), accessorKey: 'security' },
  ], [t]);

  return (
    <>
      <PageTitle>{t('employer.title')}</PageTitle>
      <Grid>
        <Card title={t('employer.checkWorker')}>
          <form onSubmit={(e) => { e.preventDefault(); vm.runCheck(); }}>
            <Row>
              <Field label={t('common.nationalId')}>
                {({ id }) => <Input id={id} dir="ltr" inputMode="numeric" value={vm.checkId} onChange={(e) => vm.setCheckId(e.target.value)} />}
              </Field>
              <Button type="submit">{t('crossing.check')}</Button>
            </Row>
          </form>
          {vm.checkError && <Alert>{vm.checkError}</Alert>}
          {vm.checkResult && (
            <div aria-live="polite">
              <Badge tone={vm.checkResult.tone}>{vm.checkResult.decision}</Badge>
              <ul>{vm.checkResult.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
            </div>
          )}
        </Card>
        <Card title={t('employer.quotas')}>
          {vm.quotas.map((q) => (
            <div key={q.sector}>
              <label htmlFor={`q-${q.sector}`}>{q.sector}: <Ltr>{q.label}</Ltr></label>
              <meter id={`q-${q.sector}`} min={0} max={q.total} value={q.used} high={q.total * 0.9} optimum={0}
                className="quota-meter">{q.label}</meter>
            </div>
          ))}
        </Card>
      </Grid>
      <Card title={t('employer.workers')}>
        <Field label={t('common.region')}>
          {({ id }) => (
            <Select id={id} value={vm.region} onChange={(e) => vm.setRegion(e.target.value)}>
              {vm.regions.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
            </Select>
          )}
        </Field>
        <QueryState isLoading={vm.loading} error={vm.error}>
          <VirtualTable data={vm.rows} columns={columns} caption={t('employer.workers')} hasMore={vm.hasMore}
            onLoadMore={vm.loadMore} loadingMore={vm.loadingMore} />
        </QueryState>
      </Card>
    </>
  );
}
