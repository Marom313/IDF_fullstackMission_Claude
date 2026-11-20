#!/usr/bin/env node
// IPP end-to-end smoke test against the running stack (http://localhost). Re-runnable.
// Usage: node scripts/smoke.mjs        (ipp smoke)
// Covers: SPA + CSP, auth for all 9 roles, eligibility (4 demo ids), RBAC/ABAC, profile/photo/QR/PDF,
// policy-driven applications + clerk approval → Kafka → eligibility, policy studio, signed ingest batch →
// automatic sanction (closing the loop) → appeal → lifted, audit, admin, chaos/circuit breaker, SSE, observability.
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.BASE_URL ?? 'http://localhost';
const root = join(dirname(fileURLToPath(import.meta.url)), '..');
let pass = 0;
let fail = 0;
const ok = (cond, name, extra = '') => {
  if (cond) { pass++; console.log(`  ✔ ${name}${extra ? ` – ${extra}` : ''}`); } else { fail++; console.log(`  ✖ ${name}${extra ? ` – ${extra}` : ''}`); }
  return cond;
};
const section = (s) => console.log(`\n▶ ${s}`);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const rnd = () => Math.random().toString(36).slice(2, 10);

async function token(user) {
  const r = await fetch(`${BASE}/auth/realms/ipp/protocol/openid-connect/token`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'password', client_id: 'ipp-web', username: user, password: 'Passw0rd!' }),
  });
  return (await r.json()).access_token;
}
async function api(tok, path, { method = 'GET', body, idem } = {}) {
  const headers = { Accept: 'application/json' };
  if (tok) headers.Authorization = `Bearer ${tok}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (idem) headers['Idempotency-Key'] = idem;
  const r = await fetch(`${BASE}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await r.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* binary or html */ }
  return { status: r.status, json, text, headers: r.headers };
}
async function until(fn, ms = 30000, step = 1000) {
  const end = Date.now() + ms;
  for (;;) {
    const v = await fn();
    if (v) return v;
    if (Date.now() > end) return null;
    await sleep(step);
  }
}
const check = (tok, nid, purpose = 'ENTRY') => api(tok, `/api/eligibility/check?nationalId=${nid}&purpose=${purpose}`);

section('frontend & security headers');
{
  const r = await fetch(`${BASE}/crossing`);
  const html = await r.text();
  ok(r.status === 200 && html.includes('<div id="root">'), 'SPA served (deep link → index.html)');
  const csp = r.headers.get('content-security-policy') ?? '';
  ok(csp.includes("script-src 'self'") && csp.includes("frame-ancestors 'none'"), 'strict CSP');
  ok(r.headers.get('x-frame-options') === 'DENY', 'X-Frame-Options DENY');
}

section('authentication (Keycloak, 9 roles)');
const users = ['worker1', 'driver1', 'employer1', 'officer1', 'clerk1', 'policy1', 'enforcer1', 'auditor1', 'admin1'];
const T = {};
for (const u of users) T[u] = await token(u);
ok(users.every((u) => T[u]), 'tokens for all demo users');
ok((await api(null, '/api/registry/persons/me')).status === 401, 'no token → 401');

section('eligibility hot path (demo ids)');
for (const [nid, want, reason] of [['900000001', 'ALLOW', 'VALID_PERMIT'], ['900000003', 'DENY', 'ACTIVE_SANCTION'],
  ['900000004', 'REFER_TO_CLERK', 'SECURITY_PENDING'], ['900000005', null, null]]) {
  const r = await check(T.officer1, nid);
  if (want) ok(r.json?.decision === want && r.json.reasons?.[0]?.startsWith(reason), `${nid} → ${want}`, r.json?.reasons?.[0]);
  else ok(r.status === 200 && r.json?.decision === 'DENY', `${nid} → DENY`, r.json?.reasons?.[0]);
}
const again = await check(T.officer1, '900000001');
ok(again.json?.cacheHit === true, 'second check served from Redis cache');
ok((await check(T.worker1, '900000001')).status === 403, 'RBAC: worker cannot run eligibility checks (403)');

section('worker profile, photo, QR, PDF');
const me = await api(T.worker1, '/api/registry/persons/me');
ok(me.status === 200 && me.json?.nationalId === '900000001', 'worker1 profile');
const photo = await fetch(`${BASE}${me.json?.photoUrl}`, { headers: { Authorization: `Bearer ${T.worker1}` } });
ok(photo.status === 200 && (photo.headers.get('content-type') ?? '').includes('svg'), 'photo (SeaweedFS S3, generated silhouette)');
const mine = await api(T.worker1, '/api/permits/mine');
const active = mine.json?.find((p) => p.status === 'ACTIVE');
ok(!!active, 'worker1 has an ACTIVE permit');
const qr = await api(T.worker1, `/api/permits/${active?.id}/qr`);
ok(!!qr.json?.qr, 'signed QR issued');
ok((await api(null, '/api/permits/qr/verify', { method: 'POST', body: { qr: qr.json?.qr } })).status === 401,
  'QR verify endpoint requires auth');
ok((await api(T.officer1, '/api/permits/qr/verify', { method: 'POST', body: { qr: qr.json?.qr } })).json?.valid === true, 'QR signature valid');
ok((await api(T.officer1, '/api/permits/qr/verify', { method: 'POST', body: { qr: `x${qr.json?.qr}` } })).json?.valid === false, 'tampered QR rejected');
ok((await api(null, '/api/permits/keys/qr')).json?.alg === 'Ed25519', 'public key reachable without login (offline terminals)');
const pdf = await fetch(`${BASE}/api/permits/${active?.id}/pdf`, { headers: { Authorization: `Bearer ${T.worker1}` } });
const pdfBytes = Buffer.from(await pdf.arrayBuffer());
ok(pdf.status === 200 && pdfBytes.subarray(0, 4).toString() === '%PDF', 'SPECIMEN permit PDF', `${pdfBytes.length} bytes`);
ok((await api(T.worker1, `/api/registry/persons/by-national-id/900000003`)).status === 404, 'IDOR: worker cannot read another person (404)');

section('applications (policy-driven) → clerk → Kafka → eligibility');
const req = await api(T.employer1, '/api/permits/required-fields?type=WORK_ENTRY');
ok(req.json?.requiredFields?.includes('workRegion'), 'required fields come from the active policy', req.json?.requiredFields?.join(','));
const workers = await api(T.employer1, '/api/registry/persons?limit=200');
ok(workers.status === 200 && workers.json.items.length > 0, 'employer portal lists workers', `${workers.json?.items?.length}`);
ok(workers.json?.items?.every((w) => w.employerId === workers.json.items[0].employerId), 'ABAC: only own workers');
const foreign = await api(T.employer1, '/api/permits/applications', { method: 'POST', idem: rnd(),
  body: { nationalId: '980000010', type: 'WORK_ENTRY', formData: { magneticCard: 'MC', employerId: 'x', workRegion: 'CENTER' } } });
ok(foreign.status === 404, 'ABAC: employer cannot apply for a foreign worker');
const target = workers.json.items[Math.floor(Math.random() * workers.json.items.length)];
const idem = `smoke-${rnd()}`;
const sub = await api(T.employer1, '/api/permits/applications', { method: 'POST', idem,
  body: { nationalId: target.nationalId, type: 'WORK_ENTRY', formData: { magneticCard: 'MC-1', employerId: target.employerId, workRegion: target.region } } });
ok(sub.status === 201 && sub.json?.policyVersion?.includes('@v'), 'application submitted + evaluated by policy-service', `${sub.json?.recommendation} ${sub.json?.policyVersion}`);
const dup = await api(T.employer1, '/api/permits/applications', { method: 'POST', idem,
  body: { nationalId: target.nationalId, type: 'WORK_ENTRY', formData: { magneticCard: 'MC-1', employerId: target.employerId, workRegion: target.region } } });
ok(dup.json?.id === sub.json?.id, 'Idempotency-Key: retry returns the same application');
const dec = await api(T.clerk1, `/api/permits/applications/${sub.json?.id}/decision`, { method: 'POST', body: { approve: true, version: sub.json?.version ?? 0 } });
ok(dec.json?.status === 'ACTIVE', 'clerk approves → ACTIVE', `${dec.json?.validFrom}..${dec.json?.validTo}`);
const stale = await api(T.clerk1, `/api/permits/applications/${sub.json?.id}/decision`, { method: 'POST', body: { approve: false, reasons: ['x'], version: 0 } });
ok(stale.status === 409, 'optimistic locking: stale version → 409');
const elig = await until(async () => {
  const r = await check(T.officer1, target.nationalId, 'WORK');
  return r.json?.permitId === sub.json?.id ? r : null;
});
ok(!!elig && elig.json.decision === 'ALLOW', 'new permit visible at the crossing via Kafka (outbox → read model)');
const drv = await api(T.driver1, '/api/permits/applications', { method: 'POST', idem: rnd(),
  body: { type: 'DRIVER', formData: { magneticCard: 'MC-2', vehiclePlate: '12-345-67', cargoType: 'FOOD', destinationRegion: 'CENTER' } } });
ok(drv.json?.recommendation === 'APPROVE', 'driver application → APPROVE', drv.json?.policyVersion);
const appt = new Date(Date.now() + 7 * 864e5).toISOString().slice(0, 10);
const med = await api(T.worker1, '/api/permits/applications', { method: 'POST', idem: rnd(),
  body: { type: 'MEDICAL', formData: { hospitalName: 'Demo Medical Center 1', appointmentDate: appt } } });
ok(med.json?.recommendation === 'APPROVE', 'medical application → APPROVE', med.json?.policyVersion);
const missing = await api(T.driver1, '/api/permits/applications', { method: 'POST', idem: rnd(), body: { type: 'DRIVER', formData: {} } });
ok(missing.status === 422 && missing.json?.code === 'MISSING_REQUIRED_FIELDS', 'missing required fields → 422 problem details');
ok((await api(T.clerk1, '/api/permits/applications/queue?limit=5')).json?.items?.length === 5, 'clerk queue (keyset page)');

section('policy studio');
const pols = await api(T.policy1, '/api/policies');
ok(pols.json?.length >= 5, 'policies listed', `${pols.json?.length}`);
const src = 'RULE "smoke" VERSION 1 EFFECTIVE 2026-01-01\nAPPLIES TO permit.type == WORK_ENTRY\nREQUIRE FIELDS idNumber\nWHEN person.age >= 30\nTHEN APPROVE VALIDITY 30d\nELSE REFER_TO_CLERK REASON "x"';
ok((await api(T.policy1, '/api/policies/validate', { method: 'POST', body: { source: src } })).json?.valid === true, 'DSL validates');
ok((await api(T.policy1, '/api/policies/validate', { method: 'POST', body: { source: 'RULE oops' } })).json?.valid === false, 'syntax error detected');
const sim = await api(T.policy1, '/api/policies/simulate', { method: 'POST', body: { source: src, caseCount: 1000, seed: 1 } });
ok(sim.json?.total === 1000, 'simulation over 1,000 cases', `changed=${sim.json?.changed}`);
ok((await api(T.clerk1, '/api/policies/validate', { method: 'POST', body: { source: src } })).status === 403, 'only POLICY_ADMIN may author policies');

section('closing the loop: signed ingest batch → violation → automatic sanction → appeal');
const ingestUp = (() => {
  try { return execFileSync('docker', ['compose', 'ps', '--status', 'running', '-q', 'ingest-service'], { cwd: root }).toString().trim() !== ''; } catch { return false; }
})();
if (ok(ingestUp, 'ingest-service running (profile ingest)')) {
  execFileSync('node', [join(root, 'scripts/ingest/make-batch.mjs'), '--ids', '900000005'], { cwd: root, stdio: 'ignore' });
  const denied = await until(async () => {
    const r = await check(T.officer1, '900000005');
    return r.json?.reasons?.[0]?.startsWith('ACTIVE_SANCTION') ? r : null;
  }, 40000, 1500);
  ok(!!denied, 'entry without permit → case → automatic ENTRY_BAN → DENY at crossing', denied?.json?.reasons?.[0]);
  const cases = await api(T.enforcer1, '/api/enforcement/cases?status=SANCTIONED&limit=50');
  const c = cases.json?.items?.find((x) => x.nationalId === '900000005');
  ok(!!c, 'sanctioned case listed for enforcement officer');
  if (c) {
    const ap = await api(T.enforcer1, `/api/enforcement/cases/${c.id}/appeal`, { method: 'POST', body: { version: c.version, text: 'smoke appeal' } });
    ok(ap.json?.status === 'APPEALED', 'appeal filed');
    const ov = await api(T.enforcer1, `/api/enforcement/cases/${c.id}/appeal-decision`, { method: 'POST', body: { version: ap.json?.version, uphold: false } });
    ok(ov.json?.status === 'CLOSED', 'appeal accepted → case closed');
    const lifted = await until(async () => {
      const r = await check(T.officer1, '900000005');
      return r.json && !r.json.reasons?.[0]?.startsWith('ACTIVE_SANCTION') ? r : null;
    }, 30000, 1500);
    ok(!!lifted, 'sanction lifted at the crossing (SanctionLifted event)', lifted?.json?.reasons?.[0]);
  }
  execFileSync('node', [join(root, 'scripts/ingest/make-batch.mjs'), '--ids', '980000030', '--badsig'], { cwd: root, stdio: 'ignore' });
  const q = await until(async () => {
    const r = await api(T.admin1, '/api/admin/quarantine');
    return r.json?.some((f) => f.reason?.startsWith('SIGNATURE')) ? r : null;
  }, 20000);
  ok(!!q, 'bad signature → quarantine with reason (admin screen)');
}

section('audit & admin');
const aud = await api(T.auditor1, `/api/audit?entityId=${sub.json?.id}`);
ok(aud.json?.items?.some((a) => a.action === 'PERMIT_APPROVED' && a.actor === 'clerk1'), 'audit: who approved, with policy version');
ok(Object.keys((await api(T.auditor1, '/api/audit/stats')).json ?? {}).length > 0, 'audit stats');
ok((await api(T.admin1, '/api/admin/dlt')).status === 200, 'DLT screen API');
ok((await api(T.worker1, '/api/audit')).status === 403, 'worker cannot read audit (403)');

section('resilience: mock-state-api outage → circuit breaker → fail-safe');
await api(T.admin1, '/api/chaos', { method: 'PUT', body: { outage: true } });
const down = await api(T.employer1, '/api/permits/applications', { method: 'POST', idem: rnd(),
  body: { nationalId: target.nationalId, type: 'WORK_ENTRY', formData: { magneticCard: 'MC-1', employerId: target.employerId, workRegion: target.region } } });
ok(down.json?.recommendation === 'REFER_TO_CLERK' && down.json?.reasons?.includes('STATE_SYSTEMS_UNAVAILABLE'), 'outage → REFER_TO_CLERK (never auto-approve)');
const blocked = await api(T.clerk1, `/api/permits/applications/${down.json?.id}/decision`, { method: 'POST', body: { approve: true, version: down.json?.version ?? 0 } });
ok(blocked.status === 503, 'approval blocked while state checks are unavailable (503)');
await api(T.admin1, '/api/chaos', { method: 'PUT', body: { outage: false } });
ok((await check(T.officer1, '900000001')).json?.decision === 'ALLOW', 'crossing check unaffected by the outage (no sync dependency)');

section('live updates & observability');
{
  const ctrl = new AbortController();
  const r = await fetch(`${BASE}/api/events/stream`, { headers: { Authorization: `Bearer ${T.clerk1}`, Accept: 'text/event-stream' }, signal: ctrl.signal });
  ok(r.status === 200 && (r.headers.get('content-type') ?? '').includes('text/event-stream'), 'SSE stream open');
  ctrl.abort();
}
try {
  const t = await (await fetch('http://127.0.0.1:9090/api/v1/targets?state=active')).json();
  const up = t.data.activeTargets.filter((x) => x.health === 'up').length;
  ok(up >= 9, 'Prometheus scraping services', `${up} targets up`);
  ok((await fetch('http://127.0.0.1:3000/api/health')).ok, 'Grafana up (http://localhost:3000)');
} catch {
  console.log('  – observability profile not running (optional): ipp up starts it');
}

console.log(`\n${fail === 0 ? '✔' : '✖'} smoke: ${pass} passed, ${fail} failed`);
process.exit(fail === 0 ? 0 : 1);
