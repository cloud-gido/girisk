import { Descriptions, Drawer, Empty, Space, Typography } from 'antd';
import { Link } from 'react-router-dom';
import GateSummaryPanel from './GateSummaryPanel';
import { useDisplayTimeZone } from '../hooks/useDisplayTimeZone';
import type { DecisionReason, DecisionVersions, RiskDecisionLog } from '../types';
import { formatInTimeZone } from '../utils/datetime';
import { buildGateSummary } from '../utils/gateSummary';
import { DecisionTag, LevelTag } from '../utils/tags';

function parseJson<T>(raw: string | undefined | null, fallback: T): T {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function yuanFromCents(cents?: number | null) {
  if (cents == null) return '-';
  return (cents / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export default function DecisionDetailDrawer({
  open,
  onClose,
  log,
}: {
  open: boolean;
  onClose: () => void;
  log: RiskDecisionLog | null;
}) {
  const displayTz = useDisplayTimeZone();
  if (!log) return null;

  const reasons = parseJson<DecisionReason[]>(log.reasonsJson, []);

  const versions = parseJson<DecisionVersions>(log.versionsJson, {});
  const snapshot = parseJson<Record<string, unknown>>(log.featureSnapshotJson, {});
  const market = parseJson<Record<string, unknown>>(log.marketJson, {});
  const gateSummary = buildGateSummary(log);

  return (
    <Drawer
      title={`决策详情 · ${log.orderId}`}
      width={720}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
        <Descriptions.Item label="决策"><DecisionTag value={log.decision} /></Descriptions.Item>
        <Descriptions.Item label="风险等级"><LevelTag value={log.riskLevel} /></Descriptions.Item>
        <Descriptions.Item label="风险分">{log.riskScore}</Descriptions.Item>
        <Descriptions.Item label="延迟">{log.latencyMs != null ? `${log.latencyMs}ms` : '-'}</Descriptions.Item>
        <Descriptions.Item label="traceId" span={2}>{log.traceId || log.requestId}</Descriptions.Item>
        <Descriptions.Item label="用户">{log.userId}</Descriptions.Item>
        <Descriptions.Item label="商户">{log.operatorId || '-'}</Descriptions.Item>
        <Descriptions.Item label="场次">
          {log.fixtureId ? (
            <Link to={`/girisk/exposure?match=${encodeURIComponent(log.fixtureId)}`}>{log.fixtureId}</Link>
          ) : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="金额">{log.amount}</Descriptions.Item>
        <Descriptions.Item label="本金(分)">{log.stakeCents ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="赔率">{log.odds || '-'}</Descriptions.Item>
        <Descriptions.Item label="返彩">{yuanFromCents(log.payoutCents)}</Descriptions.Item>
        <Descriptions.Item label="可接本金">{yuanFromCents(log.maxAcceptableStakeCents)}</Descriptions.Item>
        <Descriptions.Item label="原因" span={2}>{log.reason}</Descriptions.Item>
        <Descriptions.Item label="时间" span={2}>
          {formatInTimeZone(log.createdAt, displayTz)}
        </Descriptions.Item>
      </Descriptions>

      <Typography.Title level={5}>闸门摘要</Typography.Title>
      <div className="content-card" style={{ padding: 12, marginBottom: 16 }}>
        <GateSummaryPanel summary={gateSummary} />
      </div>

      <Typography.Title level={5}>命中规则</Typography.Title>
      {reasons.length === 0 ? (
        <Empty description="无命中（PASS）" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }}>
          {reasons.map((r, idx) => (
            <div key={`${r.ruleId}-${idx}`} className="content-card" style={{ padding: 12 }}>
              <Space wrap>
                <DecisionTag value={r.action} />
                <Typography.Text strong>{r.ruleId}</Typography.Text>
                <Typography.Text type="secondary">v{r.ruleVersion}</Typography.Text>
                <Typography.Text type="secondary">{r.stage}</Typography.Text>
              </Space>
              <div style={{ marginTop: 8 }}>{r.message}</div>
              {r.evidence && (
                <pre style={{ marginTop: 8, marginBottom: 0, fontSize: 12, background: '#f5f7fa', padding: 8, borderRadius: 6, overflow: 'auto' }}>
                  {JSON.stringify(r.evidence, null, 2)}
                </pre>
              )}
            </div>
          ))}
        </Space>
      )}

      <Typography.Title level={5}>版本三元组</Typography.Title>
      <Descriptions column={1} size="small" bordered style={{ marginBottom: 16 }}>
        <Descriptions.Item label="configEpoch">{versions.configEpoch ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="paramSetVersion">{versions.paramSetVersion ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="ruleSetVersion">{versions.ruleSetVersion ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="engineBuild">{versions.engineBuild ?? '-'}</Descriptions.Item>
      </Descriptions>

      <Typography.Title level={5}>特征快照</Typography.Title>
      <pre style={{ fontSize: 12, background: '#f5f7fa', padding: 12, borderRadius: 6, overflow: 'auto' }}>
        {JSON.stringify(snapshot, null, 2)}
      </pre>

      {Object.keys(market).length > 0 && (
        <>
          <Typography.Title level={5} style={{ marginTop: 16 }}>盘口</Typography.Title>
          <pre style={{ fontSize: 12, background: '#f5f7fa', padding: 12, borderRadius: 6, overflow: 'auto' }}>
            {JSON.stringify(market, null, 2)}
          </pre>
        </>
      )}
    </Drawer>
  );
}
