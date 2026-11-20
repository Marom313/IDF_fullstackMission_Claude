import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../../../shared/api/errors';
import { formatDateTime } from '../../../shared/i18n/format';
import { useAuditSearch, useDeadLetters, useQuarantine, useReplay } from '../model/adminApi';

export function useAdminViewModel() {
  const { t, i18n } = useTranslation();
  const lng = i18n.language;
  const [entityId, setEntityId] = useState('');
  const [query, setQuery] = useState('');
  const dlt = useDeadLetters();
  const quarantine = useQuarantine();
  const audit = useAuditSearch(query);
  const replay = useReplay();

  return {
    deadLetters: (dlt.data ?? []).map((d) => ({ id: d.id!, topic: d.originalTopic ?? d.topic ?? '', error: d.error ?? '',
      at: formatDateTime(d.at, lng), replayed: !!d.replayed, preview: (d.value ?? '').slice(0, 120) })),
    dltError: dlt.error ? errorMessage(dlt.error, t) : null,
    replay: (id: string) => replay.mutate(id), replaying: replay.isPending,
    quarantine: (quarantine.data ?? []).map((f) => ({ name: f.name!, size: f.sizeBytes ?? 0,
      at: formatDateTime(f.modifiedAt, lng), reason: f.reason ?? '–' })),
    entityId, setEntityId, search: () => setQuery(entityId.trim()),
    audit: (audit.data?.items ?? []).map((a) => ({ id: a.id!, at: formatDateTime(a.at, lng), actor: a.actor ?? '',
      action: a.action ?? '', entity: `${a.entityType ?? ''} ${(a.entityId ?? '').slice(0, 8)}`, decision: a.decision ?? '',
      policyVersion: a.policyVersion ?? '' })),
    auditLoading: audit.isLoading,
  };
}
