import { useTranslation } from 'react-i18next';
import { Alert, Button, Card, Field, Input, Ltr, PageTitle, Row, Select, Textarea } from '../../../shared/ui';
import { useCrossingViewModel, type CrossingViewModel } from '../viewmodel/useCrossingViewModel';
import s from './CrossingPage.module.scss';

/** Tablet-first crossing terminal: big input, big buttons, high-contrast green/red verdict. */
export function CrossingPage() {
  const vm = useCrossingViewModel();
  const { t } = useTranslation();
  return (
    <>
      <PageTitle>{t('crossing.title')}</PageTitle>
      <div className={s.layout}>
        <Card>
          <form onSubmit={(e) => { e.preventDefault(); vm.runCheck(); }} noValidate>
            <Field label={t('crossing.enterId')} required>
              {({ id, describedBy }) => (
                <Input id={id} aria-describedby={describedBy} className={s.idInput} inputMode="numeric"
                  autoComplete="off" autoFocus value={vm.nationalId} onChange={(e) => vm.setNationalId(e.target.value)} />
              )}
            </Field>
            <fieldset className={s.purposes}>
              <legend>{t('crossing.purpose')}</legend>
              {vm.purposes.map((p) => (
                <label key={p}>
                  <input type="radio" name="purpose" value={p} checked={vm.purpose === p} onChange={() => vm.setPurpose(p)} />
                  {t(`crossing.purpose${p}`)}
                </label>
              ))}
            </fieldset>
            <Button type="submit" large disabled={!vm.idValid || vm.checking}>
              {vm.checking ? t('common.loading') : t('crossing.check')}
            </Button>
          </form>
          {vm.error && <Alert>{vm.error}</Alert>}
          <div aria-live="assertive" aria-atomic="true">{vm.result && <Verdict vm={vm} />}</div>
        </Card>
        <Card title={t('crossing.qrTitle')}>
          <Field label={t('crossing.qrPaste')}>
            {({ id }) => <Textarea id={id} className={s.qrArea} value={vm.qrText} onChange={(e) => vm.setQrText(e.target.value)} />}
          </Field>
          <Button variant="secondary" onClick={vm.verifyQr} disabled={!vm.qrText.trim()}>{t('crossing.verifyQr')}</Button>
          {vm.qrState && (
            <Alert info={vm.qrState.valid}>
              {vm.qrState.valid ? t('crossing.qrValid') : t('crossing.qrInvalid')}
              {vm.qrState.valid && vm.qrState.offline && ' (offline · Ed25519)'}
            </Alert>
          )}
        </Card>
      </div>
    </>
  );
}

function Verdict({ vm }: { vm: CrossingViewModel }) {
  const { t } = useTranslation();
  const r = vm.result!;
  return (
    <div className={`${s.result} ${s[r.tone]}`}>
      {r.photoUrl
        ? <img className={s.photo} src={r.photoUrl} alt={t('crossing.photoAlt', { name: r.fullName })} />
        : <div className={s.photo} aria-hidden="true" />}
      <div>
        <div className={s.verdict}>{r.tone === 'allow' ? '✔ ' : r.tone === 'deny' ? '✖ ' : '⚠ '}{r.decisionLabel}</div>
        <div className={s.name}>{r.fullName} · <Ltr>{r.nationalId}</Ltr></div>
        <ul className={s.reasons}>{r.reasons.map((x) => <li key={x}>{x}</li>)}</ul>
        <p className={s.meta}>
          {t('crossing.policyVersion')}: <Ltr>{r.policyVersion}</Ltr> · {r.checkedAt} · {t('crossing.latency')}: <Ltr>{r.latencyMs} ms</Ltr>
          {r.cacheHit && <> · {t('crossing.cached')} ✓</>}
        </p>
        <Row>
          <Field label={t('crossing.gate')}>
            {({ id }) => (
              <Select id={id} value={vm.gate} onChange={(e) => vm.setGate(e.target.value)}>
                {vm.gates.map((g) => <option key={g} value={g}>{g}</option>)}
              </Select>
            )}
          </Field>
          <Button variant="success" onClick={() => vm.recordCrossing('ENTRY')} disabled={vm.recording}>{t('crossing.recordEntry')}</Button>
          <Button variant="secondary" onClick={() => vm.recordCrossing('EXIT')} disabled={vm.recording}>{t('crossing.recordExit')}</Button>
        </Row>
        {vm.recorded && <Alert info>{t('crossing.recorded')}</Alert>}
        {vm.recordError && <Alert>{vm.recordError}</Alert>}
      </div>
    </div>
  );
}
