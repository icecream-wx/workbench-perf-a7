import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

declare const process: { env: Record<string, string | undefined> };

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const backendPort = process.env.BACKEND_PORT || env.BACKEND_PORT || '8080';
  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: `http://127.0.0.1:${backendPort}`,
          changeOrigin: true,
        },
        '/actuator': {
          target: `http://127.0.0.1:${backendPort}`,
          changeOrigin: true,
        },
      },
    },
  };
});

