import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { RequireAuth } from './auth/RequireAuth';
import { LoginPage } from './pages/LoginPage';
import { CallbackPage } from './pages/CallbackPage';
import { AppListPage } from './pages/AppListPage';
import { AppDetailPage } from './pages/AppDetailPage';
import { UsagePage } from './pages/UsagePage';
import { AuditPage } from './pages/AuditPage';

// 路由表：/login 与 /callback 匿名可访问；其余挂载在 RequireAuth 守卫下，未登录自动跳 /login
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/callback" element={<CallbackPage />} />
        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/" element={<AppListPage />} />
          <Route path="/apps/:id" element={<AppDetailPage />} />
          <Route path="/usage" element={<UsagePage />} />
          <Route path="/audit" element={<AuditPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
