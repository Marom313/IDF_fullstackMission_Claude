// CI check: every translation key exists (non-empty) in all 4 languages.
import { readFileSync } from 'node:fs';

const langs = ['he', 'en', 'ru', 'ar'];
const flatten = (obj, prefix = '') =>
  Object.entries(obj).flatMap(([k, v]) =>
    typeof v === 'object' && v !== null ? flatten(v, `${prefix}${k}.`) : [[`${prefix}${k}`, v]]);

const maps = Object.fromEntries(langs.map((l) => {
  const json = JSON.parse(readFileSync(new URL(`../src/shared/i18n/locales/${l}.json`, import.meta.url), 'utf8'));
  return [l, new Map(flatten(json))];
}));
const allKeys = new Set(langs.flatMap((l) => [...maps[l].keys()]));
const problems = [];
for (const key of allKeys) {
  for (const l of langs) {
    const v = maps[l].get(key);
    if (v === undefined || String(v).trim() === '') problems.push(`${l}: missing "${key}"`);
  }
}
if (problems.length) {
  console.error(`✖ i18n: ${problems.length} missing translations\n` + problems.slice(0, 50).join('\n'));
  process.exit(1);
}
console.log(`✔ i18n: ${allKeys.size} keys × ${langs.length} languages complete`);
