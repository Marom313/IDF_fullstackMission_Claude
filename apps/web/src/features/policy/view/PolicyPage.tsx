import { useTranslation } from 'react-i18next';
import { Alert, Badge, Button, Card, Field, Ltr, PageTitle, QueryState, Row, tableStyles, Textarea } from '../../../shared/ui';
import { usePolicyViewModel } from '../viewmodel/usePolicyViewModel';
import s from './PolicyPage.module.scss';

export function PolicyPage() {
  const vm = usePolicyViewModel();
  const { t } = useTranslation();
  return (
    <>
      <PageTitle>{t('policy.title')}</PageTitle>
      <Row>
        <div style={{ flex: '1 1 18rem' }}>
          <Card title={t('policy.list')}>
            <QueryState isLoading={vm.loading} error={vm.error}>
              <ul className={s.list}>
                {vm.policies.map((p) => (
                  <li key={p.key}>
                    <button type="button" aria-pressed={p.selected} onClick={() => vm.select(p.name, p.version)}>
                      <strong><Ltr>{p.name}</Ltr></strong> v{p.version} <Badge tone={p.tone}>{p.statusLabel}</Badge>
                      <br /><small>{t('policy.requiredFields')}: <Ltr>{p.required}</Ltr></small>
                    </button>
                  </li>
                ))}
              </ul>
            </QueryState>
          </Card>
        </div>
        <div style={{ flex: '3 1 32rem' }}>
          <Card title={t('policy.editor')}>
            <div className={s.editorWrap}>
              <Field label={t('policy.editor')} hint="RULE … VERSION … WHEN … THEN APPROVE VALIDITY 90d ELSE REFER_TO_CLERK">
                {({ id, describedBy }) => (
                  <Textarea id={id} aria-describedby={describedBy} className={s.editor} spellCheck={false}
                    value={vm.source} onChange={(e) => vm.setSource(e.target.value)} />
                )}
              </Field>
              {vm.source && (
                <pre className={s.preview} aria-hidden="true">
                  {vm.highlighted.map((line, i) => (
                    <div key={i}>{line.map((tok, j) => <span key={j} className={tok.kind === 'txt' ? undefined : s[tok.kind]}>{tok.text}</span>)}</div>
                  ))}
                </pre>
              )}
              <div aria-live="polite">
                {vm.validity && (vm.validity.valid
                  ? <Alert info>✔ {t('policy.valid')} · {t('policy.requiredFields')}: <Ltr>{vm.validity.required}</Ltr></Alert>
                  : <Alert>{t('policy.invalid')}<ul>{vm.validity.errors.map((e) => <li key={e}>{e}</li>)}</ul></Alert>)}
              </div>
              <Row>
                <Button variant="secondary" onClick={vm.simulate} disabled={!vm.canSave || vm.simulating}>
                  {vm.simulating ? t('common.loading') : t('policy.simulate')}
                </Button>
                <Button variant="secondary" onClick={vm.saveDraft} disabled={!vm.canSave || vm.saving}>{t('policy.saveDraft')}</Button>
                <Button onClick={vm.publish} disabled={!vm.canSave || vm.saving}>{t('policy.publish')}</Button>
              </Row>
              {vm.saved && <Alert info>{t('policy.published')}: <Ltr>{vm.saved}</Ltr></Alert>}
              {(vm.saveError || vm.simError) && <Alert>{vm.saveError ?? vm.simError}</Alert>}
            </div>
          </Card>
          {vm.simulation && (
            <Card title={`${t('policy.simulate')} – ${vm.simulation.total} · ${t('policy.changed')}: ${vm.simulation.changed}`}>
              <p>{t('policy.simulateHint')} (<Ltr>{vm.simulation.elapsedMs} ms</Ltr>)</p>
              <table className={tableStyles.table}>
                <thead><tr><th scope="col">{t('clerk.recommendation')}</th><th scope="col">{t('policy.baseline')}</th><th scope="col">{t('policy.candidate')}</th></tr></thead>
                <tbody>
                  {vm.simulation.outcomes.map((o) => (
                    <tr key={o.outcome}><td>{o.outcome}</td><td>{o.before}</td><td>{o.after}</td></tr>
                  ))}
                </tbody>
              </table>
            </Card>
          )}
        </div>
      </Row>
    </>
  );
}
