import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import ar from './locales/ar.json';
import en from './locales/en.json';
import he from './locales/he.json';
import ru from './locales/ru.json';

export const LANGUAGES = [
  { code: 'he', label: 'עברית', dir: 'rtl' },
  { code: 'en', label: 'English', dir: 'ltr' },
  { code: 'ru', label: 'Русский', dir: 'ltr' },
  { code: 'ar', label: 'العربية', dir: 'rtl' },
] as const;
export type Lang = (typeof LANGUAGES)[number]['code'];

const STORAGE_KEY = 'ipp.lang';

function savedLang(): Lang {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    if (v && LANGUAGES.some((l) => l.code === v)) return v as Lang;
  } catch {
    /* storage blocked */
  }
  return 'he';
}

/** Keeps <html lang dir> in sync – screen readers and CSS logical properties depend on it. */
export function applyDocumentLanguage(lng: string) {
  const meta = LANGUAGES.find((l) => l.code === lng) ?? LANGUAGES[0];
  document.documentElement.lang = meta.code;
  document.documentElement.dir = meta.dir;
  try {
    localStorage.setItem(STORAGE_KEY, meta.code);
  } catch {
    /* ignore */
  }
}

void i18n.use(initReactI18next).init({
  resources: { he: { translation: he }, en: { translation: en }, ru: { translation: ru }, ar: { translation: ar } },
  lng: savedLang(),
  fallbackLng: 'he',
  interpolation: { escapeValue: false },
  returnNull: false,
});
applyDocumentLanguage(i18n.language);
i18n.on('languageChanged', applyDocumentLanguage);

export default i18n;
