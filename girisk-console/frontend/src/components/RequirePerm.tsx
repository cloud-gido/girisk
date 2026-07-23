import { Result } from 'antd';
import { Outlet } from 'react-router-dom';
import { hasPerm } from '../auth/session';

/** 嵌套路由权限闸门：无权限显示 403，不踢登录 */
export default function RequirePerm({ perm }: { perm: string }) {
  if (!hasPerm(perm)) {
    return <Result status="403" title="403" subTitle="当前账号无权访问该页面" />;
  }
  return <Outlet />;
}
