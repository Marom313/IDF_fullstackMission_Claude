#!/usr/bin/env node
// Builds a signed crossing batch (the "outside system" side of whitening). Node 20+, no npm deps.
//
//   node scripts/ingest/make-nifi-batch.mjs [scenario] [--rows N] [--out ingest/inbox]
//
// ZIP batches for the NiFi flow (docs/NIFI.md). scripts/ingest/make-batch.mjs makes the 3-loose-file
// batches for ingest-service. Both use the SAME key pair, so one sender identity is trusted by both pipelines.
//
// scenario: ok (default) · tamper · bad-signature · bad-rows · duplicate · zip-slip · overstay
// The zip holds exactly: data.csv, manifest.json, manifest.sig (base64 Ed25519 over manifest.json bytes).
// Keys: .ingest-keys/ingest.key.pem (PRIVATE, gitignored) + infra/ingest/keys/ingest.pub.pem (public).
// Generated on first run if missing (same paths as make-batch.mjs). ALL DATA IS MOCK (seed national ids 900000001-5 / 98xxxxxxx).
import { createHash, createPrivateKey, generateKeyPairSync, randomUUID, sign } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const PRIV = join(ROOT, '.ingest-keys', 'ingest.key.pem');
const PUB = join(ROOT, 'infra', 'ingest', 'keys', 'ingest.pub.pem');
const GATES = ['GATE-NORTH-1', 'GATE-NORTH-2', 'GATE-CENTER-1', 'GATE-CENTER-2', 'GATE-SOUTH-1'];
const HEADER = 'crossingId,nationalId,gate,direction,occurredAt';
const SCENARIOS = ['ok', 'tamper', 'bad-signature', 'bad-rows', 'duplicate', 'zip-slip', 'overstay'];

// ------------------------------------------------------------------ args
const args = process.argv.slice(2);
const scenario = args.find((a) => !a.startsWith('--') && !/^\d+$/.test(a)) ?? 'ok';
const opt = (name, dflt) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : dflt;
};
const rows = Number(opt('rows', '50'));
const outDir = resolve(ROOT, opt('out', join('ingest', 'inbox')));
if (!SCENARIOS.includes(scenario)) {
  console.error(`unknown scenario '${scenario}'. one of: ${SCENARIOS.join(', ')}`);
  process.exit(2);
}

// ------------------------------------------------------------------ keys
function keys() {
  if (!existsSync(PRIV)) {
    mkdirSync(dirname(PRIV), { recursive: true });
    mkdirSync(dirname(PUB), { recursive: true });
    const { privateKey, publicKey } = generateKeyPairSync('ed25519');
    writeFileSync(PRIV, privateKey.export({ type: 'pkcs8', format: 'pem' }), { mode: 0o600 });
    writeFileSync(PUB, publicKey.export({ type: 'spki', format: 'pem' }));
    console.log(`generated signing keys (private ${PRIV} is gitignored, public ${PUB})`);
  }
  return createPrivateKey(readFileSync(PRIV));
}

// ------------------------------------------------------------------ data
const nationalId = (i) => (i < 5 ? `90000000${i + 1}` : `98${String(i).padStart(7, '0')}`);
const iso = (d) => d.toISOString().replace(/\.\d{3}Z$/, 'Z');

function crossings(n) {
  const now = Date.now();
  const lines = [HEADER];
  for (let k = 0; k < n; k++) {
    const i = k < 5 ? k : 5 + Math.floor(Math.random() * 4995);
    const at = new Date(now - Math.floor(Math.random() * 8 * 3600_000));
    lines.push([`C-${randomUUID()}`, nationalId(i), GATES[i % GATES.length], k % 2 ? 'EXIT' : 'ENTRY', iso(at)].join(','));
  }
  return lines;
}

/** worker1 (900000001) entered 3 days ago and never exited → enforcement should open an overstay case. */
function overstay() {
  const at = new Date(Date.now() - 3 * 24 * 3600_000);
  return [HEADER, [`C-${randomUUID()}`, '900000001', 'GATE-NORTH-1', 'ENTRY', iso(at)].join(',')];
}

// ------------------------------------------------------------------ zip (stored, no compression → no deps)
const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = (buf) => {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};

function zip(entries) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const [name, data] of entries) {
    const nameBuf = Buffer.from(name, 'utf8');
    const crc = crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);           // version needed
    local.writeUInt16LE(0x0800, 6);       // UTF-8 names
    local.writeUInt16LE(0, 8);            // stored
    local.writeUInt32LE(0, 10);           // time/date
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    local.writeUInt16LE(0, 28);
    locals.push(local, nameBuf, data);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(0, 10);
    central.writeUInt32LE(0, 12);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(data.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBuf.length, 28);
    central.writeUInt32LE(offset, 42);
    centrals.push(central, nameBuf);
    offset += 30 + nameBuf.length + data.length;
  }
  const cd = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(cd.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, cd, end]);
}

// ------------------------------------------------------------------ build
function build(lines, key) {
  const data = Buffer.from(lines.join('\n') + '\n', 'utf8');
  const manifest = Buffer.from(JSON.stringify({
    fileName: 'data.csv',
    sha256: createHash('sha256').update(data).digest('hex'),
    sizeBytes: data.length,
    rowCount: lines.length - 1,
    schemaVersion: 1,
    createdAt: iso(new Date()),
    sourceId: 'MOCK-GATE-SYSTEM',
  }), 'utf8');
  const sig = Buffer.from(sign(null, manifest, key).toString('base64'), 'ascii');
  return { data, manifest, sig };
}

const key = keys();
let lines = scenario === 'overstay' ? overstay() : crossings(rows);
if (scenario === 'bad-rows') {
  lines.push('C-bad-1,123456789,GATE-NORTH-1,ENTRY,2026-11-20T06:00:00Z');   // unknown person
  lines.push('C-bad-2,900000002,GATE-MARS,ENTRY,2026-11-20T06:00:00Z');      // unknown gate
  lines.push('C-bad-3,900000003,GATE-NORTH-1,SIDEWAYS,2026-11-20T06:00:00Z'); // bad direction
}
const b = build(lines, key);
let entries = [['data.csv', b.data], ['manifest.json', b.manifest], ['manifest.sig', b.sig]];

if (scenario === 'tamper') {
  // change one byte after signing: signature still valid, SHA-256 no longer matches
  const t = Buffer.from(b.data);
  t[t.length - 2] = t[t.length - 2] === 0x5a ? 0x59 : 0x5a;
  entries[0] = ['data.csv', t];
}
if (scenario === 'bad-signature') {
  const { privateKey } = generateKeyPairSync('ed25519');                       // a stranger's key
  entries[2] = ['manifest.sig', Buffer.from(sign(null, b.manifest, privateKey).toString('base64'), 'ascii')];
}
if (scenario === 'zip-slip') entries.push(['../../etc/cron.d/evil', Buffer.from('* * * * * root rm -rf /\n')]);

const zipBuf = zip(entries);
mkdirSync(outDir, { recursive: true });
const stamp = new Date().toISOString().replace(/[-:.]/g, '').slice(0, 15);
const name = `crossings-${stamp}-${scenario}-${randomUUID().slice(0, 8)}.zip`;
// write to a dot-file, then rename: NiFi's ListFile never picks up a half-written zip
const tmp = join(outDir, `.${name}.part`);
writeFileSync(tmp, zipBuf);
renameSync(tmp, join(outDir, name));
console.log(`${scenario}: ${join(outDir, name)} (${lines.length - 1} rows, sha256 ${JSON.parse(b.manifest).sha256.slice(0, 12)}…)`);

if (scenario === 'duplicate') {
  const again = name.replace('-duplicate-', '-duplicate-again-');
  writeFileSync(join(outDir, `.${again}.part`), zipBuf);
  renameSync(join(outDir, `.${again}.part`), join(outDir, again));
  console.log(`duplicate: ${join(outDir, again)} (same bytes → second one goes to quarantine)`);
}
