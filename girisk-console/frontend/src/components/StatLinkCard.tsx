import { RightOutlined } from '@ant-design/icons';
import { Card, Statistic } from 'antd';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

interface StatLinkCardProps {
  title: string;
  value: number | string;
  prefix?: ReactNode;
  valueStyle?: React.CSSProperties;
  to: string;
  hint?: string;
}

export default function StatLinkCard({ title, value, prefix, valueStyle, to, hint }: StatLinkCardProps) {
  return (
    <Link to={to} style={{ display: 'block' }}>
      <Card
        className="stat-card stat-card-link"
        hoverable
        styles={{ body: { padding: '20px 24px' } }}
      >
        <Statistic title={title} value={value} prefix={prefix} valueStyle={valueStyle} />
        <div className="stat-card-foot">
          <span>{hint ?? '查看详情'}</span>
          <RightOutlined style={{ fontSize: 11 }} />
        </div>
      </Card>
    </Link>
  );
}
