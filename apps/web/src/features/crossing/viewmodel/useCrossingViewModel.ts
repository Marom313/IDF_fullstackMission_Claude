import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../../../shared/api/errors';
import { formatDateTime, translateReason } from '../../../shared/i18n/format';
import { qrPublicKey, useEligibilityCheck, useRecordCrossing, verifyQrOnServer, type Purpose } from '../model/crossingApi';
import { isWebCryptoEd25519Error, verifyQrOffline } from '../model/qr';

export type Tone = 'allow' | 'deny' | 'refer';
const GATES = ['GATE-NORTH-1', 'GATE-NORTH-2', 'GATE-CENTER-1', 'GATE-CENTER-2', 'GATE-SOUTH-1'];

/** All crossing-terminal state and logic. The view only renders what this returns. */
export function useCrossingViewModel() {
  const { t, i18n } = useTranslation();
  const [nationalId, setNationalId] = useState('');
  const [purpose, setPurpose] = useState<Purpose>('ENTRY');
  const [gate, setGate] = useState(GATES[2]!);
  const [qrText, setQrText] = useState('');
  const [qrState, setQrState] = useState<{ valid: boolean; offline: boolean; nationalId?: string } | null>(null);
  const [recorded, setRecorded] = useState<string | null>(null);
  const check = useEligibilityCheck();
  const record = useRecordCrossing();

  const idValid = /^\d{9}$/.test(nationalId);
  const data = check.data;

  // revoke object URLs of previous photos (memory leak guard)
  useEffect(() => () => {
    if (data?.photoUrl) URL.revokeObjectURL(data.photoUrl);
  }, [data?.photoUrl]);

  const runCheck = (id = nationalId) => {
    if (!/^\d{9}$/.test(id)) return;
    setRecorded(null);
    check.mutate({ nationalId: id, purpose });
  };

  const verifyQr = async () => {
    const code = qrText.trim();
    if (!code) return;
    let offline = true;
    let payload: { n: string } | null = null;
    try {
      payload = await verifyQrOffline(code, await qrPublicKey());
    } catch (e) {
      if (!isWebCryptoEd25519Error(e)) throw e;
      offline = false; // browser without Ed25519 in WebCrypto → verify on the server
      const r = await verifyQrOnServer(code);
      payload = r.valid && r.payload ? (JSON.parse(r.payload) as { n: string }) : null;
    }
    setQrState({ valid: !!payload, offline, nationalId: payload?.n });
    if (payload) {
      setNationalId(payload.n);
      runCheck(payload.n); // signature OK offline → then confirm eligibility with the server
    }
  };

  const recordCrossing = (direction: 'ENTRY' | 'EXIT') => {
    if (!data?.result.personId) return;
    record.mutate({ personId: data.result.personId, nationalId: data.result.nationalId, gate, direction }, {
      onSuccess: () => setRecorded(direction),
    });
  };

  const decision = data?.result.decision;
  const tone: Tone | null = decision === 'ALLOW' ? 'allow' : decision === 'DENY' ? 'deny' : decision ? 'refer' : null;

  return {
    nationalId, setNationalId: (v: string) => setNationalId(v.replace(/\D/g, '').slice(0, 9)), idValid,
    purpose, setPurpose, purposes: ['ENTRY', 'EXIT', 'WORK'] as Purpose[],
    gate, setGate, gates: GATES,
    runCheck: () => runCheck(), checking: check.isPending,
    error: check.error ? errorMessage(check.error, t) : null,
    result: data ? {
      tone: tone!,
      decisionLabel: t(`decision.${decision}`),
      fullName: data.result.fullName ?? '',
      nationalId: data.result.nationalId ?? '',
      photoUrl: data.photoUrl,
      reasons: (data.result.reasons ?? []).map((r) => translateReason(r, t, i18n.language)),
      policyVersion: data.result.policyVersion ?? '–',
      checkedAt: formatDateTime(data.result.checkedAt, i18n.language),
      latencyMs: data.latencyMs,
      cacheHit: !!data.result.cacheHit,
    } : null,
    qrText, setQrText, verifyQr: () => void verifyQr(), qrState,
    recordCrossing, recording: record.isPending, recorded,
    recordError: record.error ? errorMessage(record.error, t) : null,
  };
}

export type CrossingViewModel = ReturnType<typeof useCrossingViewModel>;
