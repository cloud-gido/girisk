import { LogoutOutlined } from '@ant-design/icons';
import { Breadcrumb, Button, Layout, Menu, Space, Typography } from 'antd';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import type { MenuProps } from 'antd';
import { clearAuth, getUser } from '../auth/session';
import BrandMark from './BrandMark';

const { Sider, Header, Content } = Layout;

export interface ShellLayoutProps {
  homePath: string;
  homeLabel: string;
  productName: string;
  productSubtitle: string;
  menuItems: MenuProps['items'];
  groupKeyForPath: (pathname: string) => string | undefined;
  pageTitleForPath: (pathname: string) => string;
  groupLabels: Record<string, string>;
}

export default function ShellLayout({
  homePath,
  homeLabel,
  productName,
  productSubtitle,
  menuItems,
  groupKeyForPath,
  pageTitleForPath,
  groupLabels,
}: ShellLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const user = getUser();

  const [openKeys, setOpenKeys] = useState<string[]>(() => {
    const g = groupKeyForPath(location.pathname);
    return g ? [g] : [];
  });

  useEffect(() => {
    const g = groupKeyForPath(location.pathname);
    if (g) setOpenKeys((prev) => (prev.includes(g) ? prev : [...prev, g]));
  }, [location.pathname, groupKeyForPath]);

  const breadcrumb = useMemo(() => {
    const items: { title: ReactNode }[] = [{ title: <Link to={homePath}>{homeLabel}</Link> }];
    const group = groupKeyForPath(location.pathname);
    if (group) {
      items.push({ title: groupLabels[group] ?? group });
    }
    if (location.pathname !== homePath) {
      items.push({ title: pageTitleForPath(location.pathname) });
    }
    return items;
  }, [groupKeyForPath, groupLabels, homeLabel, homePath, location.pathname, pageTitleForPath]);

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        width={220}
        theme="light"
        style={{
          borderRight: '1px solid #f0f0f0',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100,
          overflow: 'auto',
        }}
      >
        <div style={{ padding: '20px 16px', display: 'flex', alignItems: 'center', gap: 10 }}>
          <BrandMark size={36} />
          <div>
            <Typography.Text strong style={{ fontSize: 16, letterSpacing: '-0.02em' }}>
              {productName}
            </Typography.Text>
            <div style={{ fontSize: 11, color: '#8c8c8c' }}>{productSubtitle}</div>
          </div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          openKeys={openKeys}
          onOpenChange={setOpenKeys}
          items={menuItems}
          onClick={({ key }) => {
            if (typeof key === 'string' && key.startsWith('/')) navigate(key);
          }}
          style={{ border: 'none', padding: '0 8px 16px' }}
        />
      </Sider>
      <Layout style={{ marginLeft: 220 }}>
        <Header
          style={{
            background: '#fff',
            padding: '0 32px',
            borderBottom: '1px solid #f0f0f0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            height: 56,
          }}
        >
          <Breadcrumb items={breadcrumb} />
          <Space>
            <Typography.Text type="secondary">
              {user?.displayName} ({user?.role})
            </Typography.Text>
            <Button
              type="text"
              icon={<LogoutOutlined />}
              onClick={() => {
                clearAuth();
                navigate('/login');
              }}
            >
              退出
            </Button>
          </Space>
        </Header>
        <Content style={{ padding: 32, minHeight: 'calc(100vh - 56px)' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
