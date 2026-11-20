import QRCode from 'qrcode';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { errorMessage } from '../../../shared/api/errors';
import { formatDate, translateReason } from '../../../shared/i18n/format';
import { toneOfStatus } from '../../../shared/ui';
import { fetchPdfUrl, fetchQr, useMe, useMyPermits, usePhoto } from '../model/profileApi';

export function useProfileViewModel() {
  const { t, i18n } = useTranslation();
  const me = useMe();
  const permits = useMyPermits();
  const photo = usePhoto(me.data?.photoUrl);
  const [qr, setQr] = useState<{ permitId: string; dataUrl: string; text: string } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const lng = i18n.language;

  const showQr = async (permitId: string) => {
    try {
      const b = await fetchQr(permitId);
      const dataUrl = await QRCode.toDataURL(b.qr ?? '', { errorCorrectionLevel: 'M', margin: 1, width: 260 });
      setQr({ permitId, dataUrl, text: b.qr ?? '' });
    } catch (e) {
      setActionError(errorMessage(e, t));
    }
  };

  const openPdf = async (permitId: string) => {
    try {
      window.open(await fetchPdfUrl(permitId), '_blank', 'noopener');
    } catch (e) {
      setActionError(errorMessage(e, t));
    }
  };

  const p = me.data;
  return {
    loading: me.isLoading || permits.isLoading,
    error: me.error ? errorMessage(me.error, t) : permits.error ? errorMessage(permits.error, t) : actionError,
    retry: () => { void me.refetch(); void permits.refetch(); },
    photoUrl: photo.data ?? null,
    person: p ? {
      fullName: p.fullName ?? '',
      details: [
        [t('common.nationalId'), p.nationalId ?? ''],
        [t('profile.birthDate'), formatDate(p.birthDate, lng)],
        [t('profile.magneticCard'), `${p.magneticCard ?? ''} (${t('common.validTo')} ${formatDate(p.magneticCardValidTo, lng)})`],
        [t('profile.security'), p.securityStatus ?? ''],
        [t('common.region'), t(`region.${p.region}`)],
      ] as Array<[string, string]>,
    } : null,
    permits: (permits.data ?? []).map((x) => ({
      id: x.id!,
      type: t(`permitType.${x.type}`),
      status: x.status!,
      statusLabel: t(`status.${x.status}`),
      tone: toneOfStatus(x.status),
      validity: x.validFrom ? `${formatDate(x.validFrom, lng)} – ${formatDate(x.validTo, lng)}` : '–',
      reasons: (x.reasons ?? []).map((r) => translateReason(r, t, lng)).join(' · '),
      active: x.status === 'ACTIVE',
    })),
    qr, closeQr: () => setQr(null), showQr: (id: string) => void showQr(id), openPdf: (id: string) => void openPdf(id),
  };
}
