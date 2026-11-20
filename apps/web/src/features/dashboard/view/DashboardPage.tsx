import { useTranslation } from 'react-i18next';
import { Card, Grid, Ltr, PageTitle } from '../../../shared/ui';
import { useDashboardViewModel, type Bar } from '../viewmodel/useDashboardViewModel';
import s from './DashboardPage.module.scss';

/** Bars use <meter> (accessible, no inline styles needed under the strict CSP). */
function Bars({ items, title }: { items: Bar[]; title: string }) {
  return (
    <Card title={title}>
      <ul className={s.bars}>
        {items.map((b) => (
          <li key={b.key}>
            <span className={s.barLabel}>{b.label}</span>
            <meter min={0} max={b.max} value={b.value} className={`${s.meter} ${s[b.tone]}`} aria-label={`${b.label}: ${b.display}`} />
            <Ltr>{b.display}</Ltr>
          </li>
        ))}
      </ul>
    </Card>
  );
}

export function DashboardPage() {
  const vm = useDashboardViewModel();
  const { t } = useTranslation();
  return (
    <>
      <PageTitle>{t('dashboard.title')}</PageTitle>
      <div className={s.kpis}>
        {vm.kpis.map((k) => (
          <div key={k.label} className={s.kpi}>
            <div className={s.kpiValue}><Ltr>{k.value}</Ltr></div>
            <div className={s.kpiLabel}>{k.label}</div>
          </div>
        ))}
      </div>
      <Grid>
        <Bars title={t('dashboard.permitsByStatus')} items={vm.permitBars} />
        <Bars title={t('dashboard.quotas')} items={vm.quotas} />
        <Bars title={t('dashboard.casesByStatus')} items={vm.caseBars} />
        <Bars title={t('dashboard.audit')} items={vm.auditBars} />
      </Grid>
    </>
  );
}
