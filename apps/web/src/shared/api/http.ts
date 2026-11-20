import { currentToken } from '../auth/auth';

/** RFC 7807 problem details from the backend. */
export interface Problem {
  status: number;
  code: string;
  title?: string;
  detail?: string;
  traceId?: string;
  fields?: Record<string, string>;
}

export class ApiError extends Error {
  constructor(public readonly problem: Problem) {
    super(problem.detail ?? problem.code);
  }
}

const newId = () => crypto.randomUUID();

interface Options {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  idempotencyKey?: string;
  signal?: AbortSignal;
}

/** The only place that calls fetch: bearer token, correlation id, problem details. */
export async function http<T>(url: string, opts: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', 'X-Correlation-Id': newId() };
  const token = currentToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (opts.idempotencyKey) headers['Idempotency-Key'] = opts.idempotencyKey;
  const res = await fetch(url, {
    method: opts.method ?? 'GET',
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    signal: opts.signal,
  });
  if (!res.ok) {
    let problem: Problem = { status: res.status, code: `HTTP_${res.status}` };
    try {
      problem = { ...problem, ...((await res.json()) as Partial<Problem>) };
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(problem);
  }
  if (res.status === 204 || res.status === 202) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Binary resources (photos, PDFs) need the bearer token too → fetch as blob, then object URL. */
export async function httpBlobUrl(url: string): Promise<string> {
  const token = currentToken();
  const res = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!res.ok) throw new ApiError({ status: res.status, code: `HTTP_${res.status}` });
  return URL.createObjectURL(await res.blob());
}

/** Server-Sent Events with auth (EventSource cannot send headers → streaming fetch). */
export function subscribeEvents(onChange: (e: { kind: string; personId?: string; entityId?: string }) => void) {
  const ctrl = new AbortController();
  (async () => {
    while (!ctrl.signal.aborted) {
      try {
        const token = currentToken();
        const res = await fetch('/api/events/stream', {
          headers: { Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
          signal: ctrl.signal,
        });
        const reader = res.body?.getReader();
        if (!reader) throw new Error('no stream');
        const dec = new TextDecoder();
        let buf = '';
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          buf += dec.decode(value, { stream: true });
          let i;
          while ((i = buf.indexOf('\n\n')) >= 0) {
            const chunk = buf.slice(0, i);
            buf = buf.slice(i + 2);
            const data = chunk.split('\n').filter((l) => l.startsWith('data:')).map((l) => l.slice(5)).join('');
            if (data) onChange(JSON.parse(data));
          }
        }
      } catch {
        /* reconnect below */
      }
      if (!ctrl.signal.aborted) await new Promise((r) => setTimeout(r, 3000));
    }
  })();
  return () => ctrl.abort();
}
