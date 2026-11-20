import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useAuth } from '../../../shared/auth/AuthContext';
import { NAV } from '../../../shared/auth/nav';
import { Button, Card, Grid, Ltr, PageTitle, tableStyles } from '../../../shared/ui';

const DEMO = [
  ['officer1', 'CROSSING_OFFICER'], ['clerk1', 'CLERK'], ['worker1', 'WORKER'], ['driver1', 'DRIVER'],
  ['employer1', 'EMPLOYER'], ['policy1', 'POLICY_ADMIN'], ['enforcer1', 'ENFORCEMENT_OFFICER'], ['auditor1', 'AUDITOR'],
  ['admin1', 'SYS_ADMIN'],
] as const;

export function HomePage() {
  const { t } = useTranslation();
  const { session, login, hasAny } = useAuth();
  return (
    <>
      <PageTitle>{t('app.title')}</PageTitle>
      <p>{t('app.subtitle')}</p>
      {session ? (
        <Grid>
          {NAV.filter((n) => hasAny(...n.roles)).map((n) => (
            <Card key={n.path} title={<Link to={n.path}>{t(n.labelKey)}</Link>} headingLevel={2}>
              <p>{t(n.labelKey)}</p>
            </Card>
          ))}
        </Grid>
      ) : (
        <Grid>
          <Card title={t('app.login')}>
            <p>{t('app.loginHint')}</p>
            <Button large onClick={() => void login()}>{t('app.login')}</Button>
          </Card>
          <Card title={t('app.demoUsers')}>
            <table className={tableStyles.table}>
              <tbody>
                {DEMO.map(([u, r]) => <tr key={u}><td><Ltr>{u}</Ltr></td><td><Ltr>{r}</Ltr></td></tr>)}
              </tbody>
            </table>
            <p>900000001 ✔ · 900000003 ✖ · 900000004 ⚠ · 900000005 ✖</p>
          </Card>
        </Grid>
      )}
    </>
  );
}
