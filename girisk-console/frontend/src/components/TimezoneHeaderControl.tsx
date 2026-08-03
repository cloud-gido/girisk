import { Form, Modal, Select, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { setUserPrefs } from '../auth/userPrefs';
import { useDisplayTimeZone } from '../hooks/useDisplayTimeZone';
import { DISPLAY_TIMEZONES } from '../utils/timezones';

/**
 * 顶栏时区：浅色静默入口（对齐 GIDO GlobalOutlined + IANA 文案）。
 */
export default function TimezoneHeaderControl() {
  const displayTz = useDisplayTimeZone();
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<{ timezone: string }>();

  useEffect(() => {
    if (open) {
      form.setFieldsValue({ timezone: displayTz });
    }
  }, [open, displayTz, form]);

  const save = async () => {
    const values = await form.validateFields();
    setUserPrefs({ timezone: values.timezone });
    message.success('时区已更新');
    setOpen(false);
  };

  return (
    <>
      <button
        type="button"
        className="tz-quiet-btn"
        title="设置展示时区"
        onClick={() => setOpen(true)}
      >
        <span className="tz-quiet-btn__icon" aria-hidden>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <circle cx="7" cy="7" r="5.25" stroke="currentColor" strokeWidth="1.25" />
            <ellipse cx="7" cy="7" rx="2.2" ry="5.25" stroke="currentColor" strokeWidth="1.15" />
            <path
              d="M1.75 7h10.5M7 1.75c1.4 1.45 2.1 3.2 2.1 5.25S8.4 10.8 7 12.25C5.6 10.8 4.9 9.05 4.9 7S5.6 3.2 7 1.75Z"
              stroke="currentColor"
              strokeWidth="1.05"
              strokeLinejoin="round"
              opacity="0.85"
            />
          </svg>
        </span>
        <span className="tz-quiet-btn__label">{displayTz}</span>
      </button>
      <Modal
        title="展示时区"
        open={open}
        onOk={() => void save()}
        onCancel={() => setOpen(false)}
        okText="确定"
        cancelText="取消"
        destroyOnClose
        width={420}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            name="timezone"
            label="时区"
            rules={[{ required: true, message: '请选择时区' }]}
          >
            <Select
              options={DISPLAY_TIMEZONES.map((t) => ({ label: t.label, value: t.value }))}
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            订单、决策等时间按此时区换算显示。当前 {displayTz}。
          </Typography.Text>
        </Form>
      </Modal>
    </>
  );
}
