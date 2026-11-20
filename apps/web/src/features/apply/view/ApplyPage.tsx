import { useTranslation } from 'react-i18next';
import { Alert, Button, Card, Field, Input, Ltr, PageTitle, Select } from '../../../shared/ui';
import { REGIONS, useApplyViewModel } from '../viewmodel/useApplyViewModel';

export function ApplyPage() {
  const vm = useApplyViewModel();
  const { t } = useTranslation();
  return (
    <>
      <PageTitle>{t('apply.title')}</PageTitle>
      <Card>
        <form onSubmit={vm.onSubmit} noValidate>
          <Field label={t('apply.type')} required hint={t('apply.requiredByPolicy')}>
            {({ id, describedBy }) => (
              <Select id={id} aria-describedby={describedBy} value={vm.type}
                onChange={(e) => vm.setType(e.target.value as typeof vm.type)}>
                {vm.types.map((x) => <option key={x} value={x}>{t(`permitType.${x}`)}</option>)}
              </Select>
            )}
          </Field>
          {vm.onBehalf && (
            <Field label={t('apply.forPerson')} required error={vm.applicantError}>
              {({ id, describedBy, invalid }) => (
                <Input id={id} aria-describedby={describedBy} aria-invalid={invalid} inputMode="numeric" dir="ltr"
                  {...vm.register('applicantId')} />
              )}
            </Field>
          )}
          {vm.profileFields.length > 0 && <p>{t('field.fromProfile')}: {vm.profileFields.join(', ')}</p>}
          {vm.loadingFields ? <p role="status">{t('common.loading')}</p> : vm.fields.map((f) => (
            <Field key={f.name} label={f.label} required error={f.error}>
              {({ id, describedBy, invalid }) => f.kind === 'region' ? (
                <Select id={id} aria-describedby={describedBy} aria-invalid={invalid} {...vm.register(f.name)}>
                  <option value="">–</option>
                  {REGIONS.map((r) => <option key={r} value={r}>{t(`region.${r}`)}</option>)}
                </Select>
              ) : (
                <Input id={id} type={f.kind === 'date' ? 'date' : 'text'} aria-describedby={describedBy}
                  aria-invalid={invalid} {...vm.register(f.name)} />
              )}
            </Field>
          ))}
          <Button type="submit" disabled={vm.submitting || vm.loadingFields}>
            {vm.submitting ? t('common.loading') : t('common.submit')}
          </Button>
        </form>
        {vm.error && <Alert>{vm.error}</Alert>}
        {vm.submitted && (
          <Alert info>
            <p>{t('apply.submitted', { id: '' })}<Ltr>{vm.submitted.id}</Ltr></p>
            <p>{t('apply.recommendation')}: <strong>{vm.submitted.recommendation}</strong> (<Ltr>{vm.submitted.policyVersion}</Ltr>)</p>
            <ul>{vm.submitted.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
          </Alert>
        )}
      </Card>
    </>
  );
}
