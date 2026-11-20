
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Dev server proxies to the local stack (NGINX on :80) so the browser stays same-origin-like.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost', changeOrigin: false },
      '/auth': { target: 'http://localhost', changeOrigin: false },
    },
  },
  build: {
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
  css: {
    preprocessorOptions: { scss: { api: 'modern-compiler' } },
    modules: { localsConvention: 'camelCaseOnly' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: { modules: { classNameStrategy: 'non-scoped' } },
  },
});
