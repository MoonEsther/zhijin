// access_token 持久化：localStorage（V1 简化；刷新 token / 过期续期留 V2）
const KEY = 'zhijin_access_token';
export const tokenStore = {
  get: () => localStorage.getItem(KEY),
  set: (t: string) => localStorage.setItem(KEY, t),
  clear: () => localStorage.removeItem(KEY),
};
