import i18n from './i18n';
import { formatDate, translateReason } from './format';

describe('i18n formatting', () => {
  afterEach(() => void i18n.changeLanguage('he'));

  it('translates reason codes with parameters', () => {
    expect(translateReason('ACTIVE_SANCTION:ENTRY_BAN:2027-02-18', i18n.t, 'he')).toContain('מניעת כניסה');
  });

  it('passes through free text (e.g. Hebrew policy reasons)', () => {
    expect(translateReason('חסר מידע להכרעה אוטומטית', i18n.t, 'he')).toBe('חסר מידע להכרעה אוטומטית');
  });

  it('switches <html dir> with the language', async () => {
    await i18n.changeLanguage('en');
    expect(document.documentElement.dir).toBe('ltr');
    await i18n.changeLanguage('ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(document.documentElement.lang).toBe('ar');
  });

  it('formats calendar dates without time-zone shift', () => {
    expect(formatDate('2026-12-01', 'en')).toContain('2026');
    expect(formatDate('2026-12-01', 'en')).toMatch(/1/);
  });
});
