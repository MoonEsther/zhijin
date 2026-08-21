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

  // 用户身份缓存与当前 token 同步：挂载时无条件静默补拉一次 /auth/validate 覆盖缓存。
  // 先用缓存值渲染菜单（无阻塞），请求返回后写回 userStore 并触发重渲染（setUserVersion），
  // 使菜单/按钮权限反映最新角色（角色变更后下次进页面即更新）。失败静默（下次刷新重试）；
  // 401 由 fetchUserInfo 内部清 token 跳登录页（RequireAuth 兜底）。依赖 [] 只跑一次，不循环。
  const [, setUserVersion] = useState(0);
  useEffect(() => {
    fetchUserInfo()
      .then((user) => {
        userStore.set(user);
        setUserVersion((v) => v + 1);
      })
      .catch(() => {});
  }, []);

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
