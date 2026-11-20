import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import axe from 'axe-core';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../shared/auth/AuthContext';

export function Providers({ children }: { children: ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <AuthProvider>
        <MemoryRouter>{children}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}

export const renderWithProviders = (ui: ReactNode) => render(ui, { wrapper: Providers });

/** axe-core run on a rendered container; returns violation ids (WCAG 2.1 AA rules). */
export async function axeViolations(container: Element): Promise<string[]> {
  const res = await axe.run(container, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    rules: { 'color-contrast': { enabled: false } }, // jsdom cannot compute styles; checked in Playwright
  });
  return res.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
}

/** Mock fetch with a route table: { 'GET /api/x': body | (url) => body }. */
export function mockFetch(routes: Record<string, unknown>) {
  const fn = vi.fn(async (url: string, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${url.split('?')[0]}`;
    const hit = Object.entries(routes).find(([k]) => key.startsWith(k));
    if (!hit) return new Response(JSON.stringify({ code: 'NOT_MOCKED', status: 404 }), { status: 404 });
    const body = typeof hit[1] === 'function' ? (hit[1] as (u: string) => unknown)(url) : hit[1];
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}
