import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // same-origin reverse proxy, as in the deployed UI: the browser Origin is not forwarded to the API
      '/api': {
        target: process.env.CARDDEMO_API_URL ?? 'http://localhost:8080',
        configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('origin')),
      },
    },
  },
  test: { environment: 'jsdom', globals: true, setupFiles: ['src/test-setup.ts'] },
});
