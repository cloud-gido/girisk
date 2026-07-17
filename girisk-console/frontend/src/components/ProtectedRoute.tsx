import { me } from '../api/auth';
import { Spin } from 'antd';
import { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { clearAuth, isLoggedIn } from '../auth/session';

export default function ProtectedRoute({ loginPath = '/login' }: { loginPath?: string }) {
  const [checking, setChecking] = useState(true);
  const [valid, setValid] = useState(false);

  useEffect(() => {
    if (!isLoggedIn()) {
      setChecking(false);
      return;
    }
    me()
      .then(() => setValid(true))
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

  return <Outlet />;
}
