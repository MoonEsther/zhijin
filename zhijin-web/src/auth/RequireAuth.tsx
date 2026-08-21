import { Navigate } from 'react-router-dom';
import { tokenStore } from './tokenStore';

// 路由守卫：无 token 一律重定向到登录页（OAuth2 授权码流程入口）
export function RequireAuth({ children }: { children: React.ReactNode }) {
  return tokenStore.get() ? <>{children}</> : <Navigate to="/login" replace />;
}
