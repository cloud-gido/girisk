import { RightOutlined } from '@ant-design/icons';
import { Card, Statistic } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import { Link } from 'react-router-dom';

interface StatLinkCardProps {
  title: string;
  value: number | string;
  prefix?: ReactNode;
  suffix?: ReactNode;
  valueStyle?: CSSProperties;
  /** 有 to 时可点击跳转 */
  to?: string;
  hint?: string;
  onClick?: () => void;
}

/**
 * 总览指标卡：统一高度（无跳转也保留页脚占位），避免同排大小不一。
 */
export default function StatLinkCard({
  title,
  value,
  prefix,
  suffix,
  valueStyle,
  to,
  hint,
  onClick,
}: StatLinkCardProps) {
  const clickable = Boolean(to || onClick);
  const card = (
    <Card
      className={`stat-card${clickable ? ' stat-card-link' : ''}`}
      hoverable={clickable}
      onClick={onClick}
      styles={{ body: { padding: '20px 24px' } }}
    >
      <Statistic title={title} value={value} prefix={prefix} suffix={suffix} valueStyle={valueStyle} />
      <div className={`stat-card-foot${clickable ? '' : ' stat-card-foot--empty'}`}>
        {clickable ? (
          <>
            <span>{hint ?? '查看详情'}</span>
            <RightOutlined style={{ fontSize: 11 }} />
          </>
        ) : null}
      </div>
    </Card>
  );

  if (to) {
    return (
      <Link to={to} className="stat-card-link-wrap">
        {card}
      </Link>
    );
  }
  return card;
}
