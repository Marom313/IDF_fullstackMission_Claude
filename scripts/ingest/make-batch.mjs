#!/usr/bin/env node
// Creates a SIGNED demo ingest batch in ingest/inbox:
//   crossings_YYYYMMDD_HHMM.csv + .manifest.json + .manifest.sig (Ed25519 over the manifest bytes)
// The private key lives in .ingest-keys/ (gitignored); the public key is infra/ingest/keys/ingest.pub.pem.
// Usage: node scripts/ingest/make-batch.mjs [--ids 900000005,980000010] [--direction ENTRY]
//        [--tamper]   (changes the CSV after signing → HASH rejection → quarantine)
//        [--badsig]   (signs with a different key → SIGNATURE rejection)
import { createHash, createPrivateKey, generateKeyPairSync, randomUUID, sign } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const args = process.argv.slice(2);
const opt = (k, d) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : d; };
const flag = (k) => args.includes(k);

const privPath = join(root, '.ingest-keys', 'ingest.key.pem');
const pubPath = join(root, 'infra', 'ingest', 'keys', 'ingest.pub.pem');
if (!existsSync(privPath)) {
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  mkdirSync(dirname(privPath), { recursive: true });
  mkdirSync(dirname(pubPath), { recursive: true });
  writeFileSync(privPath, privateKey.export({ type: 'pkcs8', format: 'pem' }));
  writeFileSync(pubPath, publicKey.export({ type: 'spki', format: 'pem' }));
  console.log('generated demo ingest keypair (public key → infra/ingest/keys/ingest.pub.pem)');
}

// person ids follow the shared SeedCatalog: UUID v3 of "ipp-person-<i>"
const md5uuid = (s) => {
  const h = createHash('md5').update(s).digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
};
const indexOf = (nid) => (nid.startsWith('9000000') ? Number(nid.slice(-1)) - 1 : Number(nid.slice(2)));
const ids = opt('--ids', '900000005,980000010,980000020').split(',');
const direction = opt('--direction', 'ENTRY');
const now = new Date();
const rows = ids.map((nid) => [md5uuid(`ipp-person-${indexOf(nid)}`), nid, 'GATE-CENTER-1', direction, now.toISOString()].join(','));
const csv = ['personId,nationalId,gate,direction,occurredAt', ...rows].join('\n') + '\n';

const p2 = (n) => String(n).padStart(2, '0');
const stamp = `${now.getUTCFullYear()}${p2(now.getUTCMonth() + 1)}${p2(now.getUTCDate())}_${p2(now.getUTCHours())}${p2(now.getUTCMinutes())}`;
const name = `crossings_${stamp}.csv`;
const manifest = JSON.stringify({
  fileName: name, sizeBytes: Buffer.byteLength(csv), rowCount: rows.length, schemaVersion: '1',
  createdAt: now.toISOString(), sourceId: 'DEMO-GATE-SYSTEM', sha256: createHash('sha256').update(csv).digest('hex'),
  batchId: randomUUID(),
}, null, 2);
const key = flag('--badsig') ? generateKeyPairSync('ed25519').privateKey : createPrivateKey(readFileSync(privPath));
const signature = sign(null, Buffer.from(manifest), key).toString('base64');

const inbox = join(root, 'ingest', 'inbox');
mkdirSync(inbox, { recursive: true });
const base = join(inbox, name.replace(/\.csv$/, ''));
// write data + manifest first, signature LAST (the pipeline waits until all three exist)
writeFileSync(`${base}.csv`, flag('--tamper') ? csv.replace('GATE-CENTER-1', 'GATE-CENTER-2') : csv);
writeFileSync(`${base}.manifest.json`, manifest);
writeFileSync(`${base}.manifest.sig`, signature);
console.log(`batch ${name}: ${rows.length} rows${flag('--tamper') ? ' (TAMPERED)' : ''}${flag('--badsig') ? ' (BAD SIGNATURE)' : ''}`);
