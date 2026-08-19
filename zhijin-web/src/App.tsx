import { Card, Typography } from 'antd';

// V1 空壳：控制台占位页。应用管理、画布编排在计划 D 实现。
export default function App() {
  return (
    <Card style={{ maxWidth: 640, margin: '80px auto' }}>
      <Typography.Title level={3}>织锦 · zhijin 控制台</Typography.Title>
      <Typography.Paragraph>
        企业级智能体平台。V1 脚手架占位，应用管理与编排画布即将上线。
      </Typography.Paragraph>
    </Card>
  );
}
