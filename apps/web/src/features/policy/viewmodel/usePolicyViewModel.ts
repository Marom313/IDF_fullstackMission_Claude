import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../../../shared/api/errors';
import type { ValidationReport } from '../../../shared/api/types';
import { formatDate } from '../../../shared/i18n/format';
import { toneOfStatus } from '../../../shared/ui';
import { usePolicies, usePolicyDetail, useSaveAndPublish, useSimulate, validatePolicy } from '../model/policyApi';

/** Bump the VERSION number in the DSL header so a modified rule is saved as a new version. */
export function bumpVersion(source: string): string {
  return source.replace(/VERSION\s+(\d+)/, (_m, v: string) => `VERSION ${Number(v) + 1}`);
}

const KEYWORDS = /\b(RULE|VERSION|EFFECTIVE|APPLIES|TO|REQUIRE|FIELDS|WHEN|AND|OR|NOT|THEN|ELSE|APPROVE|REJECT|REFER_TO_CLERK|VALIDITY|REASON)\b/g;

/** Tokenised lines for read-only syntax highlighting next to the editor. */
export function highlight(source: string): Array<Array<{ text: string; kind: 'kw' | 'str' | 'num' | 'txt' }>> {
  return source.split('\n').map((line) => {
    const out: Array<{ text: string; kind: 'kw' | 'str' | 'num' | 'txt' }> = [];
    const re = new RegExp(`("[^"]*")|${KEYWORDS.source}|(\\b\\d+[dD]?\\b)`, 'g');
    let last = 0;
    for (const m of line.matchAll(re)) {
      if (m.index! > last) out.push({ text: line.slice(last, m.index), kind: 'txt' });
      out.push({ text: m[0], kind: m[1] ? 'str' : m[3] ? 'num' : 'kw' });
      last = m.index! + m[0].length;
    }
    if (last < line.length) out.push({ text: line.slice(last), kind: 'txt' });
    return out;
  });
}

export function usePolicyViewModel() {
  const { t, i18n } = useTranslation();
  const list = usePolicies();
  const [selected, setSelected] = useState<{ name: string; version: number } | null>(null);
  const detail = usePolicyDetail(selected?.name, selected?.version);
  const [source, setSource] = useState('');
  const [report, setReport] = useState<ValidationReport | null>(null);
  const simulate = useSimulate();
  const save = useSaveAndPublish();

  useEffect(() => {
    if (detail.data?.source) setSource(bumpVersion(detail.data.source));
  }, [detail.data?.source]);

  // live validation, debounced 400ms, previous request aborted
  useEffect(() => {
    if (!source.trim()) return undefined;
    const ctrl = new AbortController();
    const h = setTimeout(() => {
      validatePolicy(source, ctrl.signal).then(setReport).catch(() => undefined);
    }, 400);
    return () => { clearTimeout(h); ctrl.abort(); };
  }, [source]);

  const sim = simulate.data;
  return {
    policies: (list.data ?? []).map((p) => ({
      key: `${p.name}@${p.version}`, name: p.name!, version: p.version!, status: p.status!,
      statusLabel: t(`status.${p.status}`, { defaultValue: p.status ?? '' }), tone: toneOfStatus(p.status === 'ACTIVE' ? 'ACTIVE' : 'OPEN'),
      effective: formatDate(p.effectiveFrom, i18n.language), required: (p.requiredFields ?? []).join(', '),
      selected: selected?.name === p.name && selected?.version === p.version,
    })),
    loading: list.isLoading,
    error: list.error ? errorMessage(list.error, t) : null,
    select: (name: string, version: number) => { setSelected({ name, version }); simulate.reset(); save.reset(); },
    source, setSource,
    highlighted: useMemo(() => highlight(source), [source]),
    validity: report ? {
      valid: !!report.valid,
      errors: (report.errors ?? []).map((e) => `${e.line ? t('policy.line', { line: e.line }) + ': ' : ''}${e.message}`),
      required: (report.requiredFields ?? []).join(', '),
    } : null,
    simulate: () => simulate.mutate(source), simulating: simulate.isPending,
    simulation: sim ? {
      total: sim.total ?? 0, changed: sim.changed ?? 0, elapsedMs: Math.round((sim.elapsedMicros ?? 0) / 1000),
      outcomes: Object.keys({ ...(sim.baselineCounts ?? {}), ...(sim.candidateCounts ?? {}) }).map((k) => ({
        outcome: t(`decision.${k}`, { defaultValue: k }),
        before: (sim.baselineCounts as Record<string, number> | undefined)?.[k] ?? 0,
        after: (sim.candidateCounts as Record<string, number> | undefined)?.[k] ?? 0,
      })),
    } : null,
    simError: simulate.error ? errorMessage(simulate.error, t) : null,
    saveDraft: () => save.mutate({ source, publish: false }),
    publish: () => save.mutate({ source, publish: true }),
    saving: save.isPending,
    saved: save.data ? `${save.data.name} v${save.data.version}` : null,
    saveError: save.error ? errorMessage(save.error, t) : null,
    canSave: !!report?.valid,
  };
}
