import { Button, Card, Typography } from 'antd';
import { useState } from 'react';
import { redirectToAuthorize } from '../auth/oauth';

// 登录页：点击「登录」即发起 OAuth2 授权码 + PKCE 流程，跳转授权服务器（zhijin-auth 表单登录页）
export function LoginPage() {
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    setLoading(true);
    await redirectToAuthorize();
    // redirectToAuthorize 会整页跳转；若异常则复位按钮状态
    setLoading(false);
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Card style={{ width: 360, textAlign: 'center' }}>
        <Typography.Title level={3}>织锦 · zhijin 控制台</Typography.Title>
        <Typography.Paragraph type="secondary">企业级智能体平台</Typography.Paragraph>
        <Button type="primary" size="large" block loading={loading} onClick={handleLogin}>
          登录
        </Button>
      </Card>
    </div>
  );
}
