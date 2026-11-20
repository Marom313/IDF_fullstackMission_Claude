import type { TFunction } from 'i18next';

/** Server stores/returns UTC (ISO-8601); the UI always shows Asia/Jerusalem in the user's locale. */
export const TZ = 'Asia/Jerusalem';

const localeOf = (lng: string) => ({ he: 'he-IL', en: 'en-GB', ru: 'ru-RU', ar: 'ar-EG' })[lng] ?? 'he-IL';

export function formatDateTime(iso: string | undefined | null, lng: string): string {
  if (!iso) return '–';
  return new Intl.DateTimeFormat(localeOf(lng), { dateStyle: 'short', timeStyle: 'short', timeZone: TZ })
    .format(new Date(iso));
}

/** Plain dates (yyyy-mm-dd) are calendar dates – format them without shifting time zones. */
export function formatDate(day: string | undefined | null, lng: string): string {
  if (!day) return '–';
  const [y, m, d] = day.slice(0, 10).split('-').map(Number);
  return new Intl.DateTimeFormat(localeOf(lng), { dateStyle: 'medium', timeZone: 'UTC' })
    .format(new Date(Date.UTC(y!, (m ?? 1) - 1, d ?? 1)));
}

export function formatNumber(n: number | undefined | null, lng: string, opts?: Intl.NumberFormatOptions): string {
  if (n === undefined || n === null || Number.isNaN(n)) return '–';
  return new Intl.NumberFormat(localeOf(lng), opts).format(n);
}

/**
 * Reasons from the backend are machine codes like "VALID_PERMIT:WORK_ENTRY:2027-01-10".
 * Known codes are translated with their parameters; unknown text (e.g. Hebrew policy reasons) passes through.
 */
export function translateReason(raw: string, t: TFunction, lng: string): string {
  const [code, ...params] = raw.split(':');
  if (!code || !/^[A-Z_]+$/.test(code)) return raw;
  const key = `reason.${code}`;
  const p = params.map((v) => {
    if (/^\d{4}-\d{2}-\d{2}$/.test(v)) return formatDate(v, lng);
    const tr = t(`permitType.${v}`, { defaultValue: '' }) || t(`enforcement.${v}`, { defaultValue: '' })
      || t(`region.${v}`, { defaultValue: '' });
    return tr || v;
  });
  const out = t(key, { p1: p[0] ?? '', p2: p[1] ?? '', defaultValue: '' });
  return out || raw;
}
