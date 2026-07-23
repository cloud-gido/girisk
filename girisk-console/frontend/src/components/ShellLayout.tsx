import { Breadcrumb, Layout, Menu } from 'antd';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import type { MenuProps } from 'antd';
import { getUserPrefs, type UserPrefs } from '../auth/userPrefs';
import BrandMark from './BrandMark';
import UserMenu from './UserMenu';

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
  const [prefs, setPrefs] = useState<UserPrefs>(() => getUserPrefs());

  const [openKeys, setOpenKeys] = useState<string[]>(() => {
    const g = groupKeyForPath(location.pathname);
    return g ? [g] : [];
  });

  useEffect(() => {
    const g = groupKeyForPath(location.pathname);
    if (g) setOpenKeys((prev) => (prev.includes(g) ? prev : [...prev, g]));
  }, [location.pathname, groupKeyForPath]);

  useEffect(() => {
    const onPrefs = (e: Event) => {
      const detail = (e as CustomEvent<UserPrefs>).detail;
      if (detail) setPrefs(detail);
    };
    window.addEventListener('girisk-prefs', onPrefs);
    return () => window.removeEventListener('girisk-prefs', onPrefs);
  }, []);

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

  const contentPad = prefs.density === 'compact' ? 20 : 32;

  return (
    <Layout style={{ minHeight: '100vh' }} className={`shell density-${prefs.density}`}>
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
          background: '#fafcfb',
        }}
      >
        <div style={{ padding: '18px 16px', display: 'flex', alignItems: 'center', gap: 10 }}>
          <BrandMark size={34} />
          <div>
            <div style={{ fontSize: 15, fontWeight: 650, letterSpacing: '-0.02em', color: '#134e4a' }}>
              {productName}
            </div>
            <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 1 }}>{productSubtitle}</div>
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
          style={{ border: 'none', padding: '0 8px 16px', background: 'transparent' }}
        />
      </Sider>
      <Layout style={{ marginLeft: 220, background: '#f5f7fa' }}>
        <Header
          style={{
            background: 'rgba(255,255,255,0.92)',
            backdropFilter: 'blur(8px)',
            padding: '0 28px',
            borderBottom: '1px solid #eef2f1',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            height: 56,
            position: 'sticky',
            top: 0,
            zIndex: 50,
          }}
        >
          <Breadcrumb items={breadcrumb} />
          <UserMenu />
        </Header>
        <Content style={{ padding: contentPad, minHeight: 'calc(100vh - 56px)' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
