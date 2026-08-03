import { Col, Descriptions, Empty, Row, Tag, Typography } from 'antd';
import type { DecisionGateSummary } from '../types';
import { formatYuan } from '../utils/gateSummary';

function rejectTag(summary: DecisionGateSummary) {
  if (summary.limitRejected) return <Tag color="red">Gate1 限额拦截</Tag>;
  if (summary.exposureRejected) return <Tag color="volcano">Gate2 敞口拦截</Tag>;
  if (summary.rejectReason && summary.rejectReason !== 'NONE') {
    return <Tag>{summary.rejectReason}</Tag>;
  }
  return <Tag color="green">两道闸门通过</Tag>;
}

/** 值班摘要：Gate1 b_max / Gate2 最差亏，不堆完整 productAudit。 */
export default function GateSummaryPanel({
  summary,
  compact,
}: {
  summary: DecisionGateSummary | null | undefined;
  compact?: boolean;
}) {
  if (!summary) {
    return <Empty description="无闸门现场（非 Flink 决策或旧数据）" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  const g1 = summary.gate1;
  const g2 = summary.gate2;

  return (
    <div>
      <div style={{ marginBottom: 12 }}>{rejectTag(summary)}</div>
      <Row gutter={[12, 12]}>
        <Col xs={24} md={compact ? 24 : 12}>
          <Typography.Title level={5} style={{ marginTop: 0 }}>Gate1 · 等比例限额</Typography.Title>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="盘口">{g1?.selectionLabel || '-'}</Descriptions.Item>
            <Descriptions.Item label="限额分组">{g1?.groupKey || '-'}</Descriptions.Item>
            <Descriptions.Item label="本笔占用(返彩)">{formatYuan(g1?.proposedPayoutYuan)}</Descriptions.Item>
            <Descriptions.Item label="判断前盘口占用">{formatYuan(g1?.stakeBeforeYuan)}</Descriptions.Item>
            <Descriptions.Item label="目标金额">{formatYuan(g1?.targetAmountYuan)}</Descriptions.Item>
            <Descriptions.Item label="最大允许">{formatYuan(g1?.maxAllowedYuan)}</Descriptions.Item>
            <Descriptions.Item label="可投注 b_max">
              <Typography.Text strong type={summary.limitRejected ? 'danger' : undefined}>
                {formatYuan(g1?.acceptMaxYuan)}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="δ / 种子">
              {g1?.limitDelta != null ? g1.limitDelta : '-'} / {formatYuan(g1?.seedPayoutYuan)}
            </Descriptions.Item>
          </Descriptions>
        </Col>
        <Col xs={24} md={compact ? 24 : 12}>
          <Typography.Title level={5} style={{ marginTop: 0 }}>Gate2 · 风险敞口</Typography.Title>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="试探最差亏(元)">
              <Typography.Text strong type={summary.exposureRejected ? 'danger' : undefined}>
                {formatYuan(g2?.trialWorstLossYuan)}
              </Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="风险阈值">{formatYuan(g2?.maxWorstLossYuan)}</Descriptions.Item>
            <Descriptions.Item label="最差比分">{g2?.worstScore || '-'}</Descriptions.Item>
            <Descriptions.Item label="接收前最差盈亏">{formatYuan(g2?.beforeWorstPnlYuan)}</Descriptions.Item>
            <Descriptions.Item label="假设接后最差盈亏">{formatYuan(g2?.trialWorstPnlYuan)}</Descriptions.Item>
            <Descriptions.Item label="实际后最差盈亏">{formatYuan(g2?.afterWorstPnlYuan)}</Descriptions.Item>
            <Descriptions.Item label="是否超阈">
              {g2?.exceeded ? <Tag color="red">是</Tag> : <Tag>否 / 未判</Tag>}
            </Descriptions.Item>
          </Descriptions>
        </Col>
      </Row>
    </div>
  );
}
