import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 开发期把 /api 代理到平台服务(8080)，前端不直连后端其它端口，符合单一入口原则。
// D2：OAuth2 登录流涉及的 /oauth2（授权/换token）、/login（表单登录页）、/error（错误页）也必须走代理，
// 否则 dev 环境登录 404，且跨端口 fetch 换 token 会触发 CORS。
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },  // D2
      '/login': { target: 'http://localhost:8080', changeOrigin: true },   // D2
      '/error': { target: 'http://localhost:8080', changeOrigin: true },   // D2
    },
  },
});
