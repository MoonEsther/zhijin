import { tokenStore } from './tokenStore';

// 用户身份 + 权限点持久化：localStorage（与 tokenStore 配套）。
// 登录回调（CallbackPage）写入 /auth/validate 结果；菜单/按钮权限过滤（<Perm>）从这里读。
export interface UserInfo {
  username: string;
  userId: number | null;
  tenantId: number | null;
  roles: string[];
  perms: string[];
}

const KEY = 'zhijin_user';

export const userStore = {
  get: (): UserInfo | null => {
    const s = localStorage.getItem(KEY);
    // JSON.parse 失败（老版本脏数据）兜底视为未登录，避免白屏
    if (!s) return null;
    try {
      return JSON.parse(s) as UserInfo;
    } catch {
      return null;
    }
  },
  set: (u: UserInfo) => localStorage.setItem(KEY, JSON.stringify(u)),
  clear: () => {
    localStorage.removeItem(KEY);
    tokenStore.clear();
  },
  /** 是否拥有某权限点：无用户缓存时一律 false（对应「无权限不渲染」）。 */
  hasPerm: (perm: string) => userStore.get()?.perms?.includes(perm) ?? false,
};
