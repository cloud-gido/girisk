import { Segmented } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';

const OPTIONS = [
  { label: '订单试算', value: '/girisk/sandbox/order' },
  { label: '投注试算', value: '/girisk/sandbox/bet' },
  { label: '管线观察', value: '/girisk/stream' },
  { label: '接口实验室', value: '/girisk/api-lab' },
];

/** 调试沙箱顶栏：造数 / 试算 / 管线观察，入口合一 */
export default function SandboxTabs() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const value = OPTIONS.some((o) => o.value === pathname) ? pathname : '/girisk/sandbox/order';

  return (
    <Segmented
      options={OPTIONS}
      value={value}
      onChange={(v) => navigate(String(v))}
      style={{ marginBottom: 16 }}
    />
  );
}
