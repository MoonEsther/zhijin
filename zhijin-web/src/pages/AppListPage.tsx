import { Card, Typography } from 'antd';

// 应用列表占位页（T2 实现：列表 + 新建/发布，RBAC app:* 权限点）
export function AppListPage() {
  return (
    <Card>
      <Typography.Title level={4}>应用列表</Typography.Title>
      <Typography.Paragraph type="secondary">待实现（计划 D T2）</Typography.Paragraph>
    </Card>
  );
}
