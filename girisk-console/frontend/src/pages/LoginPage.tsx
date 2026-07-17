import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { login } from '../api/auth';
import { isLoggedIn } from '../auth/session';
import BrandMark from '../components/BrandMark';
import { PRODUCT_NAME, PRODUCT_TAGLINE } from '../brand';

export default function LoginPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  if (isLoggedIn()) {
    return <Navigate to="/girisk" replace />;
  }

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      await login(values.username, values.password);
      message.success('登录成功');
      navigate('/girisk');
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(160deg, #f0fdfa 0%, #f8fffe 42%, #f5f7fa 100%)',
      }}
    >
      <Card style={{ width: 400, borderRadius: 16, boxShadow: '0 8px 32px rgba(20,184,166,0.12)' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <BrandMark size={56} style={{ margin: '0 auto 14px', display: 'block' }} />
          <Typography.Title level={3} style={{ margin: 0, letterSpacing: '-0.02em' }}>
            {PRODUCT_NAME}
          </Typography.Title>
          <Typography.Text type="secondary">{PRODUCT_TAGLINE}</Typography.Text>
        </div>
        <Form layout="vertical" onFinish={onFinish} initialValues={{ username: 'admin', password: 'admin123' }}>
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" size="large" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={loading}>
            登录
          </Button>
        </Form>
        <Typography.Paragraph type="secondary" style={{ marginTop: 16, fontSize: 12, textAlign: 'center' }}>
          默认账号 admin / admin123
        </Typography.Paragraph>
      </Card>
    </div>
  );
}
