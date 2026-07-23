import {
  LogoutOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Divider, Dropdown, Modal, Radio, Space, Tag, Typography, message } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { clearAuth, getUser, hasPerm, Perm } from '../auth/session';
import {
  avatarHue,
  avatarInitials,
  getUserPrefs,
  setUserPrefs,
  type Density,
  type LandingPath,
  type UserPrefs,
} from '../auth/userPrefs';

const ROLE_LABEL: Record<string, string> = {
  ADMIN: '管理员',
  REVIEWER: '审核员',
  VIEWER: '观察员',
  TRADER: '交易员',
};

export default function UserMenu() {
  const navigate = useNavigate();
  const user = getUser();
  const [prefsOpen, setPrefsOpen] = useState(false);
  const [prefs, setPrefs] = useState<UserPrefs>(() => getUserPrefs());

  const initials = useMemo(
    () => avatarInitials(user?.displayName, user?.username),
    [user?.displayName, user?.username],
  );
  const color = useMemo(
    () => avatarHue(user?.username || user?.displayName),
    [user?.username, user?.displayName],
  );
  const roleLabel = ROLE_LABEL[user?.role || ''] || user?.role || '用户';

  const savePrefs = () => {
    setUserPrefs(prefs);
    message.success('偏好已保存');
    setPrefsOpen(false);
  };

  const logout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <>
      <Dropdown
        trigger={['click']}
        placement="bottomRight"
        dropdownRender={() => (
          <div className="user-menu-panel">
            <div className="user-menu-panel-head">
              <Avatar size={40} style={{ background: color, fontWeight: 600 }}>
                {initials}
              </Avatar>
              <div style={{ minWidth: 0 }}>
                <Typography.Text strong ellipsis style={{ display: 'block', maxWidth: 160 }}>
                  {user?.displayName || user?.username}
                </Typography.Text>
                <Space size={6} style={{ marginTop: 4 }}>
                  <Tag className="user-menu-role-tag">{roleLabel}</Tag>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    @{user?.username}
                  </Typography.Text>
                </Space>
              </div>
            </div>
            <Divider style={{ margin: '10px 0' }} />
            <button
              type="button"
              className="user-menu-item"
              onClick={() => {
                setPrefs(getUserPrefs());
                setPrefsOpen(true);
              }}
            >
              <SettingOutlined />
              个性化设置
            </button>
            {hasPerm(Perm.IAM_MANAGE) && (
              <button
                type="button"
                className="user-menu-item"
                onClick={() => navigate('/girisk/iam')}
              >
                <TeamOutlined />
                账号管理
              </button>
            )}
            <Divider style={{ margin: '10px 0' }} />
            <button type="button" className="user-menu-item danger" onClick={logout}>
              <LogoutOutlined />
              退出登录
            </button>
          </div>
        )}
      >
        <button type="button" className="user-menu-trigger" aria-label="用户菜单">
          <Avatar
            size={32}
            style={{ background: color, fontWeight: 600, fontSize: 13, flexShrink: 0 }}
            icon={!user ? <UserOutlined /> : undefined}
          >
            {user ? initials : null}
          </Avatar>
          <span className="user-menu-meta">
            <span className="user-menu-name">{user?.displayName || '未登录'}</span>
            <span className="user-menu-role">{roleLabel}</span>
          </span>
        </button>
      </Dropdown>

      <Modal
        title="个性化设置"
        open={prefsOpen}
        onCancel={() => setPrefsOpen(false)}
        onOk={savePrefs}
        okText="保存"
        cancelText="取消"
        destroyOnClose
        width={420}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20, paddingTop: 8 }}>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
              界面密度
            </Typography.Text>
            <Radio.Group
              value={prefs.density}
              onChange={(e) => setPrefs((p) => ({ ...p, density: e.target.value as Density }))}
              optionType="button"
              buttonStyle="solid"
              options={[
                { label: '舒适', value: 'comfortable' },
                { label: '紧凑', value: 'compact' },
              ]}
            />
            <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0', fontSize: 12 }}>
              紧凑模式缩小内容区内边距，适合大屏盯盘。
            </Typography.Paragraph>
          </div>
          <Divider style={{ margin: 0 }} />
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
              登录后默认页
            </Typography.Text>
            <Radio.Group
              value={prefs.landingPath}
              onChange={(e) =>
                setPrefs((p) => ({ ...p, landingPath: e.target.value as LandingPath }))
              }
              style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
              options={[
                { label: '总览', value: '/girisk' },
                { label: '敞口看板', value: '/girisk/exposure' },
                { label: '管线观察', value: '/girisk/stream' },
              ]}
            />
          </div>
        </div>
      </Modal>
    </>
  );
}
