import { useEffect, useState } from 'react';
import { Layout, Menu } from 'antd';
import {
  AppstoreOutlined,
  BarChartOutlined,
  SafetyCertificateOutlined,
  SolutionOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { fetchUserInfo } from '../api/auth';
import { userStore } from '../auth/userStore';

// 控制台整体布局：左侧 Sider 菜单 + 右侧 Content（路由出口）。
// 菜单项按 RBAC 权限点过滤（无权限不渲染，非禁用），perm 与后端 Permissions.kt 常量一致。
const MENU_ITEMS = [
  { key: '/', icon: <AppstoreOutlined />, label: '应用', perm: 'app:view' },
  { key: '/usage', icon: <BarChartOutlined />, label: '用量', perm: 'usage:view' },
  { key: '/audit', icon: <SafetyCertificateOutlined />, label: '审计', perm: 'audit:view' },
  { key: '/users', icon: <TeamOutlined />, label: '用户', perm: 'user:manage' },
  { key: '/roles', icon: <SolutionOutlined />, label: '角色', perm: 'role:manage' },
];

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  // 详情页 /apps/:id 属于「应用」菜单，选中态归一化到 /
  const selectedKey = location.pathname.startsWith('/apps') ? '/' : location.pathname;

  // 用户身份缓存兜底：首次进入（或老版本登录后 localStorage 无 zhijin_user）时，
  // 主动拉取 /auth/validate 回填 perms，避免菜单因权限点缺失全部消失。
  // 401 会由 fetchUserInfo 内部清 token 跳登录页（RequireAuth 兜底）；其他失败静默，
  // 下次刷新重试。userReady 触发一次重渲染，使过滤后的菜单随 perms 落位。
  const [userReady, setUserReady] = useState(() => Boolean(userStore.get()));
  useEffect(() => {
    if (userReady) return;
    fetchUserInfo()
      .then((user) => {
        userStore.set(user);
        setUserReady(true);
      })
      .catch(() => {});
  }, [userReady]);

  const menus = MENU_ITEMS.filter((m) => userStore.hasPerm(m.perm));

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Sider theme="dark" width={200}>
        <div style={{ color: '#fff', textAlign: 'center', padding: '16px 0', fontWeight: 600 }}>
          织锦 · zhijin
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menus}
          onClick={({ key }) => navigate(key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Content style={{ padding: 24, overflow: 'auto' }}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
