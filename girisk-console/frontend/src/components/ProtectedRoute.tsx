import { Result, Spin } from 'antd';
import { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { me } from '../api/auth';
import { clearAuth, hasPerm, isLoggedIn, setAuth, getToken } from '../auth/session';

export default function ProtectedRoute({
  loginPath = '/login',
  requiredPerm,
}: {
  loginPath?: string;
  requiredPerm?: string;
}) {
  const [checking, setChecking] = useState(true);
  const [valid, setValid] = useState(false);

  useEffect(() => {
    if (!isLoggedIn()) {
      setChecking(false);
      return;
    }
    me()
      .then((profile) => {
        const token = getToken();
        if (token) {
          setAuth(
            token,
            profile.username,
            profile.role,
            profile.displayName,
            profile.roles ?? [profile.role],
            profile.permissions ?? [],
            profile.operatorScope ?? '*',
          );
        }
        setValid(true);
      })
      .catch(() => clearAuth())
      .finally(() => setChecking(false));
  }, []);

  if (checking) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!isLoggedIn() || !valid) {
    return <Navigate to={loginPath} replace />;
  }

  if (requiredPerm && !hasPerm(requiredPerm)) {
    return (
      <Result
        status="403"
        title="403"
        subTitle="当前账号无权访问该页面"
      />
    );
  }

  return <Outlet />;
}
