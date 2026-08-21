import { Card, Typography } from 'antd';
import { useParams } from 'react-router-dom';

// 应用详情占位页（T3 实现：React Flow 画布 + 发布 + API Key，DSL 草稿存 localStorage）
export function AppDetailPage() {
  const { id } = useParams<{ id: string }>();
  return (
    <Card>
      <Typography.Title level={4}>应用详情（id={id}）</Typography.Title>
      <Typography.Paragraph type="secondary">待实现（计划 D T3：画布编排 + 发布）</Typography.Paragraph>
    </Card>
  );
}
