import {
  BgColorsOutlined,
  CheckOutlined,
  InfoCircleOutlined,
  KeyOutlined,
  LogoutOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  Avatar,
  Divider,
  Dropdown,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { changePassword, logout as apiLogout } from '../api/auth';
import { clearAuth, getUser, hasPerm, Perm } from '../auth/session';
import {
  APPEARANCE_THEMES,
  AVATAR_PRESETS,
  avatarInitials,
  getUserPrefs,
  resolveAvatarColor,
  setUserPrefs,
  type AppearanceTheme,
  type Density,
  type LandingPath,
  type UserPrefs,
} from '../auth/userPrefs';
import { DISPLAY_TIMEZONES } from '../utils/timezones';

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
  const [avatarOpen, setAvatarOpen] = useState(false);
  const [pwdOpen, setPwdOpen] = useState(false);
  const [pwdSubmitting, setPwdSubmitting] = useState(false);
  const [prefs, setPrefs] = useState<UserPrefs>(() => getUserPrefs());
  const [pwdForm] = Form.useForm();

  const initials = useMemo(
    () => avatarInitials(user?.displayName, user?.username),
    [user?.displayName, user?.username],
  );
  const color = useMemo(
    () => resolveAvatarColor(user?.username, user?.displayName, prefs.avatarPresetId),
    [user?.username, user?.displayName, prefs.avatarPresetId],
  );
  const roleLabel = ROLE_LABEL[user?.role || ''] || user?.role || '用户';

  const refreshPrefs = () => setPrefs(getUserPrefs());

  const savePrefs = () => {
    setUserPrefs(prefs);
    message.success('偏好已保存');
    setPrefsOpen(false);
  };

  const pickAppearance = (appearance: AppearanceTheme) => {
    const next = setUserPrefs({ appearance });
    setPrefs(next);
    message.success('界面主题已切换');
  };

  const saveAvatar = (presetId: string | undefined) => {
    const next = setUserPrefs({ avatarPresetId: presetId });
    setPrefs(next);
    message.success(presetId ? '头像色已更新' : '已恢复默认头像色');
    setAvatarOpen(false);
  };

  const submitPassword = async () => {
    const values = await pwdForm.validateFields();
    if (values.newPassword !== values.confirmPassword) {
      message.error('两次输入的新密码不一致');
      return;
    }
    setPwdSubmitting(true);
    try {
      await changePassword(values.currentPassword, values.newPassword);
      message.success('密码已更新，请重新登录');
      setPwdOpen(false);
      pwdForm.resetFields();
      await apiLogout();
      clearAuth();
      navigate('/login');
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '修改失败');
    } finally {
      setPwdSubmitting(false);
    }
  };

  const logout = async () => {
    await apiLogout();
    clearAuth();
    navigate('/login');
  };

  const appearanceActive = (t: AppearanceTheme) =>
    (prefs.appearance || 'classic') === t ? <CheckOutlined /> : <span style={{ width: 14, display: 'inline-block' }} />;

  return (
    <>
      <Dropdown
        trigger={['click']}
        placement="bottomRight"
        dropdownRender={() => (
          <div className="user-menu-panel">
            <div className="user-menu-panel-head">
              <Avatar
                size={40}
                style={{ background: color, fontWeight: 600, cursor: 'pointer' }}
                onClick={() => {
                  refreshPrefs();
                  setAvatarOpen(true);
                }}
              >
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
                refreshPrefs();
                setAvatarOpen(true);
              }}
            >
              <UserOutlined />
              更换头像
            </button>
            <div className="user-menu-submenu">
              <div className="user-menu-submenu-label">
                <BgColorsOutlined />
                界面与背景
              </div>
              {APPEARANCE_THEMES.map((t) => (
                <button
                  key={t.value}
                  type="button"
                  className="user-menu-item user-menu-item-nested"
                  onClick={() => pickAppearance(t.value)}
                >
                  {appearanceActive(t.value)}
                  {t.label}
                </button>
              ))}
              <button
                type="button"
                className="user-menu-item user-menu-item-nested"
                onClick={() => {
                  refreshPrefs();
                  setPrefsOpen(true);
                }}
              >
                <SettingOutlined />
                更多偏好（时区 / 密度 / 首页）
              </button>
            </div>
            <Divider style={{ margin: '10px 0' }} />
            <button
              type="button"
              className="user-menu-item"
              onClick={() => {
                pwdForm.resetFields();
                setPwdOpen(true);
              }}
            >
              <KeyOutlined />
              修改密码
            </button>
            {hasPerm(Perm.IAM_MANAGE) && (
              <button
                type="button"
                className="user-menu-item"
                onClick={() => navigate('/girisk/iam')}
              >
                <TeamOutlined />
                系统管理
              </button>
            )}
            <button
              type="button"
              className="user-menu-item"
              onClick={() => navigate('/girisk/about')}
            >
              <InfoCircleOutlined />
              关于 GiRisk
            </button>
            <Divider style={{ margin: '10px 0' }} />
            <button type="button" className="user-menu-item danger" onClick={() => void logout()}>
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
        title="更换头像"
        open={avatarOpen}
        onCancel={() => setAvatarOpen(false)}
        footer={null}
        destroyOnClose
        width={400}
      >
        <div style={{ textAlign: 'center', marginBottom: 16 }}>
          <Avatar size={72} style={{ background: color, fontWeight: 600, fontSize: 28 }}>
            {initials}
          </Avatar>
        </div>
        <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 10, fontSize: 12 }}>
          选择预设色（本地保存，与 GIDO 家族体验对齐）
        </Typography.Text>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginBottom: 16 }}>
          {AVATAR_PRESETS.map((p) => (
            <button
              key={p.id}
              type="button"
              title={p.id}
              onClick={() => saveAvatar(p.id)}
              style={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                border:
                  prefs.avatarPresetId === p.id ? '2px solid #0f766e' : '2px solid transparent',
                background: p.color,
                cursor: 'pointer',
                boxShadow: prefs.avatarPresetId === p.id ? '0 0 0 2px #99f6e4' : undefined,
              }}
            />
          ))}
        </div>
        <button type="button" className="user-menu-item" onClick={() => saveAvatar(undefined)}>
          恢复默认（按用户名派生）
        </button>
      </Modal>

      <Modal
        title="修改密码"
        open={pwdOpen}
        onCancel={() => setPwdOpen(false)}
        onOk={() => void submitPassword()}
        confirmLoading={pwdSubmitting}
        okText="更新并重新登录"
        cancelText="取消"
        destroyOnClose
        width={420}
      >
        <Form form={pwdForm} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            name="currentPassword"
            label="当前密码"
            rules={[{ required: true, message: '请输入当前密码' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '至少 6 位' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            rules={[{ required: true, message: '请再次输入新密码' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="个性化设置"
        open={prefsOpen}
        onCancel={() => setPrefsOpen(false)}
        onOk={savePrefs}
        okText="保存"
        cancelText="取消"
        destroyOnClose
        width={440}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20, paddingTop: 8 }}>
          <div>
            <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
              展示时区
            </Typography.Text>
            <Select
              style={{ width: '100%' }}
              value={prefs.timezone}
              options={DISPLAY_TIMEZONES.map((t) => ({ label: t.label, value: t.value }))}
              onChange={(v) => setPrefs((p) => ({ ...p, timezone: v }))}
              showSearch
              optionFilterProp="label"
            />
            <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0', fontSize: 12 }}>
              也可点顶栏当前时区名称快速切换。
            </Typography.Paragraph>
          </div>
          <Divider style={{ margin: 0 }} />
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
