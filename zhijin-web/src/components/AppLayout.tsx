import { Layout, Menu } from 'antd';
import { AppstoreOutlined, AuditOutlined, BarChartOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

// 控制台整体布局：左侧 Sider 菜单 + 右侧 Content（路由出口）。
// 菜单项按应用/用量/审计三大块；RBAC 权限点过滤（user:manage/role:manage 等）留 T2 加。
const MENU_ITEMS = [
  { key: '/', icon: <AppstoreOutlined />, label: '应用' },
  { key: '/usage', icon: <BarChartOutlined />, label: '用量' },
  { key: '/audit', icon: <AuditOutlined />, label: '审计' },
];

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  // 详情页 /apps/:id 属于「应用」菜单，选中态归一化到 /
  const selectedKey = location.pathname.startsWith('/apps') ? '/' : location.pathname;

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
          items={MENU_ITEMS}
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
