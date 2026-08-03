import { PlusOutlined } from '@ant-design/icons';
import {
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { request } from '../api/http';

type IamUser = {
  id: number;
  username: string;
  displayName: string;
  role: string;
  enabled: boolean;
  roles: string[];
  permissions: string[];
  operatorScope?: string;
  createdAt?: string;
};

type IamRole = {
  id: number;
  code: string;
  name: string;
  builtin: boolean;
  description?: string;
  permissions: string[];
};

type IamPerm = {
  id: number;
  code: string;
  name: string;
  module: string;
  description?: string;
};

export default function IamPage() {
  const [users, setUsers] = useState<IamUser[]>([]);
  const [roles, setRoles] = useState<IamRole[]>([]);
  const [perms, setPerms] = useState<IamPerm[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<IamUser | null>(null);
  const [pwdUser, setPwdUser] = useState<IamUser | null>(null);
  const [editRole, setEditRole] = useState<IamRole | null>(null);
  const [form] = Form.useForm();
  const [editForm] = Form.useForm();
  const [pwdForm] = Form.useForm();

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const [u, r, p] = await Promise.all([
        request<IamUser[]>('/iam/users'),
        request<IamRole[]>('/iam/roles'),
        request<IamPerm[]>('/iam/permissions'),
      ]);
      setUsers(u);
      setRoles(r);
      setPerms(p);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const roleOptions = roles.map((r) => ({ label: `${r.name} (${r.code})`, value: r.code }));

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        账号与角色
      </Typography.Title>
      <Tabs
        items={[
          {
            key: 'users',
            label: '用户',
            children: (
              <Card
                extra={
                  <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
                    新建用户
                  </Button>
                }
              >
                <Table
                  rowKey="id"
                  loading={loading}
                  dataSource={users}
                  pagination={false}
                  columns={[
                    { title: '用户名', dataIndex: 'username' },
                    { title: '显示名', dataIndex: 'displayName' },
                    {
                      title: '商户范围',
                      dataIndex: 'operatorScope',
                      width: 140,
                      render: (v?: string) => (
                        <Typography.Text code ellipsis style={{ maxWidth: 120 }}>
                          {v || '*'}
                        </Typography.Text>
                      ),
                    },
                    {
                      title: '角色',
                      dataIndex: 'roles',
                      render: (rs: string[]) => (
                        <Space wrap>
                          {(rs || []).map((r) => (
                            <Tag key={r}>{r}</Tag>
                          ))}
                        </Space>
                      ),
                    },
                    {
                      title: '启用',
                      dataIndex: 'enabled',
                      render: (v: boolean, row) => (
                        <Switch
                          checked={v}
                          onChange={async (checked) => {
                            try {
                              await request(`/iam/users/${row.id}/enabled`, {
                                method: 'PATCH',
                                body: JSON.stringify({ enabled: checked }),
                              });
                              message.success('已更新');
                              void reload();
                            } catch (e) {
                              message.error((e as Error).message);
                            }
                          }}
                        />
                      ),
                    },
                    {
                      title: '操作',
                      render: (_, row) => (
                        <Space>
                          <Button
                            size="small"
                            onClick={() => {
                              setEditUser(row);
                              editForm.setFieldsValue({
                                displayName: row.displayName,
                                role: row.role,
                                roles: row.roles,
                                enabled: row.enabled,
                                operatorScope: row.operatorScope || '*',
                              });
                            }}
                          >
                            编辑
                          </Button>
                          <Button size="small" onClick={() => setPwdUser(row)}>
                            重置密码
                          </Button>
                        </Space>
                      ),
                    },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: 'roles',
            label: '角色权限',
            children: (
              <Card>
                <Table
                  rowKey="id"
                  loading={loading}
                  dataSource={roles}
                  pagination={false}
                  columns={[
                    { title: '代码', dataIndex: 'code' },
                    { title: '名称', dataIndex: 'name' },
                    {
                      title: '内置',
                      dataIndex: 'builtin',
                      render: (v: boolean) => (v ? <Tag color="blue">builtin</Tag> : <Tag>custom</Tag>),
                    },
                    {
                      title: '权限',
                      dataIndex: 'permissions',
                      render: (ps: string[]) => (
                        <Space wrap size={[4, 4]}>
                          {(ps || []).map((p) => (
                            <Tag key={p}>{p}</Tag>
                          ))}
                        </Space>
                      ),
                    },
                    {
                      title: '操作',
                      render: (_, row) => (
                        <Button
                          size="small"
                          onClick={() => setEditRole(row)}
                          disabled={row.code === 'ADMIN'}
                        >
                          配置权限
                        </Button>
                      ),
                    },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: 'perms',
            label: '权限目录',
            children: (
              <Card>
                <Table
                  rowKey="code"
                  loading={loading}
                  dataSource={perms}
                  pagination={false}
                  columns={[
                    { title: '权限码', dataIndex: 'code' },
                    { title: '名称', dataIndex: 'name' },
                    { title: '模块', dataIndex: 'module' },
                    { title: '说明', dataIndex: 'description' },
                  ]}
                />
              </Card>
            ),
          },
        ]}
      />

      <Modal
        title="新建用户"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={async (values) => {
            try {
              await request('/iam/users', { method: 'POST', body: JSON.stringify(values) });
              message.success('已创建');
              setCreateOpen(false);
              form.resetFields();
              void reload();
            } catch (e) {
              message.error((e as Error).message);
            }
          }}
        >
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 6 }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="role" label="主角色" rules={[{ required: true }]} initialValue="VIEWER">
            <Select options={roleOptions} />
          </Form.Item>
          <Form.Item
            name="operatorScope"
            label="商户范围"
            initialValue="*"
            extra="* = 全部；多个商户用逗号分隔，如 OP-A001,OP-B002"
          >
            <Input placeholder="*" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="编辑用户"
        open={!!editUser}
        onCancel={() => setEditUser(null)}
        onOk={() => editForm.submit()}
        destroyOnClose
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={async (values) => {
            if (!editUser) return;
            try {
              await request(`/iam/users/${editUser.id}`, {
                method: 'PUT',
                body: JSON.stringify(values),
              });
              message.success('已保存');
              setEditUser(null);
              void reload();
            } catch (e) {
              message.error((e as Error).message);
            }
          }}
        >
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="role" label="主角色" rules={[{ required: true }]}>
            <Select options={roleOptions} />
          </Form.Item>
          <Form.Item name="roles" label="绑定角色">
            <Select mode="multiple" options={roleOptions} />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item
            name="operatorScope"
            label="商户范围"
            extra="* = 全部；多个商户用逗号分隔"
          >
            <Input placeholder="*" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重置密码"
        open={!!pwdUser}
        onCancel={() => setPwdUser(null)}
        onOk={() => pwdForm.submit()}
        destroyOnClose
      >
        <Form
          form={pwdForm}
          layout="vertical"
          onFinish={async (values) => {
            if (!pwdUser) return;
            try {
              await request(`/iam/users/${pwdUser.id}/reset-password`, {
                method: 'POST',
                body: JSON.stringify(values),
              });
              message.success('密码已重置');
              setPwdUser(null);
              pwdForm.resetFields();
            } catch (e) {
              message.error((e as Error).message);
            }
          }}
        >
          <Form.Item name="password" label="新密码" rules={[{ required: true, min: 6 }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editRole ? `配置权限 · ${editRole.code}` : '配置权限'}
        open={!!editRole}
        onCancel={() => setEditRole(null)}
        onOk={async () => {
          if (!editRole) return;
          try {
            await request(`/iam/roles/${editRole.id}/permissions`, {
              method: 'PUT',
              body: JSON.stringify({ permissions: editRole.permissions }),
            });
            message.success('已保存');
            setEditRole(null);
            void reload();
          } catch (e) {
            message.error((e as Error).message);
          }
        }}
        width={640}
      >
        {editRole && (
          <Checkbox.Group
            style={{ width: '100%' }}
            value={editRole.permissions}
            onChange={(vals) =>
              setEditRole({ ...editRole, permissions: vals.map(String) })
            }
          >
            <Space direction="vertical">
              {perms.map((p) => (
                <Checkbox key={p.code} value={p.code}>
                  <Typography.Text code>{p.code}</Typography.Text> {p.name}
                </Checkbox>
              ))}
            </Space>
          </Checkbox.Group>
        )}
      </Modal>
    </Space>
  );
}
