import { useTranslation } from 'react-i18next';
import { Card, PageTitle } from '../../../shared/ui';

export function AccessibilityPage() {
  const { t } = useTranslation();
  return (
    <>
      <PageTitle>{t('a11y.title')}</PageTitle>
      <Card>
        <p>{t('a11y.p1')}</p>
        <p>{t('a11y.p2')}</p>
        <p>{t('a11y.p3')}</p>
        <p>{t('a11y.contact')}</p>
      </Card>
    </>
  );
}
