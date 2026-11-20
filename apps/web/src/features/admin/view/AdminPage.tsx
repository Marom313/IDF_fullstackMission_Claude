import { useTranslation } from 'react-i18next';
import { Alert, Badge, Button, Card, Field, Grid, Input, Ltr, PageTitle, Row, tableStyles } from '../../../shared/ui';
import { useAdminViewModel } from '../viewmodel/useAdminViewModel';

export function AdminPage() {
  const vm = useAdminViewModel();
  const { t } = useTranslation();
  return (
    <>
      <PageTitle>{t('admin.title')}</PageTitle>
      <Grid>
        <Card title={t('admin.dlt')}>
          {vm.dltError && <Alert>{vm.dltError}</Alert>}
          {vm.deadLetters.length === 0 ? <p>{t('admin.noDlt')}</p> : (
            <ul>
              {vm.deadLetters.map((d) => (
                <li key={d.id}>
                  <Ltr>{d.topic}</Ltr> · {d.at} {d.replayed && <Badge tone="allow">{t('admin.replayed')}</Badge>}
                  <br /><small>{t('admin.reason')}: {d.error}</small>
                  <br /><code dir="ltr">{d.preview}</code>
                  <br /><Button variant="secondary" disabled={vm.replaying || d.replayed} onClick={() => vm.replay(d.id)}>{t('admin.replay')}</Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card title={t('admin.quarantine')}>
          {vm.quarantine.length === 0 ? <p>{t('admin.noQuarantine')}</p> : (
            <ul>{vm.quarantine.map((f) => <li key={f.name}><Ltr>{f.name}</Ltr> · {f.at}<br /><small>{t('admin.reason')}: {f.reason}</small></li>)}</ul>
          )}
        </Card>
      </Grid>
      <Card title={t('admin.audit')}>
        <form onSubmit={(e) => { e.preventDefault(); vm.search(); }}>
          <Row>
            <Field label={t('admin.entity')}>
              {({ id }) => <Input id={id} dir="ltr" value={vm.entityId} onChange={(e) => vm.setEntityId(e.target.value)} />}
            </Field>
            <Button type="submit">{t('common.search')}</Button>
          </Row>
        </form>
        <div className={tableStyles.wrap}>
          <table className={tableStyles.table}>
            <thead>
              <tr>
                <th scope="col">{t('common.date')}</th><th scope="col">{t('admin.actor')}</th>
                <th scope="col">{t('admin.action')}</th><th scope="col">{t('admin.entity')}</th>
                <th scope="col">{t('common.status')}</th><th scope="col">{t('crossing.policyVersion')}</th>
              </tr>
            </thead>
            <tbody>
              {vm.audit.map((a) => (
                <tr key={a.id}>
                  <td>{a.at}</td><td><Ltr>{a.actor}</Ltr></td><td><Ltr>{a.action}</Ltr></td>
                  <td><Ltr>{a.entity}</Ltr></td><td>{a.decision}</td><td><Ltr>{a.policyVersion}</Ltr></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
