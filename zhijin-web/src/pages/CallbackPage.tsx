import { Alert, Card, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchUserInfo } from '../api/auth';
import { exchangeCode } from '../auth/oauth';
import { tokenStore } from '../auth/tokenStore';
import { userStore } from '../auth/userStore';

// 授权服务器回调页：校验 state → 用 code + code_verifier 换 token → 存 localStorage → 进主界面。
// 失败（state 不匹配 / 换 token 报错）时展示错误信息，不进入主界面。
export function CallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      const code = params.get('code');
      const state = params.get('state');
      const storedState = sessionStorage.getItem('oauth_state');
      const verifier = sessionStorage.getItem('code_verifier');

      // 防 CSRF：回调 state 必须与发起登录时一致
      if (!code || !state || state !== storedState) {
        setError('登录回调校验失败：state 不匹配或缺少授权码');
        return;
      }
      if (!verifier) {
        setError('登录回调校验失败：缺少 code_verifier');
        return;
      }
      try {
        const { access_token } = await exchangeCode(code, verifier);
        tokenStore.set(access_token);
        sessionStorage.removeItem('oauth_state');
        sessionStorage.removeItem('code_verifier');
        // 拉取用户身份 + 权限点写入 userStore，供菜单/按钮按 perms 过滤；
        // 失败不阻塞进入主界面（AppLayout 还有 mount 兜底补拉），仅打日志便于排查
        try {
          const user = await fetchUserInfo();
          userStore.set(user);
        } catch (e) {
          console.warn('拉取用户信息失败（进入主界面后由 AppLayout 兜底补拉）', e);
        }
        navigate('/', { replace: true });
      } catch (e) {
        setError(`换取 token 失败：${e instanceof Error ? e.message : String(e)}`);
      }
    })();
  }, [params, navigate]);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Card style={{ width: 420 }}>
        {error ? (
          <Alert type="error" message="登录失败" description={error} showIcon />
        ) : (
          <div style={{ textAlign: 'center' }}>
            <Spin />
            <Typography.Paragraph style={{ marginTop: 16 }}>登录中，正在换取访问凭证…</Typography.Paragraph>
          </div>
        )}
      </Card>
    </div>
  );
}
