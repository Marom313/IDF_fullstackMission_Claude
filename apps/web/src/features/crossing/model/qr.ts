/** QR format: base64url(payloadJson) + "." + base64url(Ed25519 signature). Verified with WebCrypto, offline. */
export interface QrPayload {
  p: string; // permit id
  n: string; // national id
  t: string; // permit type
  to: string; // valid to
}

const b64urlToBytes = (s: string): Uint8Array => {
  const b64 = s.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(s.length / 4) * 4, '=');
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
};

export async function verifyQrOffline(qr: string, spkiB64: string): Promise<QrPayload | null> {
  const dot = qr.indexOf('.');
  if (dot < 1) return null;
  const data = b64urlToBytes(qr.slice(0, dot));
  const sig = b64urlToBytes(qr.slice(dot + 1));
  const spki = Uint8Array.from(atob(spkiB64), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey('spki', spki, { name: 'Ed25519' }, false, ['verify']);
  const ok = await crypto.subtle.verify({ name: 'Ed25519' }, key, sig, data);
  return ok ? (JSON.parse(new TextDecoder().decode(data)) as QrPayload) : null;
}

export const isWebCryptoEd25519Error = (e: unknown) => e instanceof DOMException && e.name === 'NotSupportedError';
