import { bumpVersion, highlight } from '../viewmodel/usePolicyViewModel';

describe('policy studio helpers', () => {
  it('bumps the VERSION so an edit becomes a new version', () => {
    expect(bumpVersion('RULE "x" VERSION 3 EFFECTIVE 2026-12-01')).toBe('RULE "x" VERSION 4 EFFECTIVE 2026-12-01');
  });

  it('highlights keywords, strings and numbers', () => {
    const [line] = highlight('WHEN person.age >= 22 THEN APPROVE VALIDITY 90d REASON "ok"');
    const kinds = line!.filter((t) => t.kind !== 'txt').map((t) => `${t.kind}:${t.text}`);
    expect(kinds).toEqual(['kw:WHEN', 'num:22', 'kw:THEN', 'kw:APPROVE', 'kw:VALIDITY', 'num:90d', 'kw:REASON', 'str:"ok"']);
  });
});
