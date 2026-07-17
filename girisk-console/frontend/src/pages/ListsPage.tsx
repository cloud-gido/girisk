import { Button, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { RiskListEntry } from '../types';

const listTypes = [
  { value: 'BLACKLIST', label: '用户黑名单', color: 'red' },
  { value: 'WHITELIST', label: '用户白名单', color: 'green' },
  { value: 'IP_BLACKLIST', label: 'IP 黑名单', color: 'orange' },
];

export default function ListsPage() {
  const [entries, setEntries] = useState<RiskListEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = () => api.lists().then(setEntries).finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  const onAdd = async (values: Record<string, string>) => {
    await api.createList({ ...values, source: 'MANUAL', enabled: true });
    message.success('添加成功');
    setOpen(false);
    form.resetFields();
    load();
  };

  const onDelete = async (id: number) => {
    await api.deleteList(id);
    message.success('已删除');
    load();
  };

  const columns = [
    { title: '名单类型', dataIndex: 'listType', width: 130, render: (v: string) => {
      const t = listTypes.find((x) => x.value === v);
      return <Tag color={t?.color}>{t?.label || v}</Tag>;
    }},
    { title: '键', dataIndex: 'listKey', width: 100 },
    { title: '值', dataIndex: 'listValue', width: 140 },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
    { title: '来源', dataIndex: 'source', width: 90 },
    { title: '状态', dataIndex: 'enabled', width: 80, render: (v: boolean) => v ? <Tag color="success">生效</Tag> : <Tag>禁用</Tag> },
    { title: '操作', width: 80, render: (_: unknown, r: RiskListEntry) => <Button type="link" danger size="small" onClick={() => onDelete(r.id)}>删除</Button> },
  ];

  return (
    <>
      <div className="page-header">
        <h2>黑白名单</h2>
        <p>管理用户/IP 黑白名单，支持实时命中拦截与放行</p>
      </div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={() => setOpen(true)}>新增名单</Button>
      </Space>
      <Table className="content-card" rowKey="id" loading={loading} columns={columns} dataSource={entries} />
      <Modal title="新增名单" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={onAdd} initialValues={{ listKey: 'userId' }}>
          <Form.Item name="listType" label="名单类型" rules={[{ required: true }]}>
            <Select options={listTypes.map((t) => ({ value: t.value, label: t.label }))} />
          </Form.Item>
          <Form.Item name="listKey" label="键" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="listValue" label="值" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="reason" label="原因"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
    </>
  );
}
