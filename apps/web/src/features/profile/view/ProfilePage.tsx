import { useTranslation } from 'react-i18next';
import { Badge, Button, Card, DefList, Grid, Ltr, PageTitle, QueryState, Row, tableStyles } from '../../../shared/ui';
import { useProfileViewModel } from '../viewmodel/useProfileViewModel';

export function ProfilePage() {
  const vm = useProfileViewModel();
  const { t } = useTranslation();
  return (
    <>
      <PageTitle>{t('profile.title')}</PageTitle>
      <QueryState isLoading={vm.loading} error={vm.error} onRetry={vm.retry}>
        <Grid>
          {vm.person && (
            <Card title={vm.person.fullName}>
              <Row>
                {vm.photoUrl && <img src={vm.photoUrl} alt={vm.person.fullName} width={120} height={150} />}
                <DefList items={vm.person.details.map(([k, v]) => [k, <Ltr key={k}>{v}</Ltr>])} />
              </Row>
            </Card>
          )}
          {vm.qr && (
            <Card title={t('profile.showQr')}>
              <img src={vm.qr.dataUrl} alt="QR" width={260} height={260} />
              <p><Button variant="secondary" onClick={vm.closeQr}>{t('common.close')}</Button></p>
            </Card>
          )}
        </Grid>
        <Card title={t('profile.myPermits')}>
          {vm.permits.length === 0 ? <p>{t('profile.noPermits')}</p> : (
            <div className={tableStyles.wrap}>
              <table className={tableStyles.table}>
                <thead>
                  <tr>
                    <th scope="col">{t('common.type')}</th>
                    <th scope="col">{t('common.status')}</th>
                    <th scope="col">{t('common.validTo')}</th>
                    <th scope="col">{t('common.reasons')}</th>
                    <th scope="col">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {vm.permits.map((p) => (
                    <tr key={p.id}>
                      <td>{p.type}</td>
                      <td><Badge tone={p.tone}>{p.statusLabel}</Badge></td>
                      <td>{p.validity}</td>
                      <td>{p.reasons}</td>
                      <td>
                        {p.active && (
                          <Row>
                            <Button variant="secondary" onClick={() => vm.showQr(p.id)}>{t('profile.showQr')}</Button>
                            <Button variant="secondary" onClick={() => vm.openPdf(p.id)}>{t('profile.downloadPdf')}</Button>
                          </Row>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </QueryState>
    </>
  );
}
