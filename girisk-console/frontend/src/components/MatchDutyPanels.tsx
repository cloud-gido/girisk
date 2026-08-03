import { Card, Col, Collapse, Empty, Row, Table, Typography } from 'antd';
import type { ReactNode } from 'react';
import type {
  FixtureReplayStats,
  MarketGroupView,
  OutcomeLimitRow,
  RiskFixtureView,
  SportsMatchView,
} from '../types';
import { buildOutcomeRows, groupStakeTotal } from '../utils/proportionalLimit';
import { outcomeLimitColumns } from '../utils/sportsLimitColumns';

export function fmtNum(n?: number | null, digits = 2) {
  if (n == null || Number.isNaN(n)) return '—';
  return n.toLocaleString(undefined, { maximumFractionDigits: digits, minimumFractionDigits: 0 });
}

function StatTile({
  label,
  value,
  tone,
}: {
  label: string;
  value: ReactNode;
  tone?: 'danger' | 'ok' | 'muted';
}) {
  const color = tone === 'danger' ? '#cf1322' : tone === 'ok' ? '#389e0d' : undefined;
  return (
    <Col xs={12} sm={8} md={6} lg={4}>
      <div
        style={{
          background: '#fafafa',
          border: '1px solid #f0f0f0',
          borderRadius: 8,
          padding: '12px 14px',
          minHeight: 78,
        }}
      >
        <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 6 }}>
          {label}
        </Typography.Text>
        <div style={{ fontSize: 18, fontWeight: 600, color, wordBreak: 'break-all' }}>{value}</div>
      </div>
    </Col>
  );
}

export function mergeInterceptStats(
  liveView?: RiskFixtureView | null,
  match?: SportsMatchView | null,
): FixtureReplayStats {
  const rs = liveView?.replayStats ?? {};
  // 决策口径：勿回退 confirmedOrders（那是确认池大小，会把「接收」和敞口池混为一谈）
  const accepted = rs.acceptedCount ?? 0;
  const rejected = rs.rejectedTotal ?? 0;
  const duplicate = rs.duplicateCount ?? 0;
  const rejectedLimit = rs.rejectedLimit ?? 0;
  const rejectedExposure = rs.rejectedExposure ?? 0;
  const worstYuanAbs = (liveView?.worstLossCents || 0) / 100;
  const withRiskPnl =
    rs.withRiskWorstPnlYuan ?? (liveView != null ? -Math.abs(worstYuanAbs) : undefined);
  const withRiskScore = rs.withRiskWorstScore || liveView?.worstScore || undefined;
  // 限额参数：只用 Console 分层生效值；禁止回退 Redis 决策快照（整场快照常有值、段快照常无 → Tab 分叉）
  const seed = match?.seedPayoutYuan != null ? Number(match.seedPayoutYuan) : undefined;
  const maxWorst =
    match?.maxWorstLossYuan != null
      ? Number(match.maxWorstLossYuan)
      : match?.exposureThreshold != null
        ? Number(match.exposureThreshold)
        : undefined;
  const delta = match?.delta != null ? Number(match.delta) : undefined;
  const accounted = accepted + rejected + duplicate;

  return {
    totalOrders: accounted,
    acceptedCount: accepted,
    rejectedTotal: rejected,
    duplicateCount: duplicate,
    rejectedLimit,
    rejectedExposure,
    seedPayoutYuan: seed,
    maxWorstLossYuan: maxWorst,
    delta,
    noRiskWorstPnlYuan: rs.noRiskWorstPnlYuan,
    noRiskWorstScore: rs.noRiskWorstScore,
    withRiskWorstPnlYuan: withRiskPnl,
    withRiskWorstScore: withRiskScore,
    acceptedStakeYuan: rs.acceptedStakeYuan,
    confirmedPoolCount: rs.confirmedPoolCount,
  };
}

export function MatchInterceptSummary({
  liveView,
  match,
  defaultOpen = true,
}: {
  liveView?: RiskFixtureView | null;
  match?: SportsMatchView | null;
  /** 与「各盘口明细」一致：下拉箭头折叠 */
  defaultOpen?: boolean;
}) {
  const body = (() => {
    if (!liveView && !match) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="暂无本场数据（确认 Flink 已写入 Redis 视图）"
          style={{ margin: '8px 0 16px' }}
        />
      );
    }

    const rs = mergeInterceptStats(liveView, match);
    const thr = rs.maxWorstLossYuan;
    const riskLine =
      thr != null && !Number.isNaN(Number(thr))
        ? `${fmtNum(thr, 0)}（盈亏线 −${fmtNum(thr, 0)}）`
        : '—';

    return (
      <div>
        <Row gutter={[12, 12]}>
          <StatTile label="有效订单数" value={fmtNum(rs.totalOrders, 0)} />
          <StatTile label="接收订单数" value={fmtNum(rs.acceptedCount, 0)} tone="ok" />
          <StatTile label="拦截订单数" value={fmtNum(rs.rejectedTotal, 0)} tone="danger" />
          <StatTile label="重复订单数" value={fmtNum(rs.duplicateCount, 0)} tone="muted" />
          <StatTile label="限额拦截数" value={fmtNum(rs.rejectedLimit, 0)} tone="danger" />
          <StatTile label="风险拦截数" value={fmtNum(rs.rejectedExposure, 0)} tone="danger" />
          <StatTile label="初始已投注金额/盘口" value={fmtNum(rs.seedPayoutYuan, 0)} />
          <StatTile label="风险阈值" value={riskLine} />
          <StatTile label="完全不拦截最差盈亏" value={fmtNum(rs.noRiskWorstPnlYuan)} tone="danger" />
          <StatTile label="完全不拦截最差比分" value={rs.noRiskWorstScore || '—'} />
          <StatTile label="拦截后最差盈亏" value={fmtNum(rs.withRiskWorstPnlYuan)} tone="danger" />
          <StatTile label="拦截后最差比分" value={rs.withRiskWorstScore || '—'} />
          <StatTile label="拦截后累计投注金额" value={fmtNum(rs.acceptedStakeYuan)} />
        </Row>
        <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0, fontSize: 13 }}>
          有效 = 接收 + 拦截 + 重复（按 orderId 幂等；重复=pre 重放）：{fmtNum(rs.totalOrders, 0)} ={' '}
          {fmtNum(rs.acceptedCount, 0)} + {fmtNum(rs.rejectedTotal, 0)} + {fmtNum(rs.duplicateCount, 0)}
          {rs.confirmedPoolCount != null
            ? ` · 确认池 ${fmtNum(rs.confirmedPoolCount, 0)}`
            : ''}
          {rs.delta != null ? ` · δ=${rs.delta}` : ''}
          {' · 风险阈值与盈亏线为同一参数（正/负）· 累计投注=本金；盘口已投注=返彩'}
          {liveView?.updatedAt ? ` · Redis ${liveView.updatedAt}` : ''}
        </Typography.Paragraph>
      </div>
    );
  })();

  const accepted = liveView?.replayStats?.acceptedCount;
  const subtitle =
    accepted != null
      ? `Flink→Redis · 接收 ${fmtNum(accepted, 0)}`
      : 'Flink→Redis · 约 5s';

  return (
    <Collapse
      className="liability-inline-markets"
      bordered={false}
      defaultActiveKey={defaultOpen ? ['intercept'] : []}
      items={[{
        key: 'intercept',
        label: (
          <span>
            拦截结果汇总
            <Typography.Text type="secondary" style={{ marginLeft: 8, fontWeight: 400 }}>
              {subtitle}
            </Typography.Text>
          </span>
        ),
        children: body,
      }]}
    />
  );
}

export function MarketGroupStatsStrip({
  rows,
  delta,
  seedPayoutYuan = 0,
}: {
  group: MarketGroupView;
  rows: OutcomeLimitRow[];
  delta: number;
  seedPayoutYuan?: number;
}) {
  const actualTotal = groupStakeTotal(
    Object.fromEntries(rows.map((r) => [r.selection, r.stake])) as Record<string, number>,
  );
  const perSeed = Number(seedPayoutYuan) || Number(rows[0]?.seedYuan) || 0;
  const seedTotal = Math.round(rows.length * perSeed * 100) / 100;
  const bookTotal = Math.round((actualTotal + seedTotal) * 100) / 100;
  const overCount = rows.filter((r) => {
    const book = r.bookStake ?? r.stake + (r.seedYuan || perSeed || 0);
    return book > r.maxAllowedAmount;
  }).length;
  const acceptLeft = rows.reduce((s, r) => s + Math.max(0, r.acceptMax ?? 0), 0);
  return (
    <Row gutter={16} style={{ marginBottom: 12 }}>
      <Col span={6}>
        <Typography.Text type="secondary">真实已投注合计</Typography.Text>
        <div style={{ fontWeight: 600 }}>{fmtNum(actualTotal)}</div>
      </Col>
      <Col span={6}>
        <Typography.Text type="secondary">冷启动合计</Typography.Text>
        <div style={{ fontWeight: 600 }}>{fmtNum(seedTotal)}</div>
      </Col>
      <Col span={4}>
        <Typography.Text type="secondary">含种子账面</Typography.Text>
        <div style={{ fontWeight: 600 }}>{fmtNum(bookTotal)}</div>
      </Col>
      <Col span={4}>
        <Typography.Text type="secondary">超额方向</Typography.Text>
        <div style={{ fontWeight: 600, color: overCount ? '#cf1322' : undefined }}>
          {overCount} / {rows.length}
        </div>
      </Col>
      <Col span={4}>
        <Typography.Text type="secondary">还能接收 / δ</Typography.Text>
        <div style={{ fontWeight: 600 }}>
          {fmtNum(acceptLeft)} · {delta}
        </div>
      </Col>
    </Row>
  );
}

export function MatchMarketsCollapse({ view }: { view: SportsMatchView }) {
  const seed = Number(view.seedPayoutYuan) || 0;
  // stakes 来自 Flink 时含种子；优先用 outcomes.actualStake 合计真实投注
  const total = Math.round(
    view.marketGroups.reduce((sum, g) => {
      if (g.outcomes?.length) {
        return (
          sum +
          g.outcomes.reduce((s, o) => {
            const raw = o as OutcomeLimitRow & { actualStake?: number; seedYuan?: number };
            if (raw.actualStake != null) return s + Number(raw.actualStake);
            const book = Number(raw.stake) || 0;
            return s + Math.max(0, book - (Number(raw.seedYuan) || seed));
          }, 0)
        );
      }
      const keys = Object.keys(g.stakes || {});
      const book = groupStakeTotal(g.stakes || {});
      return sum + Math.max(0, book - keys.length * seed);
    }, 0) * 100,
  ) / 100;

  return (
    <Collapse
      className="liability-inline-markets"
      bordered={false}
      defaultActiveKey={['markets']}
      items={[{
        key: 'markets',
        label: (
          <span>
            各盘口明细（{view.marketGroups.length}）
            <Typography.Text type="secondary" style={{ marginLeft: 8, fontWeight: 400 }}>
              Flink→Redis · 真实已投注 {total.toLocaleString()}
            </Typography.Text>
          </span>
        ),
        children: view.marketGroups.length === 0 ? (
          <Empty description="等待 Flink 盘口视图" />
        ) : (
          view.marketGroups.map((g) => {
            const seed = Number(view.seedPayoutYuan) || 0;
            const rows: OutcomeLimitRow[] = g.outcomes?.length
              ? g.outcomes.map((o) => {
                  const raw = o as OutcomeLimitRow & {
                    actualStake?: number;
                    seedYuan?: number;
                    bookStake?: number;
                  };
                  const book =
                    raw.bookStake != null
                      ? Number(raw.bookStake)
                      : raw.actualStake != null
                        ? Number(raw.actualStake) + (Number(raw.seedYuan) || seed)
                        : Number(raw.stake) || 0;
                  const actual =
                    raw.actualStake != null
                      ? Number(raw.actualStake)
                      : Math.max(0, book - (Number(raw.seedYuan) || seed));
                  const seedYuan = Number(raw.seedYuan) || seed;
                  return {
                    selection: raw.selection,
                    stake: actual,
                    seedYuan,
                    bookStake: book,
                    targetAmount: Number(raw.targetAmount) || 0,
                    maxAllowedAmount: Number(raw.maxAllowedAmount) || 0,
                    acceptMax: Number(raw.acceptMax) || 0,
                  };
                })
              : buildOutcomeRows(
                  Object.keys(g.stakes || {}),
                  g.stakes || {},
                  Number(view.delta) || 0.2,
                  seed,
                ).map((r) => ({
                  ...r,
                  seedYuan: seed,
                  bookStake: r.stake + seed,
                }));
            return (
              <Card
                key={`${g.marketType}|${g.line || ''}`}
                size="small"
                className="liability-market"
                title={<span>{g.marketLabel}{g.line ? ` · ${g.line}` : ''}</span>}
                style={{ marginBottom: 12 }}
              >
                <MarketGroupStatsStrip
                  group={g}
                  rows={rows}
                  delta={Number(view.delta)}
                  seedPayoutYuan={seed}
                />
                <Table<OutcomeLimitRow>
                  size="small"
                  pagination={false}
                  rowKey="selection"
                  scroll={{ x: 640 }}
                  dataSource={rows}
                  rowClassName={(row) =>
                    (row.bookStake ?? row.stake + (row.seedYuan || 0)) > row.maxAllowedAmount
                      ? 'row-over-limit'
                      : ''
                  }
                  columns={outcomeLimitColumns({
                    line: g.line,
                    marketType: g.marketType,
                    editable: false,
                  })}
                />
              </Card>
            );
          })
        ),
      }]}
    />
  );
}
